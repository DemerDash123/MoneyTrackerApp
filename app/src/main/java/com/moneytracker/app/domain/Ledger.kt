package com.moneytracker.app.domain

import com.moneytracker.app.core.MoneyFormatter
import com.moneytracker.app.i18n.S
import com.moneytracker.app.core.TimeRange
import com.moneytracker.app.data.db.Category
import com.moneytracker.app.data.db.MoneyTransaction
import com.moneytracker.app.data.db.TxType

/**
 * Balances derived from the ledger.
 *  total     = all income - all expenses
 *  available = money not assigned to any category
 *  byCategory= money currently assigned to each category
 * Invariant: total == available + sum(byCategory).
 */
data class Balances(
    val total: Long,
    val available: Long,
    val byCategory: Map<Long, Long>,
) {
    val allocated: Long get() = total - available
    fun of(categoryId: Long?): Long = if (categoryId == null) available else byCategory[categoryId] ?: 0L

    companion object {
        val EMPTY = Balances(0, 0, emptyMap())
    }
}

/** Lifetime (or ranged) movement figures for one category, or for Available when id is null. */
data class CategoryStats(
    /** Income received directly into the category. */
    val income: Long,
    /** Money moved in from Available / other categories. */
    val transfersIn: Long,
    /** Money moved out to Available / other categories. */
    val transfersOut: Long,
    /** Money spent from the category. */
    val spent: Long,
    val count: Int,
) {
    /** "Total amount allocated": every amount that ever entered the category. */
    val totalAdded: Long get() = income + transfersIn
    val net: Long get() = income + transfersIn - transfersOut - spent
}

object Ledger {

    fun balances(txs: Iterable<MoneyTransaction>, before: Long = Long.MAX_VALUE): Balances {
        var total = 0L
        var available = 0L
        val map = HashMap<Long, Long>()
        fun add(cat: Long?, delta: Long) {
            if (cat == null) available += delta else map[cat] = (map[cat] ?: 0L) + delta
        }
        for (t in txs) {
            if (t.timestamp >= before) continue
            when (t.type) {
                TxType.INCOME -> {
                    total += t.amount
                    add(t.categoryId, t.amount)
                }
                TxType.EXPENSE -> {
                    total -= t.amount
                    add(t.categoryId, -t.amount)
                }
                TxType.TRANSFER -> {
                    add(t.categoryId, -t.amount)
                    add(t.toCategoryId, t.amount)
                }
            }
        }
        return Balances(total, available, map)
    }

    fun touches(t: MoneyTransaction, categoryId: Long?): Boolean =
        t.categoryId == categoryId || (t.type == TxType.TRANSFER && t.toCategoryId == categoryId)

    /** Signed effect of [t] on the balance of [categoryId] (null = Available). */
    fun effectOn(t: MoneyTransaction, categoryId: Long?): Long = when (t.type) {
        TxType.INCOME -> if (t.categoryId == categoryId) t.amount else 0
        TxType.EXPENSE -> if (t.categoryId == categoryId) -t.amount else 0
        TxType.TRANSFER -> {
            var d = 0L
            if (t.categoryId == categoryId) d -= t.amount
            if (t.toCategoryId == categoryId) d += t.amount
            d
        }
    }

    fun categoryStats(
        categoryId: Long?,
        txs: Iterable<MoneyTransaction>,
        range: TimeRange = TimeRange.ALL,
    ): CategoryStats {
        var income = 0L
        var tin = 0L
        var tout = 0L
        var spent = 0L
        var count = 0
        for (t in txs) {
            if (t.timestamp !in range) continue
            if (!touches(t, categoryId)) continue
            count++
            when (t.type) {
                TxType.INCOME -> income += t.amount
                TxType.EXPENSE -> spent += t.amount
                TxType.TRANSFER -> {
                    if (t.categoryId == categoryId) tout += t.amount
                    if (t.toCategoryId == categoryId) tin += t.amount
                }
            }
        }
        return CategoryStats(income, tin, tout, spent, count)
    }
}

/** Thrown when an operation would break the books; the message is shown to the user. */
class FinanceException(message: String) : Exception(message)

object Validation {

    /**
     * Returns a user-facing error if any pool (Available or a category) would be negative,
     * or null when the books are consistent.
     */
    fun balanceProblem(
        categories: List<Category>,
        txs: List<MoneyTransaction>,
        fmt: MoneyFormatter,
    ): String? {
        val b = Ledger.balances(txs)
        if (b.available < 0) {
            return S.notEnoughAvailable(fmt.format(b.available))
        }
        for (c in categories) {
            val bal = b.byCategory[c.id] ?: 0L
            if (bal < 0) {
                return S.notEnoughIn(c.name, fmt.format(bal))
            }
        }
        return null
    }
}
