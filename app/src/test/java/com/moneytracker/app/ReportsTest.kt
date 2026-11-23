package com.moneytracker.app

import com.moneytracker.app.Books.Companion.u
import com.moneytracker.app.core.Dates
import com.moneytracker.app.domain.BudgetState
import com.moneytracker.app.domain.Granularity
import com.moneytracker.app.domain.Reports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class ReportsTest {

    private fun books(): Triple<Books, Long, Long> {
        val b = Books()
        val food = b.cat("Food", limit = 100.0)
        val savings = b.cat("Savings", target = 500.0)
        // October
        b.income(1000.0, at = b.ts(2026, 10, 1))
        b.transfer(200.0, null, food, at = b.ts(2026, 10, 1, 14))
        b.transfer(300.0, null, savings, at = b.ts(2026, 10, 1, 15))
        b.expense(40.0, food, at = b.ts(2026, 10, 10))
        b.expense(25.0, null, at = b.ts(2026, 10, 20))
        // November
        b.income(800.0, at = b.ts(2026, 11, 1))
        b.expense(90.0, food, at = b.ts(2026, 11, 3))
        b.expense(30.0, food, at = b.ts(2026, 11, 4))
        b.expense(10.0, savings, at = b.ts(2026, 11, 4))
        b.transfer(50.0, savings, food, at = b.ts(2026, 11, 5))
        return Triple(b, food, savings)
    }

    @Test fun monthlyReportsDoNotMixMonths() {
        val (b, food, savings) = books()
        val oct = Reports.monthReport(b.txs, b.categories, YearMonth.of(2026, 10), b.zone)
        assertEquals(u(1000.0), oct.summary.income)
        assertEquals(u(65.0), oct.summary.expense)
        assertEquals(u(935.0), oct.summary.net)
        assertEquals(5, oct.summary.count)
        val octFood = oct.rows.first { it.categoryId == food }
        assertEquals(u(200.0), octFood.added)
        assertEquals(u(40.0), octFood.spent)
        assertEquals(u(160.0), octFood.balanceEnd)
        assertEquals(u(935.0), oct.totalAtEnd)
        assertEquals(u(475.0), oct.availableAtEnd)

        val nov = Reports.monthReport(b.txs, b.categories, YearMonth.of(2026, 11), b.zone)
        assertEquals(u(800.0), nov.summary.income)
        assertEquals(u(130.0), nov.summary.expense)
        assertEquals(oct.summary, nov.previous)
        val novFood = nov.rows.first { it.categoryId == food }
        assertEquals(u(50.0), novFood.added)
        assertEquals(u(120.0), novFood.spent)
        assertEquals(u(90.0), novFood.balanceEnd)
        val novSavings = nov.rows.first { it.categoryId == savings }
        assertEquals(u(50.0), novSavings.removed)
        assertEquals(u(10.0), novSavings.spent)
        assertEquals(u(240.0), novSavings.balanceEnd)
        assertEquals(u(40.0), nov.dailyExpense[3]) // day 4: 30 food + 10 savings
        assertEquals(30, nov.dailyExpense.size)
    }

    @Test fun historicalReportStableWhenNewMonthsAreAdded() {
        val (b, _, _) = books()
        val before = Reports.monthReport(b.txs, b.categories, YearMonth.of(2026, 10), b.zone)
        b.income(999.0, at = b.ts(2026, 12, 1))
        b.expense(123.0, at = b.ts(2026, 12, 2))
        val after = Reports.monthReport(b.txs, b.categories, YearMonth.of(2026, 10), b.zone)
        assertEquals(before, after)
    }

    @Test fun spendingBreakdownSharesSumToOne() {
        val (b, food, savings) = books()
        val s = Reports.spendingByCategory(b.txs, Dates.monthRange(YearMonth.of(2026, 11), b.zone))
        assertEquals(food, s[0].categoryId)
        assertEquals(u(120.0), s[0].amount)
        assertEquals(2, s[0].count)
        assertEquals(savings, s[1].categoryId)
        assertEquals(1f, s.sumOf { it.share.toDouble() }.toFloat(), 0.0001f)
    }

    @Test fun trendBucketsAndTotals() {
        val (b, _, _) = books()
        val days = Reports.trend(b.txs, Granularity.DAY, LocalDate.of(2026, 11, 5), 7, b.zone, DayOfWeek.MONDAY)
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 10, 30), days.first().start)
        assertEquals(u(90.0), days.first { it.start == LocalDate.of(2026, 11, 3) }.expense)
        assertEquals(u(40.0), days.first { it.start == LocalDate.of(2026, 11, 4) }.expense)

        val months = Reports.trend(b.txs, Granularity.MONTH, LocalDate.of(2026, 11, 20), 3, b.zone)
        assertEquals(listOf(0L, u(65.0), u(130.0)), months.map { it.expense })
        assertEquals(listOf(0L, u(1000.0), u(800.0)), months.map { it.income })

        val weeks = Reports.trend(b.txs, Granularity.WEEK, LocalDate.of(2026, 11, 5), 2, b.zone, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 11, 2), weeks.last().start)
        assertEquals(u(130.0), weeks.last().expense)

        val years = Reports.trend(b.txs, Granularity.YEAR, LocalDate.of(2026, 11, 5), 2, b.zone)
        assertEquals(u(195.0), years.last().expense)
    }

    @Test fun savingsGrowthIsCumulativeBalance() {
        val (b, _, _) = books()
        val g = Reports.savingsGrowth(b.txs, YearMonth.of(2026, 11), 3, b.zone)
        assertEquals(listOf(0L, u(935.0), u(1605.0)), g.map { it.second })
    }

    @Test fun budgetsWarnAndExceed() {
        val (b, food, _) = books()
        val nov = Reports.budgets(b.txs, b.categories, YearMonth.of(2026, 11), b.zone)
        val foodBudget = nov.first { it.category.id == food }
        assertEquals(u(120.0), foodBudget.spent)
        assertEquals(BudgetState.EXCEEDED, foodBudget.state)
        val oct = Reports.budgets(b.txs, b.categories, YearMonth.of(2026, 10), b.zone)
        assertEquals(BudgetState.OK, oct.first().state)
        b.expense(45.0, food, at = b.ts(2026, 10, 12)) // 85 of 100 -> warning
        val oct2 = Reports.budgets(b.txs, b.categories, YearMonth.of(2026, 10), b.zone)
        assertEquals(BudgetState.WARNING, oct2.first().state)
    }

    @Test fun goalsProgressAndMonthlyNeed() {
        val (b, _, savings) = books()
        val bal = com.moneytracker.app.domain.Ledger.balances(b.txs)
        val cats = b.categories.map { if (it.id == savings) it.copy(targetDate = LocalDate.of(2027, 2, 28).toEpochDay()) else it }
        val g = Reports.goals(cats, bal, LocalDate.of(2026, 11, 24)).single()
        assertEquals(u(240.0), g.saved)
        assertEquals(u(260.0), g.remaining)
        assertEquals(0.48f, g.progress, 0.0001f)
        assertEquals(u(65.0), g.perMonthNeeded) // Nov, Dec, Jan, Feb = 4 months
        assertTrue(g.daysLeft!! > 0)
    }

    @Test fun insightsForMonth() {
        val (b, food, savings) = books()
        val i = Reports.insights(b.txs, b.categories, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 10), b.zone)
        assertEquals(food, i.highest!!.categoryId)
        assertEquals(savings, i.lowest!!.categoryId)
        assertEquals(u(13.0), i.averageDaily) // 130 / 10 days
        assertEquals(u(65.0), i.expenseChange)
        assertEquals(100.0, i.expenseChangePct!!, 0.001)
        assertEquals(1, i.exceeded.size)

        val past = Reports.insights(b.txs, b.categories, YearMonth.of(2026, 10), LocalDate.of(2026, 11, 10), b.zone)
        assertEquals(u(65.0) / 31, past.averageDaily)
        assertNull(past.lowest) // only one category had spending
    }

    @Test fun calendarDailyActivity() {
        val (b, _, _) = books()
        val m = Reports.dailyActivity(b.txs, YearMonth.of(2026, 11), b.zone)
        val d4 = m[LocalDate.of(2026, 11, 4)]!!
        assertEquals(u(40.0), d4.expense)
        assertEquals(2, d4.count)
        assertEquals(1, m[LocalDate.of(2026, 11, 5)]!!.transfers)
        assertEquals(u(800.0), m[LocalDate.of(2026, 11, 1)]!!.income)
    }
}
