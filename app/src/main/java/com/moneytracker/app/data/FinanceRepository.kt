package com.moneytracker.app.data

import androidx.room.withTransaction
import com.moneytracker.app.core.Dates
import com.moneytracker.app.core.MoneyFormatter
import com.moneytracker.app.core.MoneyMath
import com.moneytracker.app.data.db.AppDatabase
import com.moneytracker.app.data.db.Category
import com.moneytracker.app.data.db.MoneyTransaction
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.domain.Balances
import com.moneytracker.app.domain.FinanceException
import com.moneytracker.app.domain.Ledger
import com.moneytracker.app.domain.Validation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** Everything the UI needs, emitted again after every change in the database. */
data class FinanceSnapshot(
    val categories: List<Category>,
    val transactions: List<MoneyTransaction>,
) {
    val balances: Balances by lazy { Ledger.balances(transactions) }
    val activeCategories: List<Category> by lazy { categories.filter { !it.archived } }
    private val byId: Map<Long, Category> by lazy { categories.associateBy { it.id } }
    fun category(id: Long?): Category? = id?.let { byId[it] }

    companion object {
        val EMPTY = FinanceSnapshot(emptyList(), emptyList())
    }
}

/** What the user entered in a transaction form. */
data class TxDraft(
    val type: TxType,
    val amount: Long,
    /** INCOME: destination, EXPENSE: source, TRANSFER: source. null = Available. */
    val categoryId: Long?,
    /** TRANSFER destination. null = Available. */
    val toCategoryId: Long? = null,
    val description: String = "",
    val note: String = "",
    val timestamp: Long,
)

data class CategoryInput(
    val name: String,
    val icon: String,
    val color: Int,
    val monthlyLimit: Long? = null,
    val targetAmount: Long? = null,
    val targetDate: LocalDate? = null,
)

sealed interface DeleteCategoryResult {
    data object Deleted : DeleteCategoryResult
    data class Archived(val returnedToAvailable: Long) : DeleteCategoryResult
}

/**
 * Single entry point for every change to financial data.
 * Each operation runs inside one SQLite transaction and re-validates the whole ledger
 * before committing, so a failed check rolls everything back (atomic and consistent).
 */
class FinanceRepository(
    private val db: AppDatabase,
    private val formatter: () -> MoneyFormatter,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val categoryDao = db.categoryDao()
    private val txDao = db.transactionDao()

    val snapshot: Flow<FinanceSnapshot> =
        combine(categoryDao.observeAll(), txDao.observeAll()) { c, t -> FinanceSnapshot(c, t) }

    suspend fun currentSnapshot(): FinanceSnapshot = FinanceSnapshot(categoryDao.getAll(), txDao.getAll())

    suspend fun getTransaction(id: Long): MoneyTransaction? = txDao.get(id)

    suspend fun getCategory(id: Long): Category? = categoryDao.get(id)

    // ---------------------------------------------------------------- transactions

    suspend fun addTransaction(draft: TxDraft): Long = db.withTransaction {
        val normalized = validateDraft(draft, existing = null)
        val now = clock()
        val id = txDao.insert(
            MoneyTransaction(
                type = normalized.type,
                amount = normalized.amount,
                categoryId = normalized.categoryId,
                toCategoryId = normalized.toCategoryId,
                description = normalized.description,
                note = normalized.note,
                timestamp = normalized.timestamp,
                createdAt = now,
                updatedAt = now,
            )
        )
        ensureConsistent()
        id
    }

    suspend fun updateTransaction(id: Long, draft: TxDraft) = db.withTransaction {
        val existing = txDao.get(id) ?: throw FinanceException("This transaction no longer exists.")
        val n = validateDraft(draft, existing)
        txDao.update(
            existing.copy(
                type = n.type,
                amount = n.amount,
                categoryId = n.categoryId,
                toCategoryId = n.toCategoryId,
                description = n.description,
                note = n.note,
                timestamp = n.timestamp,
                updatedAt = clock(),
            )
        )
        ensureConsistent()
    }

    suspend fun deleteTransaction(id: Long) = db.withTransaction {
        if (txDao.delete(id) == 0) throw FinanceException("This transaction no longer exists.")
        ensureConsistent()
    }

    private suspend fun validateDraft(draft: TxDraft, existing: MoneyTransaction?): TxDraft {
        if (draft.amount <= 0) throw FinanceException("Amount must be greater than zero.")
        if (draft.amount > MoneyMath.MAX_AMOUNT) throw FinanceException("Amount is too large.")
        val endOfToday = Dates.startOfDay(LocalDate.now().plusDays(1))
        if (draft.timestamp >= endOfToday) throw FinanceException("The date can't be in the future.")
        if (draft.description.length > 80) throw FinanceException("Description is too long (max 80 characters).")
        if (draft.note.length > 500) throw FinanceException("Note is too long (max 500 characters).")

        val normalized = when (draft.type) {
            TxType.INCOME, TxType.EXPENSE -> draft.copy(toCategoryId = null)
            TxType.TRANSFER -> {
                if (draft.categoryId == draft.toCategoryId) {
                    throw FinanceException("Choose two different places to move money between.")
                }
                draft
            }
        }.copy(description = draft.description.trim(), note = draft.note.trim())

        for (cid in listOfNotNull(normalized.categoryId, normalized.toCategoryId)) {
            val c = categoryDao.get(cid) ?: throw FinanceException("That category no longer exists.")
            val alreadyLinked = existing != null && (existing.categoryId == cid || existing.toCategoryId == cid)
            if (c.archived && !alreadyLinked) {
                throw FinanceException("“${c.name}” was deleted. Choose another category.")
            }
        }
        return normalized
    }

    private suspend fun ensureConsistent() {
        val problem = Validation.balanceProblem(categoryDao.getAll(), txDao.getAll(), formatter())
        if (problem != null) throw FinanceException(problem)
    }

    // ---------------------------------------------------------------- categories

    suspend fun createCategory(input: CategoryInput, initialAllocation: Long = 0): Long = db.withTransaction {
        val clean = validateCategory(input, excludeId = 0)
        val now = clock()
        val id = categoryDao.insert(
            Category(
                name = clean.name,
                icon = clean.icon,
                color = clean.color,
                monthlyLimit = clean.monthlyLimit,
                targetAmount = clean.targetAmount,
                targetDate = clean.targetDate?.toEpochDay(),
                sortOrder = categoryDao.maxSortOrder() + 1,
                createdAt = now,
            )
        )
        if (initialAllocation > 0) {
            if (initialAllocation > MoneyMath.MAX_AMOUNT) throw FinanceException("Amount is too large.")
            txDao.insert(
                MoneyTransaction(
                    type = TxType.TRANSFER,
                    amount = initialAllocation,
                    categoryId = null,
                    toCategoryId = id,
                    description = "Allocated to ${clean.name}",
                    timestamp = now,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
        ensureConsistent()
        id
    }

    suspend fun updateCategory(id: Long, input: CategoryInput) = db.withTransaction {
        val existing = categoryDao.get(id) ?: throw FinanceException("That category no longer exists.")
        val clean = validateCategory(input, excludeId = id)
        categoryDao.update(
            existing.copy(
                name = clean.name,
                icon = clean.icon,
                color = clean.color,
                monthlyLimit = clean.monthlyLimit,
                targetAmount = clean.targetAmount,
                targetDate = clean.targetDate?.toEpochDay(),
            )
        )
    }

    private suspend fun validateCategory(input: CategoryInput, excludeId: Long): CategoryInput {
        val name = input.name.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) throw FinanceException("Please enter a name.")
        if (name.length > 40) throw FinanceException("Name is too long (max 40 characters).")
        if (name.equals("Available", ignoreCase = true)) {
            throw FinanceException("“Available” is reserved for unallocated money.")
        }
        if (categoryDao.countActiveWithName(name, excludeId) > 0) {
            throw FinanceException("A category called “$name” already exists.")
        }
        if (input.monthlyLimit != null && input.monthlyLimit <= 0) {
            throw FinanceException("Monthly limit must be greater than zero.")
        }
        if (input.targetAmount != null && input.targetAmount <= 0) {
            throw FinanceException("Target must be greater than zero.")
        }
        return input.copy(name = name)
    }

    /**
     * Categories without history are removed completely. Categories with history are archived:
     * their remaining balance is returned to Available (recorded as a transfer) and their
     * past transactions stay intact so historical reports remain correct.
     */
    suspend fun deleteCategory(id: Long): DeleteCategoryResult = db.withTransaction {
        val c = categoryDao.get(id) ?: throw FinanceException("That category no longer exists.")
        if (txDao.countForCategory(id) == 0) {
            categoryDao.delete(id)
            return@withTransaction DeleteCategoryResult.Deleted
        }
        val balance = Ledger.balances(txDao.getAll()).of(id)
        if (balance > 0) {
            val now = clock()
            txDao.insert(
                MoneyTransaction(
                    type = TxType.TRANSFER,
                    amount = balance,
                    categoryId = id,
                    toCategoryId = null,
                    description = "Returned from ${c.name} (category deleted)",
                    timestamp = now,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
        categoryDao.update(c.copy(archived = true))
        ensureConsistent()
        DeleteCategoryResult.Archived(balance.coerceAtLeast(0))
    }

    suspend fun restoreCategory(id: Long) = db.withTransaction {
        val c = categoryDao.get(id) ?: throw FinanceException("That category no longer exists.")
        if (categoryDao.countActiveWithName(c.name, id) > 0) {
            throw FinanceException("An active category called “${c.name}” already exists. Rename it first.")
        }
        categoryDao.update(c.copy(archived = false, sortOrder = categoryDao.maxSortOrder() + 1))
    }

    // ---------------------------------------------------------------- bulk

    suspend fun resetAll() = db.withTransaction {
        txDao.deleteAll()
        categoryDao.deleteAll()
    }

    /** Replaces all data atomically (used by restore). Validated before anything is deleted. */
    suspend fun replaceAll(categories: List<Category>, transactions: List<MoneyTransaction>) {
        Validation.balanceProblem(categories, transactions, formatter())?.let {
            throw FinanceException("The backup is inconsistent: $it")
        }
        db.withTransaction {
            txDao.deleteAll()
            categoryDao.deleteAll()
            categoryDao.insertAll(categories)
            txDao.insertAll(transactions)
            ensureConsistent()
        }
    }
}
