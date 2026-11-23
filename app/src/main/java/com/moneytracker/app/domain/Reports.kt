package com.moneytracker.app.domain

import com.moneytracker.app.core.Dates
import com.moneytracker.app.core.TimeRange
import com.moneytracker.app.data.db.Category
import com.moneytracker.app.data.db.MoneyTransaction
import com.moneytracker.app.data.db.TxType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class PeriodSummary(val income: Long, val expense: Long, val count: Int) {
    val net: Long get() = income - expense

    companion object {
        val EMPTY = PeriodSummary(0, 0, 0)
    }
}

/** Spending of one category (null = paid from Available) in a period. */
data class CategorySpend(val categoryId: Long?, val amount: Long, val count: Int, val share: Float)

enum class Granularity(val label: String) { DAY("Daily"), WEEK("Weekly"), MONTH("Monthly"), YEAR("Yearly") }

data class TrendBucket(val start: LocalDate, val label: String, val income: Long, val expense: Long)

data class MonthPoint(val month: YearMonth, val summary: PeriodSummary)

data class CategoryMonthRow(
    val categoryId: Long?,
    /** Income + transfers into the category during the month. */
    val added: Long,
    /** Transfers out of the category during the month. */
    val removed: Long,
    val spent: Long,
    /** Category balance at the end of the month (as the ledger stood then). */
    val balanceEnd: Long,
    val limit: Long?,
)

data class MonthReport(
    val month: YearMonth,
    val summary: PeriodSummary,
    val previous: PeriodSummary,
    val transferCount: Int,
    val rows: List<CategoryMonthRow>,
    /** Expense per day of month, index 0 = day 1. */
    val dailyExpense: List<Long>,
    val dailyIncome: List<Long>,
    val totalAtEnd: Long,
    val availableAtEnd: Long,
    val spending: List<CategorySpend>,
)

data class DayActivity(val date: LocalDate, val income: Long, val expense: Long, val transfers: Int, val count: Int)

enum class BudgetState { OK, WARNING, EXCEEDED }

data class BudgetStatus(val category: Category, val limit: Long, val spent: Long) {
    val ratio: Float get() = if (limit <= 0) 0f else spent.toFloat() / limit
    val remaining: Long get() = limit - spent
    val state: BudgetState
        get() = when {
            spent > limit -> BudgetState.EXCEEDED
            ratio >= 0.8f -> BudgetState.WARNING
            else -> BudgetState.OK
        }
}

data class GoalStatus(
    val category: Category,
    val target: Long,
    val saved: Long,
    val targetDate: LocalDate?,
    val today: LocalDate,
) {
    val remaining: Long get() = (target - saved).coerceAtLeast(0)
    val progress: Float get() = if (target <= 0) 0f else (saved.toFloat() / target).coerceIn(0f, 1f)
    val reached: Boolean get() = saved >= target
    val daysLeft: Long? get() = targetDate?.let { ChronoUnit.DAYS.between(today, it) }

    /** Amount to set aside per month to hit the target on time (null if no date or already reached). */
    val perMonthNeeded: Long?
        get() {
            val d = targetDate ?: return null
            if (reached) return null
            val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(d)) + 1
            return if (months <= 0) remaining else (remaining + months - 1) / months
        }
}

data class Insights(
    val highest: CategorySpend?,
    val lowest: CategorySpend?,
    val averageDaily: Long,
    val monthNet: Long,
    val monthExpense: Long,
    val previousExpense: Long,
    val exceeded: List<BudgetStatus>,
    val nearLimit: List<BudgetStatus>,
) {
    val expenseChange: Long get() = monthExpense - previousExpense
    val expenseChangePct: Double?
        get() = if (previousExpense == 0L) null else expenseChange * 100.0 / previousExpense
}

object Reports {

    fun summary(txs: Iterable<MoneyTransaction>, range: TimeRange): PeriodSummary {
        var inc = 0L
        var exp = 0L
        var n = 0
        for (t in txs) {
            if (t.timestamp !in range) continue
            n++
            when (t.type) {
                TxType.INCOME -> inc += t.amount
                TxType.EXPENSE -> exp += t.amount
                TxType.TRANSFER -> Unit
            }
        }
        return PeriodSummary(inc, exp, n)
    }

    fun spendingByCategory(txs: Iterable<MoneyTransaction>, range: TimeRange): List<CategorySpend> {
        val amounts = LinkedHashMap<Long?, Long>()
        val counts = HashMap<Long?, Int>()
        var total = 0L
        for (t in txs) {
            if (t.type != TxType.EXPENSE || t.timestamp !in range) continue
            amounts[t.categoryId] = (amounts[t.categoryId] ?: 0L) + t.amount
            counts[t.categoryId] = (counts[t.categoryId] ?: 0) + 1
            total += t.amount
        }
        return amounts.entries
            .map { (id, amount) ->
                CategorySpend(id, amount, counts[id] ?: 0, if (total == 0L) 0f else amount.toFloat() / total)
            }
            .sortedByDescending { it.amount }
    }

    /** [count] consecutive buckets ending with the bucket that contains [endDate]. */
    fun trend(
        txs: Iterable<MoneyTransaction>,
        granularity: Granularity,
        endDate: LocalDate,
        count: Int,
        zone: ZoneId = Dates.zone(),
        weekStart: DayOfWeek = Dates.firstDayOfWeek(),
    ): List<TrendBucket> {
        val lastStart = bucketStart(endDate, granularity, weekStart)
        val starts = (count - 1 downTo 0).map { step(lastStart, granularity, -it.toLong()) }
        val firstStart = starts.first()
        val endExclusive = step(lastStart, granularity, 1)
        val range = Dates.rangeOf(firstStart, endExclusive.minusDays(1), zone)
        val inc = LongArray(count)
        val exp = LongArray(count)
        for (t in txs) {
            if (t.type == TxType.TRANSFER || t.timestamp !in range) continue
            val d = Dates.localDate(t.timestamp, zone)
            val bs = bucketStart(d, granularity, weekStart)
            val idx = indexBetween(firstStart, bs, granularity)
            if (idx !in 0 until count) continue
            if (t.type == TxType.INCOME) inc[idx] += t.amount else exp[idx] += t.amount
        }
        return starts.mapIndexed { i, s -> TrendBucket(s, label(s, granularity), inc[i], exp[i]) }
    }

    private fun bucketStart(d: LocalDate, g: Granularity, weekStart: DayOfWeek): LocalDate = when (g) {
        Granularity.DAY -> d
        Granularity.WEEK -> Dates.weekStart(d, weekStart)
        Granularity.MONTH -> d.withDayOfMonth(1)
        Granularity.YEAR -> d.withDayOfYear(1)
    }

    private fun step(d: LocalDate, g: Granularity, n: Long): LocalDate = when (g) {
        Granularity.DAY -> d.plusDays(n)
        Granularity.WEEK -> d.plusWeeks(n)
        Granularity.MONTH -> d.plusMonths(n)
        Granularity.YEAR -> d.plusYears(n)
    }

    private fun indexBetween(first: LocalDate, s: LocalDate, g: Granularity): Int = when (g) {
        Granularity.DAY -> ChronoUnit.DAYS.between(first, s).toInt()
        Granularity.WEEK -> ChronoUnit.WEEKS.between(first, s).toInt()
        Granularity.MONTH -> ChronoUnit.MONTHS.between(first, s).toInt()
        Granularity.YEAR -> ChronoUnit.YEARS.between(first, s).toInt()
    }

    private fun label(s: LocalDate, g: Granularity): String = when (g) {
        Granularity.DAY -> s.dayOfMonth.toString()
        Granularity.WEEK -> s.format(Dates.shortDate)
        Granularity.MONTH -> s.format(Dates.shortMonth)
        Granularity.YEAR -> s.year.toString()
    }

    fun monthHistory(
        txs: List<MoneyTransaction>,
        endMonth: YearMonth,
        count: Int,
        zone: ZoneId = Dates.zone(),
    ): List<MonthPoint> = (count - 1 downTo 0).map { back ->
        val ym = endMonth.minusMonths(back.toLong())
        MonthPoint(ym, summary(txs, Dates.monthRange(ym, zone)))
    }

    fun categoryMonthlySpending(
        txs: List<MoneyTransaction>,
        categoryId: Long?,
        endMonth: YearMonth,
        count: Int,
        zone: ZoneId = Dates.zone(),
    ): List<Pair<YearMonth, Long>> = (count - 1 downTo 0).map { back ->
        val ym = endMonth.minusMonths(back.toLong())
        ym to Ledger.categoryStats(categoryId, txs, Dates.monthRange(ym, zone)).spent
    }

    /** Total balance at the end of each month — how savings grew over time. */
    fun savingsGrowth(
        txs: List<MoneyTransaction>,
        endMonth: YearMonth,
        count: Int,
        zone: ZoneId = Dates.zone(),
    ): List<Pair<YearMonth, Long>> {
        val sorted = txs.sortedBy { it.timestamp }
        return (count - 1 downTo 0).map { back ->
            val ym = endMonth.minusMonths(back.toLong())
            val end = Dates.monthRange(ym, zone).end
            var total = 0L
            for (t in sorted) {
                if (t.timestamp >= end) break
                when (t.type) {
                    TxType.INCOME -> total += t.amount
                    TxType.EXPENSE -> total -= t.amount
                    TxType.TRANSFER -> Unit
                }
            }
            ym to total
        }
    }

    fun monthReport(
        txs: List<MoneyTransaction>,
        categories: List<Category>,
        ym: YearMonth,
        zone: ZoneId = Dates.zone(),
    ): MonthReport {
        val range = Dates.monthRange(ym, zone)
        val prevRange = Dates.monthRange(ym.minusMonths(1), zone)
        val endBalances = Ledger.balances(txs, before = range.end)
        val days = ym.lengthOfMonth()
        val dailyExp = LongArray(days)
        val dailyInc = LongArray(days)
        var transfers = 0
        for (t in txs) {
            if (t.timestamp !in range) continue
            val day = Dates.localDate(t.timestamp, zone).dayOfMonth - 1
            when (t.type) {
                TxType.INCOME -> dailyInc[day] += t.amount
                TxType.EXPENSE -> dailyExp[day] += t.amount
                TxType.TRANSFER -> transfers++
            }
        }
        val catIds: List<Long?> = listOf<Long?>(null) + categories
            .filter { c ->
                !c.archived || txs.any { it.timestamp in range && Ledger.touches(it, c.id) } ||
                    (endBalances.byCategory[c.id] ?: 0L) != 0L
            }
            .map { it.id }
        val limits = categories.associate { it.id to it.monthlyLimit }
        val rows = catIds.map { id ->
            val s = Ledger.categoryStats(id, txs, range)
            CategoryMonthRow(
                categoryId = id,
                added = s.totalAdded,
                removed = s.transfersOut,
                spent = s.spent,
                balanceEnd = endBalances.of(id),
                limit = id?.let { limits[it] },
            )
        }
        return MonthReport(
            month = ym,
            summary = summary(txs, range),
            previous = summary(txs, prevRange),
            transferCount = transfers,
            rows = rows,
            dailyExpense = dailyExp.toList(),
            dailyIncome = dailyInc.toList(),
            totalAtEnd = endBalances.total,
            availableAtEnd = endBalances.available,
            spending = spendingByCategory(txs, range),
        )
    }

    fun dailyActivity(
        txs: Iterable<MoneyTransaction>,
        ym: YearMonth,
        zone: ZoneId = Dates.zone(),
    ): Map<LocalDate, DayActivity> {
        val range = Dates.monthRange(ym, zone)
        val map = HashMap<LocalDate, DayActivity>()
        for (t in txs) {
            if (t.timestamp !in range) continue
            val d = Dates.localDate(t.timestamp, zone)
            val cur = map[d] ?: DayActivity(d, 0, 0, 0, 0)
            map[d] = when (t.type) {
                TxType.INCOME -> cur.copy(income = cur.income + t.amount, count = cur.count + 1)
                TxType.EXPENSE -> cur.copy(expense = cur.expense + t.amount, count = cur.count + 1)
                TxType.TRANSFER -> cur.copy(transfers = cur.transfers + 1, count = cur.count + 1)
            }
        }
        return map
    }

    fun budgets(
        txs: List<MoneyTransaction>,
        categories: List<Category>,
        ym: YearMonth,
        zone: ZoneId = Dates.zone(),
    ): List<BudgetStatus> {
        val range = Dates.monthRange(ym, zone)
        return categories.filter { !it.archived && it.monthlyLimit != null && it.monthlyLimit > 0 }
            .map { c -> BudgetStatus(c, c.monthlyLimit!!, Ledger.categoryStats(c.id, txs, range).spent) }
    }

    fun goals(
        categories: List<Category>,
        balances: Balances,
        today: LocalDate,
    ): List<GoalStatus> = categories
        .filter { !it.archived && it.targetAmount != null && it.targetAmount > 0 }
        .map { c ->
            GoalStatus(
                category = c,
                target = c.targetAmount!!,
                saved = balances.of(c.id),
                targetDate = c.targetDate?.let { LocalDate.ofEpochDay(it) },
                today = today,
            )
        }

    fun insights(
        txs: List<MoneyTransaction>,
        categories: List<Category>,
        ym: YearMonth,
        today: LocalDate,
        zone: ZoneId = Dates.zone(),
    ): Insights {
        val range = Dates.monthRange(ym, zone)
        val spending = spendingByCategory(txs, range).filter { it.categoryId != null }
        val month = summary(txs, range)
        val prev = summary(txs, Dates.monthRange(ym.minusMonths(1), zone))
        val current = YearMonth.from(today)
        val daysElapsed = when {
            ym == current -> today.dayOfMonth
            ym.isBefore(current) -> ym.lengthOfMonth()
            else -> 0
        }
        val budgets = budgets(txs, categories, ym, zone)
        return Insights(
            highest = spending.maxByOrNull { it.amount },
            lowest = if (spending.size >= 2) spending.minByOrNull { it.amount } else null,
            averageDaily = if (daysElapsed == 0) 0 else month.expense / daysElapsed,
            monthNet = month.net,
            monthExpense = month.expense,
            previousExpense = prev.expense,
            exceeded = budgets.filter { it.state == BudgetState.EXCEEDED },
            nearLimit = budgets.filter { it.state == BudgetState.WARNING },
        )
    }
}
