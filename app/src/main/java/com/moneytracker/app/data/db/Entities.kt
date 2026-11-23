package com.moneytracker.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxType { INCOME, EXPENSE, TRANSFER }

/**
 * A money category / fund ("Food", "Savings", "New phone" ...).
 * Its balance is never stored: it is always derived from the transaction ledger,
 * so it can never drift out of sync with the transactions.
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Int,
    /** Optional monthly spending limit (budget), milli-units. */
    val monthlyLimit: Long? = null,
    /** Optional savings target (goal), milli-units. */
    val targetAmount: Long? = null,
    /** Optional goal deadline as LocalDate.toEpochDay(). */
    val targetDate: Long? = null,
    /** Archived categories are hidden; their history is kept for reports. */
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
)

/**
 * One ledger entry. `null` category means the general "Available" (unallocated) pool.
 *
 *  INCOME   : money enters.  categoryId = destination (null = Available).
 *  EXPENSE  : money leaves.  categoryId = source      (null = Available).
 *  TRANSFER : money moves.   categoryId = source, toCategoryId = destination.
 *             Allocating = Available -> category; removing = category -> Available.
 *             Transfers never change the total balance.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["toCategoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("toCategoryId"), Index("timestamp")],
)
data class MoneyTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    /** Always positive, milli-units. */
    val amount: Long,
    val categoryId: Long? = null,
    val toCategoryId: Long? = null,
    val description: String = "",
    val note: String = "",
    val timestamp: Long,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
