package com.moneytracker.app

import com.moneytracker.app.Books.Companion.u
import com.moneytracker.app.core.MoneyFormatter
import com.moneytracker.app.domain.Ledger
import com.moneytracker.app.domain.Validation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The exact scenarios from the specification ("Financial Accuracy and Error Prevention"). */
class LedgerTest {
    private val fmt = MoneyFormatter("OMR", 3)

    @Test fun specExampleAllocationOf500() {
        val b = Books()
        val savings = b.cat("Savings")
        val food = b.cat("Food")
        val shopping = b.cat("Shopping")
        val transport = b.cat("Transportation")
        b.income(500.0)
        b.transfer(150.0, null, savings)
        b.transfer(100.0, null, food)
        b.transfer(50.0, null, shopping)
        b.transfer(50.0, null, transport)

        var bal = Ledger.balances(b.txs)
        assertEquals(u(500.0), bal.total)
        assertEquals(u(150.0), bal.available)
        assertEquals(u(350.0), bal.allocated)
        assertEquals(u(100.0), bal.of(food))

        // "If I spend 15 OMR on food, the Food category should automatically decrease to 85 OMR."
        b.expense(15.0, food)
        bal = Ledger.balances(b.txs)
        assertEquals(u(85.0), bal.of(food))
        assertEquals(u(485.0), bal.total)
        assertEquals(u(150.0), bal.available)
    }

    @Test fun incomeIncreasesAndExpenseDecreasesTotal() {
        val b = Books()
        b.income(50.0)
        assertEquals(u(50.0), Ledger.balances(b.txs).total)
        b.expense(20.0)
        assertEquals(u(30.0), Ledger.balances(b.txs).total)
    }

    @Test fun allocatingDoesNotChangeTotal() {
        val b = Books()
        val savings = b.cat("Savings")
        b.income(300.0)
        val before = Ledger.balances(b.txs).total
        b.transfer(100.0, null, savings)
        val after = Ledger.balances(b.txs)
        assertEquals(before, after.total)
        assertEquals(u(100.0), after.of(savings))
        assertEquals(u(200.0), after.available)
    }

    @Test fun movingBetweenCategoriesDoesNotChangeTotal() {
        val b = Books()
        val savings = b.cat("Savings")
        val shopping = b.cat("Shopping")
        b.income(300.0)
        b.transfer(100.0, null, savings)
        val before = Ledger.balances(b.txs)
        b.transfer(30.0, savings, shopping)
        val after = Ledger.balances(b.txs)
        assertEquals(before.total, after.total)
        assertEquals(before.available, after.available)
        assertEquals(u(70.0), after.of(savings))
        assertEquals(u(30.0), after.of(shopping))
    }

    @Test fun spendingFromCategoryReducesCategoryAndTotal() {
        val b = Books()
        val savings = b.cat("Savings")
        b.income(300.0)
        b.transfer(100.0, null, savings)
        b.expense(10.0, savings)
        val bal = Ledger.balances(b.txs)
        assertEquals(u(90.0), bal.of(savings))
        assertEquals(u(290.0), bal.total)
        assertEquals(u(200.0), bal.available)
    }

    @Test fun editingRecalculatesAndDeletingReverses() {
        val b = Books()
        val food = b.cat("Food")
        b.income(200.0)
        b.transfer(100.0, null, food)
        val spend = b.expense(20.0, food)

        // edit amount 20 -> 35
        b.txs[b.txs.indexOf(spend)] = spend.copy(amount = u(35.0))
        var bal = Ledger.balances(b.txs)
        assertEquals(u(65.0), bal.of(food))
        assertEquals(u(165.0), bal.total)

        // move the expense to Available instead
        b.txs[b.txs.indexOfFirst { it.id == spend.id }] = spend.copy(amount = u(35.0), categoryId = null)
        bal = Ledger.balances(b.txs)
        assertEquals(u(100.0), bal.of(food))
        assertEquals(u(65.0), bal.available)

        // delete -> fully reversed
        b.txs.removeAll { it.id == spend.id }
        bal = Ledger.balances(b.txs)
        assertEquals(u(100.0), bal.of(food))
        assertEquals(u(100.0), bal.available)
        assertEquals(u(200.0), bal.total)
    }

    @Test fun invariantTotalEqualsAvailablePlusCategories() {
        val b = Books()
        val ids = (1..5).map { b.cat("C$it") }
        b.income(1000.0)
        b.income(250.5, ids[0])
        ids.forEachIndexed { i, id -> b.transfer(10.0 * (i + 1), null, id) }
        b.transfer(5.0, ids[1], ids[2])
        b.expense(3.25, ids[2])
        b.expense(7.0)
        val bal = Ledger.balances(b.txs)
        assertEquals(bal.total, bal.available + bal.byCategory.values.sum())
    }

    @Test fun categoryStatsSplitMovements() {
        val b = Books()
        val food = b.cat("Food")
        val other = b.cat("Other")
        b.income(500.0)
        b.income(20.0, food)
        b.transfer(100.0, null, food)
        b.transfer(10.0, food, other)
        b.expense(15.0, food)
        val s = Ledger.categoryStats(food, b.txs)
        assertEquals(u(120.0), s.totalAdded)
        assertEquals(u(10.0), s.transfersOut)
        assertEquals(u(15.0), s.spent)
        assertEquals(u(95.0), s.net)
        assertEquals(Ledger.balances(b.txs).of(food), s.net)
    }

    @Test fun validationRejectsOverspendingAndOverAllocation() {
        val b = Books()
        val food = b.cat("Food")
        b.income(100.0)
        assertNull(Validation.balanceProblem(b.categories, b.txs, fmt))

        b.transfer(150.0, null, food) // allocate more than we have
        val p1 = Validation.balanceProblem(b.categories, b.txs, fmt)
        assertNotNull(p1)
        assertTrue(p1!!.contains("available", ignoreCase = true))
        b.txs.removeAt(b.txs.lastIndex)

        b.transfer(40.0, null, food)
        b.expense(41.0, food) // insufficient category balance
        val p2 = Validation.balanceProblem(b.categories, b.txs, fmt)
        assertNotNull(p2)
        assertTrue(p2!!.contains("Food"))
    }

    @Test fun deletingAllocatedIncomeIsDetected() {
        val b = Books()
        val savings = b.cat("Savings")
        val salary = b.income(100.0)
        b.transfer(80.0, null, savings)
        b.txs.remove(salary)
        assertNotNull(Validation.balanceProblem(b.categories, b.txs, fmt))
    }

    @Test fun balancesBeforeTimestampIgnoreLaterTransactions() {
        val b = Books()
        b.income(100.0, at = b.ts(2026, 10, 5))
        b.expense(30.0, at = b.ts(2026, 11, 5))
        assertEquals(u(100.0), Ledger.balances(b.txs, before = b.ts(2026, 11, 1, 0)).total)
        assertEquals(u(70.0), Ledger.balances(b.txs).total)
    }
}
