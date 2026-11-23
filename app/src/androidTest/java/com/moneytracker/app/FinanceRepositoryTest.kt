package com.moneytracker.app

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneytracker.app.core.MoneyFormatter
import com.moneytracker.app.data.AppSettings
import com.moneytracker.app.data.BackupCodec
import com.moneytracker.app.data.BackupData
import com.moneytracker.app.data.CategoryInput
import com.moneytracker.app.data.DeleteCategoryResult
import com.moneytracker.app.data.FinanceRepository
import com.moneytracker.app.data.TxDraft
import com.moneytracker.app.data.db.AppDatabase
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.domain.FinanceException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.math.roundToLong

/** Integration tests: real Room database (in memory) + repository with atomic validation. */
@RunWith(AndroidJUnit4::class)
class FinanceRepositoryTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository

    private fun u(x: Double) = (x * 1000).roundToLong()
    private val now get() = System.currentTimeMillis() - 60_000

    @Before fun setUp() {
        db = AppDatabase.inMemory(ctx)
        repo = FinanceRepository(db, { MoneyFormatter("OMR", 3) })
    }

    @After fun tearDown() = db.close()

    private suspend fun cat(name: String) = repo.createCategory(CategoryInput(name, "category", 0xFF10B981.toInt()))
    private suspend fun income(x: Double, to: Long? = null, desc: String = "") =
        repo.addTransaction(TxDraft(TxType.INCOME, u(x), to, null, desc, "", now))
    private suspend fun expense(x: Double, from: Long? = null) =
        repo.addTransaction(TxDraft(TxType.EXPENSE, u(x), from, null, "", "", now))
    private suspend fun move(x: Double, from: Long?, to: Long?) =
        repo.addTransaction(TxDraft(TxType.TRANSFER, u(x), from, to, "", "", now))

    private suspend fun expectRejected(block: suspend () -> Unit) {
        val before = repo.currentSnapshot()
        try {
            block()
            fail("Expected the operation to be rejected")
        } catch (e: FinanceException) {
            assertTrue(e.message!!.isNotBlank())
        }
        val after = repo.currentSnapshot()
        assertEquals("Rejected operation must not change the database", before.transactions, after.transactions)
        assertEquals(before.categories, after.categories)
    }

    @Test fun specScenario() = runBlocking<Unit> {
        val savings = cat("Savings")
        val food = cat("Food")
        val shopping = cat("Shopping")
        val transport = cat("Transportation")
        income(500.0)
        move(150.0, null, savings)
        move(100.0, null, food)
        move(50.0, null, shopping)
        move(50.0, null, transport)
        var b = repo.currentSnapshot().balances
        assertEquals(u(500.0), b.total)
        assertEquals(u(150.0), b.available)

        expense(15.0, food)
        b = repo.currentSnapshot().balances
        assertEquals(u(85.0), b.of(food))
        assertEquals(u(485.0), b.total)

        move(30.0, savings, shopping)
        b = repo.currentSnapshot().balances
        assertEquals(u(485.0), b.total)
        assertEquals(u(120.0), b.of(savings))
        assertEquals(u(80.0), b.of(shopping))

        expense(10.0, savings)
        b = repo.currentSnapshot().balances
        assertEquals(u(110.0), b.of(savings))
        assertEquals(u(475.0), b.total)
    }

    @Test fun rejectsInvalidOperationsAtomically() = runBlocking<Unit> {
        val food = cat("Food")
        income(50.0)
        move(20.0, null, food)
        expectRejected { expense(31.0) }                // only 30 available
        expectRejected { expense(20.001, food) }        // category short by 1 baisa
        expectRejected { move(31.0, null, food) }       // over-allocation
        expectRejected { move(5.0, food, food) }        // same place
        expectRejected { move(5.0, null, null) }
        expectRejected { repo.addTransaction(TxDraft(TxType.INCOME, 0, null, null, "", "", now)) }
        expectRejected { repo.addTransaction(TxDraft(TxType.INCOME, -5, null, null, "", "", now)) }
        expectRejected {
            val tomorrow = System.currentTimeMillis() + 2 * 24 * 3600 * 1000L
            repo.addTransaction(TxDraft(TxType.INCOME, u(1.0), null, null, "", "", tomorrow))
        }
        expectRejected { repo.addTransaction(TxDraft(TxType.EXPENSE, u(1.0), 9999L, null, "", "", now)) }
        // exact amounts are fine
        expense(20.0, food)
        expense(30.0)
        assertEquals(0L, repo.currentSnapshot().balances.total)
    }

    @Test fun editRecalculatesAndDeleteReverses() = runBlocking<Unit> {
        val food = cat("Food")
        income(200.0)
        move(100.0, null, food)
        val spend = expense(20.0, food)
        repo.updateTransaction(spend, TxDraft(TxType.EXPENSE, u(35.0), food, null, "Groceries", "", now))
        var b = repo.currentSnapshot().balances
        assertEquals(u(65.0), b.of(food))
        assertEquals(u(165.0), b.total)

        // edit that would overdraw the category is rejected and nothing changes
        expectRejected { repo.updateTransaction(spend, TxDraft(TxType.EXPENSE, u(150.0), food, null, "", "", now)) }

        // change type: expense -> income
        repo.updateTransaction(spend, TxDraft(TxType.INCOME, u(35.0), null, null, "Refund", "", now))
        b = repo.currentSnapshot().balances
        assertEquals(u(235.0), b.total)
        assertEquals(u(100.0), b.of(food))

        repo.deleteTransaction(spend)
        b = repo.currentSnapshot().balances
        assertEquals(u(200.0), b.total)
        assertEquals(u(100.0), b.available)
        assertEquals(2, repo.currentSnapshot().transactions.size)
    }

    @Test fun deletingAllocatedIncomeIsRejected() = runBlocking<Unit> {
        val savings = cat("Savings")
        val salary = income(100.0)
        move(80.0, null, savings)
        expectRejected { repo.deleteTransaction(salary) }
    }

    @Test fun noDuplicates() = runBlocking<Unit> {
        income(10.0)
        income(10.0)
        val s = repo.currentSnapshot()
        assertEquals(2, s.transactions.size)
        assertEquals(2, s.transactions.map { it.id }.toSet().size)
        assertEquals(u(20.0), s.balances.total)
    }

    @Test fun categoryLifecycle() = runBlocking<Unit> {
        income(300.0)
        val id = repo.createCategory(CategoryInput("Travel", "flight", 1, targetAmount = u(500.0)), initialAllocation = u(120.0))
        assertEquals(u(120.0), repo.currentSnapshot().balances.of(id))
        expectRejected { cat("travel") }   // duplicate (case-insensitive)
        expectRejected { cat("   ") }
        expectRejected { cat("Available") }
        expectRejected { repo.createCategory(CategoryInput("Big", "x", 1), initialAllocation = u(1000.0)) }

        repo.updateCategory(id, CategoryInput("Trips", "flight", 2, monthlyLimit = u(50.0), targetAmount = u(500.0), targetDate = LocalDate.of(2030, 1, 1)))
        val c = repo.getCategory(id)!!
        assertEquals("Trips", c.name)
        assertEquals(u(50.0), c.monthlyLimit)

        expense(20.0, id)
        val totalBefore = repo.currentSnapshot().balances.total
        val result = repo.deleteCategory(id)
        assertTrue(result is DeleteCategoryResult.Archived)
        assertEquals(u(100.0), (result as DeleteCategoryResult.Archived).returnedToAvailable)
        val s = repo.currentSnapshot()
        assertEquals(totalBefore, s.balances.total)
        assertEquals(0L, s.balances.of(id))
        assertEquals(s.balances.total, s.balances.available)
        assertTrue(s.category(id)!!.archived)
        // history is kept
        assertTrue(s.transactions.any { it.categoryId == id && it.type == TxType.EXPENSE })
        // cannot be used for new transactions
        expectRejected { expense(1.0, id) }
        // restore
        repo.restoreCategory(id)
        assertFalse(repo.getCategory(id)!!.archived)

        // empty category is deleted for real
        val empty = cat("Empty")
        assertEquals(DeleteCategoryResult.Deleted, repo.deleteCategory(empty))
        assertEquals(null, repo.getCategory(empty))
    }

    @Test fun backupRoundTripAndInvalidRestoreKeepsData() = runBlocking<Unit> {
        val food = cat("Food")
        income(250.0, desc = "Salary")
        move(60.0, null, food)
        expense(12.5, food)
        val before = repo.currentSnapshot()
        val json = BackupCodec.encode(BackupData(AppSettings(), before.categories, before.transactions), 0)

        repo.resetAll()
        assertEquals(0, repo.currentSnapshot().transactions.size)

        val decoded = BackupCodec.decode(json)
        repo.replaceAll(decoded.categories, decoded.transactions)
        val after = repo.currentSnapshot()
        assertEquals(before.transactions, after.transactions)
        assertEquals(before.categories, after.categories)
        assertEquals(before.balances, after.balances)

        // An internally inconsistent backup (spends more than it has) is refused and nothing is touched.
        val bad = decoded.transactions.map { if (it.type == TxType.EXPENSE) it.copy(amount = u(9999.0)) else it }
        expectRejected { repo.replaceAll(decoded.categories, bad) }
    }

    @Test fun backupManagerFilesCsvAndPdf() = runBlocking<Unit> {
        val app = ctx.applicationContext as MoneyTrackerApplication
        val c = app.container
        c.finance.resetAll()
        val food = c.finance.createCategory(CategoryInput("Food", "restaurant", 1))
        c.finance.addTransaction(TxDraft(TxType.INCOME, u(100.0), null, null, "Salary", "", now))
        c.finance.addTransaction(TxDraft(TxType.TRANSFER, u(40.0), null, food, "", "", now))
        c.finance.addTransaction(TxDraft(TxType.EXPENSE, u(7.25), food, null, "Lunch, with \"friends\"", "", now))
        val before = c.finance.currentSnapshot()

        val dir = ctx.cacheDir
        val backup = java.io.File(dir, "backup-test.json")
        c.backup.exportBackup(Uri.fromFile(backup))
        assertTrue(backup.length() > 100)

        c.finance.resetAll()
        c.backup.restoreBackup(Uri.fromFile(backup))
        val after = c.finance.currentSnapshot()
        assertEquals(before.transactions, after.transactions)
        assertEquals(before.balances, after.balances)

        val broken = java.io.File(dir, "broken.json").apply { writeText("{\"app\":\"MoneyTrackerApp\"") }
        try {
            c.backup.restoreBackup(Uri.fromFile(broken))
            fail("broken backup must be rejected")
        } catch (e: FinanceException) {
            assertNotNull(e.message)
        }
        assertEquals(before.transactions, c.finance.currentSnapshot().transactions)

        val csv = java.io.File(dir, "tx.csv")
        c.backup.exportCsv(Uri.fromFile(csv))
        val text = csv.readText(Charsets.UTF_8).removePrefix("﻿")
        assertTrue(text.startsWith("Date,Time,Type,Amount"))
        assertTrue(text.contains("\"Lunch, with \"\"friends\"\"\""))
        assertTrue(text.contains("Expense,7.250,-7.250,OMR"))

        val pdf = java.io.File(dir, "report.pdf")
        c.backup.exportMonthPdf(Uri.fromFile(pdf), java.time.YearMonth.now())
        val head = pdf.readBytes().copyOfRange(0, 4).toString(Charsets.US_ASCII)
        assertEquals("%PDF", head)
        c.finance.resetAll()
    }

    @Test fun fileDatabaseSurvivesReopen() = runBlocking<Unit> {
        val name = "persist-test.db"
        ctx.deleteDatabase(name)
        var fileDb = Room.databaseBuilder(ctx, AppDatabase::class.java, name).build()
        var r = FinanceRepository(fileDb, { MoneyFormatter("OMR", 3) })
        val c = r.createCategory(CategoryInput("Savings", "savings", 1))
        r.addTransaction(TxDraft(TxType.INCOME, u(42.5), null, null, "", "", now))
        r.addTransaction(TxDraft(TxType.TRANSFER, u(12.5), null, c, "", "", now))
        val before = r.currentSnapshot()
        fileDb.close()

        fileDb = Room.databaseBuilder(ctx, AppDatabase::class.java, name).build()
        r = FinanceRepository(fileDb, { MoneyFormatter("OMR", 3) })
        val after = r.currentSnapshot()
        assertEquals(before.transactions, after.transactions)
        assertEquals(u(12.5), after.balances.of(c))
        fileDb.close()
        ctx.deleteDatabase(name)
    }
}
