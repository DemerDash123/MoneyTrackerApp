package com.moneytracker.app.data

import com.moneytracker.app.core.Dates
import com.moneytracker.app.core.MoneyFormatter
import com.moneytracker.app.core.MoneyMath
import com.moneytracker.app.data.db.Category
import com.moneytracker.app.data.db.MoneyTransaction
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.domain.FinanceException
import com.moneytracker.app.i18n.S
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.ZoneId

data class BackupData(
    val settings: AppSettings,
    val categories: List<Category>,
    val transactions: List<MoneyTransaction>,
)

/** JSON backup format + CSV export. Pure functions, no Android I/O. */
object BackupCodec {
    const val FORMAT_VERSION = 1
    private const val APP_ID = "MoneyTrackerApp"

    fun encode(data: BackupData, exportedAt: Long): String {
        val root = JSONObject()
        root.put("app", APP_ID)
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", exportedAt)
        root.put("amountUnit", "milli")
        root.put(
            "settings",
            JSONObject()
                .put("currency", data.settings.currency)
                .put("decimals", data.settings.decimals)
                .put("theme", data.settings.theme.name),
        )
        val cats = JSONArray()
        for (c in data.categories) {
            val o = JSONObject()
                .put("id", c.id)
                .put("name", c.name)
                .put("icon", c.icon)
                .put("color", c.color)
                .put("archived", c.archived)
                .put("sortOrder", c.sortOrder)
                .put("createdAt", c.createdAt)
            c.monthlyLimit?.let { o.put("monthlyLimit", it) }
            c.targetAmount?.let { o.put("targetAmount", it) }
            c.targetDate?.let { o.put("targetDate", it) }
            cats.put(o)
        }
        root.put("categories", cats)
        val txs = JSONArray()
        for (t in data.transactions) {
            val o = JSONObject()
                .put("id", t.id)
                .put("type", t.type.name)
                .put("amount", t.amount)
                .put("description", t.description)
                .put("note", t.note)
                .put("timestamp", t.timestamp)
                .put("createdAt", t.createdAt)
                .put("updatedAt", t.updatedAt)
            t.categoryId?.let { o.put("categoryId", it) }
            t.toCategoryId?.let { o.put("toCategoryId", it) }
            txs.put(o)
        }
        root.put("transactions", txs)
        return root.toString(2)
    }

    /** Parses and structurally validates a backup. Throws [FinanceException] with a readable message. */
    fun decode(json: String): BackupData {
        try {
            val root = JSONObject(json)
            if (root.optString("app") != APP_ID) throw FinanceException(S.notBackupFile)
            val version = root.optInt("formatVersion", -1)
            if (version < 1 || version > FORMAT_VERSION) {
                throw FinanceException(S.unsupportedVersion(version))
            }
            val s = root.optJSONObject("settings") ?: JSONObject()
            val settings = AppSettings(
                currency = s.optString("currency", "OMR").take(6),
                decimals = s.optInt("decimals", 3).coerceIn(0, 3),
                theme = runCatching { ThemeMode.valueOf(s.optString("theme", "SYSTEM")) }.getOrDefault(ThemeMode.SYSTEM),
            )
            val catArr = root.getJSONArray("categories")
            val categories = (0 until catArr.length()).map { i ->
                val o = catArr.getJSONObject(i)
                Category(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    icon = o.optString("icon", "category"),
                    color = o.getInt("color"),
                    monthlyLimit = if (o.has("monthlyLimit")) o.getLong("monthlyLimit") else null,
                    targetAmount = if (o.has("targetAmount")) o.getLong("targetAmount") else null,
                    targetDate = if (o.has("targetDate")) o.getLong("targetDate") else null,
                    archived = o.optBoolean("archived", false),
                    sortOrder = o.optInt("sortOrder", i),
                    createdAt = o.optLong("createdAt", 0),
                )
            }
            val ids = categories.map { it.id }
            if (ids.toSet().size != ids.size) throw FinanceException(S.backupDuplicateCategories)
            if (ids.any { it <= 0 }) throw FinanceException(S.backupInvalidCategoryIds)
            val idSet = ids.toSet()

            val txArr = root.getJSONArray("transactions")
            val transactions = (0 until txArr.length()).map { i ->
                val o = txArr.getJSONObject(i)
                val type = runCatching { TxType.valueOf(o.getString("type")) }.getOrNull()
                    ?: throw FinanceException(S.backupUnknownType)
                val amount = o.getLong("amount")
                if (amount <= 0 || amount > MoneyMath.MAX_AMOUNT) {
                    throw FinanceException(S.backupInvalidAmount)
                }
                val cat = if (o.has("categoryId") && !o.isNull("categoryId")) o.getLong("categoryId") else null
                val to = if (o.has("toCategoryId") && !o.isNull("toCategoryId")) o.getLong("toCategoryId") else null
                if (cat != null && cat !in idSet) throw FinanceException(S.backupMissingCategory)
                if (to != null && to !in idSet) throw FinanceException(S.backupMissingCategory)
                if (type == TxType.TRANSFER && cat == to) throw FinanceException(S.backupInvalidTransfer)
                MoneyTransaction(
                    id = o.getLong("id"),
                    type = type,
                    amount = amount,
                    categoryId = cat,
                    toCategoryId = if (type == TxType.TRANSFER) to else null,
                    description = o.optString("description", ""),
                    note = o.optString("note", ""),
                    timestamp = o.getLong("timestamp"),
                    createdAt = o.optLong("createdAt", 0),
                    updatedAt = o.optLong("updatedAt", 0),
                )
            }
            val txIds = transactions.map { it.id }
            if (txIds.toSet().size != txIds.size || txIds.any { it <= 0 }) {
                throw FinanceException(S.backupDuplicateTransactions)
            }
            return BackupData(settings, categories, transactions)
        } catch (e: JSONException) {
            throw FinanceException(S.backupDamaged)
        }
    }

    fun csv(
        transactions: List<MoneyTransaction>,
        categories: List<Category>,
        fmt: MoneyFormatter,
        zone: ZoneId = Dates.zone(),
    ): String {
        val names = categories.associate { it.id to it.name }
        fun place(id: Long?) = if (id == null) S.available else names[id] ?: S.unknown
        val sb = StringBuilder()
        sb.append(S.csvHeader.joinToString(",") { escape(it) }).append("\r\n")
        for (t in transactions.sortedWith(compareBy<MoneyTransaction> { it.timestamp }.thenBy { it.id })) {
            val dt = Dates.localDateTime(t.timestamp, zone)
            val amount = fmt.number(t.amount).replace(",", "")
            val signed = when (t.type) {
                TxType.INCOME -> amount
                TxType.EXPENSE -> "-$amount"
                TxType.TRANSFER -> "0"
            }
            val row = listOf(
                dt.toLocalDate().format(Dates.isoDate),
                dt.toLocalTime().format(Dates.time),
                S.typeName(t.type),
                amount,
                signed,
                fmt.currency,
                t.description,
                place(t.categoryId),
                if (t.type == TxType.TRANSFER) place(t.toCategoryId) else "",
                t.note,
            )
            sb.append(row.joinToString(",") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun escape(v: String): String {
        val needs = v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        // Guard against spreadsheet formula injection.
        val safe = if (v.isNotEmpty() && v[0] in "=+@" ) "'$v" else v
        return if (needs) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
