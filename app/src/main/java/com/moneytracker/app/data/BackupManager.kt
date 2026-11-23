package com.moneytracker.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.moneytracker.app.core.Dates
import com.moneytracker.app.domain.FinanceException
import com.moneytracker.app.domain.Reports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.YearMonth

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
            ?: throw FinanceException("Couldn't open the selected file.")
        val data = BackupCodec.decode(text)
        finance.replaceAll(data.categories, data.transactions)
        settings.setCurrency(data.settings.currency, data.settings.decimals)
        data
    }

    suspend fun exportCsv(uri: Uri, range: com.moneytracker.app.core.TimeRange? = null) = withContext(Dispatchers.IO) {
        val snap = finance.currentSnapshot()
        val txs = if (range == null) snap.transactions else snap.transactions.filter { it.timestamp in range }
        val csv = BackupCodec.csv(txs, snap.categories, settings.settings.value.formatter)
        // UTF-8 BOM so Excel shows Arabic and symbols correctly.
        write(uri, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + csv.toByteArray(Charsets.UTF_8))
    }

    /** Writes a printable one-or-more page PDF monthly report. */
    suspend fun exportMonthPdf(uri: Uri, ym: YearMonth) = withContext(Dispatchers.IO) {
        val snap = finance.currentSnapshot()
        val fmt = settings.settings.value.formatter
        val report = Reports.monthReport(snap.transactions, snap.categories, ym)
        val names = snap.categories.associate { it.id to it.name }

        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 40f
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        val title = Paint().apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(15, 118, 110) }
        val h2 = Paint().apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val body = Paint().apply { textSize = 10.5f; color = Color.DKGRAY }
        val bodyRight = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.8f }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = margin
        }
        fun ensure(space: Float) {
            if (page == null || y + space > pageH - margin) newPage()
        }
        fun text(s: String, p: Paint, x: Float = margin) {
            page!!.canvas.drawText(s, x, y, p)
        }

        ensure(60f)
        y += 10f
        text("Monthly report — ${ym.format(Dates.monthYear)}", title)
        y += 18f
        text("Generated ${LocalDateTime.now().format(Dates.mediumDate)} by Money Tracker", body)
        y += 26f

        val s = report.summary
        val rows = listOf(
            "Total income" to fmt.format(s.income),
            "Total expenses" to fmt.format(s.expense),
            "Net savings" to fmt.format(s.net),
            "Transactions" to s.count.toString(),
            "Balance at end of month" to fmt.format(report.totalAtEnd),
            "Unallocated at end of month" to fmt.format(report.availableAtEnd),
            "Previous month income" to fmt.format(report.previous.income),
            "Previous month expenses" to fmt.format(report.previous.expense),
        )
        ensure(20f)
        text("Summary", h2)
        y += 16f
        for ((k, v) in rows) {
            ensure(16f)
            text(k, body)
            page!!.canvas.drawText(v, (pageW - margin), y, bodyRight)
            y += 15f
        }

        y += 14f
        ensure(40f)
        text("Categories", h2)
        y += 16f
        val cols = floatArrayOf(margin, 230f, 320f, 410f, 555f)
        val headers = listOf("Category", "Added", "Spent", "Budget", "Balance at end")
        headers.forEachIndexed { i, hTxt ->
            if (i == 0) text(hTxt, h2) else page!!.canvas.drawText(hTxt, cols[i] + if (i == 4) 0f else 70f, y, Paint(bodyRight).apply { typeface = Typeface.DEFAULT_BOLD })
        }
        y += 6f
        page!!.canvas.drawLine(margin, y, pageW - margin, y, line)
        y += 14f
        for (r in report.rows) {
            ensure(16f)
            val name = if (r.categoryId == null) "Available (unallocated)" else names[r.categoryId] ?: "Deleted"
            text(name.take(34), body)
            page!!.canvas.drawText(fmt.number(r.added), cols[1] + 70f, y, bodyRight)
            page!!.canvas.drawText(fmt.number(r.spent), cols[2] + 70f, y, bodyRight)
            page!!.canvas.drawText(r.limit?.let { fmt.number(it) } ?: "—", cols[3] + 70f, y, bodyRight)
            page!!.canvas.drawText(fmt.number(r.balanceEnd), cols[4], y, bodyRight)
            y += 15f
        }

        y += 14f
        ensure(40f)
        text("Spending by category", h2)
        y += 16f
        if (report.spending.isEmpty()) {
            text("No expenses this month.", body)
            y += 15f
        }
        for (sp in report.spending) {
            ensure(16f)
            val name = if (sp.categoryId == null) "Available (unallocated)" else names[sp.categoryId] ?: "Deleted"
            text(name.take(40), body)
            page!!.canvas.drawText(
                "${fmt.format(sp.amount)}  (${String.format(java.util.Locale.US, "%.1f", sp.share * 100)}%)",
                pageW - margin, y, bodyRight,
            )
            y += 15f
        }

        page?.let { doc.finishPage(it) }
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { doc.writeTo(it) }
                ?: throw FinanceException("Couldn't write to the selected file.")
        } finally {
            doc.close()
        }
    }

    private fun write(uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw FinanceException("Couldn't write to the selected file.")
    }
}
