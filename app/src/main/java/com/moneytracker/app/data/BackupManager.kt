package com.moneytracker.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.moneytracker.app.core.Dates
import com.moneytracker.app.core.MoneyFormatter
import com.moneytracker.app.core.TimeRange
import com.moneytracker.app.domain.FinanceException
import com.moneytracker.app.domain.Reports
import com.moneytracker.app.i18n.I18n
import com.moneytracker.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Reads/writes backups and exports through Storage Access Framework URIs (no permissions needed). */
class BackupManager(
    private val context: Context,
    private val finance: FinanceRepository,
    private val settings: SettingsRepository,
) {

    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val snap = finance.currentSnapshot()
        val json = BackupCodec.encode(
            BackupData(settings.settings.value, snap.categories, snap.transactions),
            exportedAt = System.currentTimeMillis(),
        )
        write(uri, json.toByteArray(Charsets.UTF_8))
    }

    /** Validates the whole file first; current data is only replaced if the backup is valid. */
    suspend fun restoreBackup(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw FinanceException(S.cantOpenFile)
        val data = BackupCodec.decode(text)
        finance.replaceAll(data.categories, data.transactions)
        settings.setCurrency(data.settings.currency, data.settings.decimals)
        data
    }

    suspend fun exportCsv(uri: Uri, range: TimeRange? = null) = withContext(Dispatchers.IO) {
        val snap = finance.currentSnapshot()
        val txs = if (range == null) snap.transactions else snap.transactions.filter { it.timestamp in range }
        // Plain (non-isolated) numbers in files.
        val fmt = settings.settings.value.let { MoneyFormatter(it.currency, it.decimals) }
        val csv = BackupCodec.csv(txs, snap.categories, fmt)
        // UTF-8 BOM so Excel shows Arabic and symbols correctly.
        write(uri, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + csv.toByteArray(Charsets.UTF_8))
    }

    /** Writes a printable monthly report; laid out right-to-left when the app is in Arabic. */
    suspend fun exportMonthPdf(uri: Uri, ym: YearMonth) = withContext(Dispatchers.IO) {
        val snap = finance.currentSnapshot()
        val fmt = settings.settings.value.let { MoneyFormatter(it.currency, it.decimals) }
        val report = Reports.monthReport(snap.transactions, snap.categories, ym)
        val names = snap.categories.associate { it.id to it.name }
        val rtl = I18n.isRtl

        val doc = PdfDocument()
        val pageW = 595f
        val pageH = 842f
        val margin = 40f
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(15, 118, 110) }
        val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val h3 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.DKGRAY }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.8f }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), pageNo).create())
            y = margin
        }
        fun ensure(space: Float) {
            if (page == null || y + space > pageH - margin) newPage()
        }
        /** Draws at a position measured from the reading start (left in English, right in Arabic). */
        fun draw(s: String, fromStart: Float, p: Paint, alignEnd: Boolean = false) {
            val x = if (rtl) pageW - fromStart else fromStart
            val paint = Paint(p).apply {
                textAlign = when {
                    alignEnd == rtl -> Paint.Align.LEFT
                    else -> Paint.Align.RIGHT
                }
            }
            page!!.canvas.drawText(s, x, y, paint)
        }
        val startX = margin
        val endX = pageW - margin

        ensure(60f)
        y += 10f
        draw(S.pdfTitle(ym.format(Dates.monthYear)), startX, title)
        y += 18f
        draw(S.pdfGenerated(LocalDate.now().format(Dates.mediumDate)), startX, body)
        y += 26f

        val s = report.summary
        val rows = listOf(
            S.totalIncome to fmt.format(s.income),
            S.totalExpenses to fmt.format(s.expense),
            S.netSavings to fmt.format(s.net),
            S.transactions to s.count.toString(),
            S.balanceMonthEnd to fmt.format(report.totalAtEnd),
            S.unallocatedMonthEnd to fmt.format(report.availableAtEnd),
            S.prevIncome to fmt.format(report.previous.income),
            S.prevExpenses to fmt.format(report.previous.expense),
        )
        ensure(20f)
        draw(S.summary, startX, h2)
        y += 16f
        for ((k, v) in rows) {
            ensure(16f)
            draw(k, startX, body)
            draw(v, endX, body, alignEnd = true)
            y += 15f
        }

        y += 14f
        ensure(40f)
        draw(S.tabCategories, startX, h2)
        y += 16f
        // Column end positions (distance from the reading start).
        val cols = floatArrayOf(startX, 300f, 380f, 460f, endX)
        draw(S.category, cols[0], h3)
        draw(S.added, cols[1], h3, alignEnd = true)
        draw(S.spent, cols[2], h3, alignEnd = true)
        draw(S.budget, cols[3], h3, alignEnd = true)
        draw(S.balanceAtEnd, cols[4], h3, alignEnd = true)
        y += 6f
        page!!.canvas.drawLine(margin, y, pageW - margin, y, line)
        y += 14f
        for (r in report.rows) {
            ensure(16f)
            val name = if (r.categoryId == null) S.availableUnallocated else names[r.categoryId] ?: S.deletedWord
            draw(name.take(30), cols[0], body)
            draw(fmt.number(r.added), cols[1], body, alignEnd = true)
            draw(fmt.number(r.spent), cols[2], body, alignEnd = true)
            draw(r.limit?.let { fmt.number(it) } ?: "—", cols[3], body, alignEnd = true)
            draw(fmt.number(r.balanceEnd), cols[4], body, alignEnd = true)
            y += 15f
        }

        y += 14f
        ensure(40f)
        draw(S.spendingByCategory, startX, h2)
        y += 16f
        if (report.spending.isEmpty()) {
            draw(S.noExpensesThisMonth, startX, body)
            y += 15f
        }
        for (sp in report.spending) {
            ensure(16f)
            val name = if (sp.categoryId == null) S.availableUnallocated else names[sp.categoryId] ?: S.deletedWord
            draw(name.take(40), startX, body)
            draw("${fmt.format(sp.amount)}  (${String.format(Locale.US, "%.1f", sp.share * 100)}%)", endX, body, alignEnd = true)
            y += 15f
        }

        page?.let { doc.finishPage(it) }
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { doc.writeTo(it) }
                ?: throw FinanceException(S.cantWriteFile)
        } finally {
            doc.close()
        }
    }

    private fun write(uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw FinanceException(S.cantWriteFile)
    }
}
