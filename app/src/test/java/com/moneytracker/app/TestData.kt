package com.moneytracker.app

import com.moneytracker.app.data.db.Category
import com.moneytracker.app.data.db.MoneyTransaction
import com.moneytracker.app.data.db.TxType
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Small builder for ledger tests. Amounts are given in whole units and converted to milli-units. */
class Books {
    val zone: ZoneId = ZoneOffset.UTC
    private var nextTx = 1L
    private var nextCat = 1L
    val categories = mutableListOf<Category>()
    val txs = mutableListOf<MoneyTransaction>()

    fun ts(y: Int, m: Int, d: Int, h: Int = 12): Long =
        LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    fun cat(name: String, limit: Double? = null, target: Double? = null): Long {
        val id = nextCat++
        categories += Category(
            id = id, name = name, icon = "category", color = 0,
            monthlyLimit = limit?.let { u(it) }, targetAmount = target?.let { u(it) },
        )
        return id
    }

    fun income(units: Double, to: Long? = null, at: Long = ts(2026, 11, 1)) =
        add(MoneyTransaction(id = nextTx++, type = TxType.INCOME, amount = u(units), categoryId = to, timestamp = at))

    fun expense(units: Double, from: Long? = null, at: Long = ts(2026, 11, 2)) =
        add(MoneyTransaction(id = nextTx++, type = TxType.EXPENSE, amount = u(units), categoryId = from, timestamp = at))

    fun transfer(units: Double, from: Long?, to: Long?, at: Long = ts(2026, 11, 1, 13)) =
        add(MoneyTransaction(id = nextTx++, type = TxType.TRANSFER, amount = u(units), categoryId = from, toCategoryId = to, timestamp = at))

    private fun add(t: MoneyTransaction): MoneyTransaction {
        txs += t
        return t
    }

    companion object {
        /** whole units -> milli-units, exact for up to 3 decimals */
        fun u(units: Double): Long = Math.round(units * 1000)
    }
}
