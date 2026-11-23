package com.moneytracker.app.ui.reports

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.moneytracker.app.AppContainer
import com.moneytracker.app.core.Dates
import com.moneytracker.app.core.TimeRange
import com.moneytracker.app.data.FinanceSnapshot
import com.moneytracker.app.data.db.MoneyTransaction
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.domain.*
import com.moneytracker.app.ui.AppViewModelProvider
import com.moneytracker.app.ui.LocalMoney
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.*
import com.moneytracker.app.ui.theme.FinanceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs

enum class StatPeriod(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All") }

data class StatsParams(
    val period: StatPeriod = StatPeriod.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val granularity: Granularity = Granularity.DAY,
    val month: YearMonth = YearMonth.now(),
    /** Category to compare across months on the Monthly tab; -1 = Available, null = auto. */
    val compareCategory: Long? = null,
)

data class OverviewState(
    val periodLabel: String,
    val summary: PeriodSummary,
    val spending: List<CategorySpend>,
    val spendingTx: Map<Long?, List<MoneyTransaction>>,
    val incomeVsExpense: List<MonthPoint>,
    val trend: List<TrendBucket>,
    val savingsGrowth: List<Pair<YearMonth, Long>>,
    val totalSaved: Long,
    val goals: List<GoalStatus>,
    val insights: Insights,
    val insightsMonth: YearMonth,
)

data class MonthlyState(
    val report: MonthReport,
    val history: List<MonthPoint>,
    val compareCategory: Long?,
    val categorySeries: List<Pair<YearMonth, Long>>,
)

data class StatsState(
    val snapshot: FinanceSnapshot,
    val params: StatsParams,
    val overview: OverviewState,
    val monthly: MonthlyState,
)

class StatisticsViewModel(handle: SavedStateHandle, private val container: AppContainer) : ViewModel() {
    val initialTab: Int = handle.get<Int>("tab") ?: 0
    val params = MutableStateFlow(
        StatsParams(
            month = handle.get<String>("ym")?.takeIf { it.isNotBlank() }
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.now(),
        )
    )
    val message = MutableStateFlow<String?>(null)

    val state: StateFlow<StatsState?> = combine(container.finance.snapshot, params) { s, p ->
        StatsState(s, p, overview(s, p), monthly(s, p))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun periodRange(p: StatsParams): TimeRange = when (p.period) {
        StatPeriod.WEEK -> Dates.weekStart(p.anchor).let { Dates.rangeOf(it, it.plusDays(6)) }
        StatPeriod.MONTH -> Dates.monthRange(YearMonth.from(p.anchor))
        StatPeriod.YEAR -> Dates.yearRange(p.anchor.year)
        StatPeriod.ALL -> TimeRange.ALL
    }

    private fun periodLabel(p: StatsParams): String = when (p.period) {
        StatPeriod.WEEK -> Dates.weekStart(p.anchor).let { "${it.format(Dates.shortDate)} – ${it.plusDays(6).format(Dates.shortDate)}" }
        StatPeriod.MONTH -> YearMonth.from(p.anchor).format(Dates.monthYear)
        StatPeriod.YEAR -> p.anchor.year.toString()
        StatPeriod.ALL -> "All time"
    }

    private fun overview(s: FinanceSnapshot, p: StatsParams): OverviewState {
        val txs = s.transactions
        val range = periodRange(p)
        val today = LocalDate.now()
        val endDate = when (p.period) {
            StatPeriod.ALL -> today
            else -> minOf(today, Dates.localDate(range.end - 1))
        }
        val endMonth = YearMonth.from(endDate)
        val count = when (p.granularity) {
            Granularity.DAY -> 14
            Granularity.WEEK -> 12
            Granularity.MONTH -> 12
            Granularity.YEAR -> 5
        }
        val spending = Reports.spendingByCategory(txs, range)
        val spendingTx = txs.filter { it.type == TxType.EXPENSE && it.timestamp in range }.groupBy { it.categoryId }
        val insightsMonth = if (p.period == StatPeriod.WEEK || p.period == StatPeriod.MONTH) YearMonth.from(p.anchor) else YearMonth.now()
        return OverviewState(
            periodLabel = periodLabel(p),
            summary = Reports.summary(txs, range),
            spending = spending,
            spendingTx = spendingTx,
            incomeVsExpense = Reports.monthHistory(txs, endMonth, 6),
            trend = Reports.trend(txs, p.granularity, endDate, count),
            savingsGrowth = Reports.savingsGrowth(txs, endMonth, 12),
            totalSaved = s.balances.total,
            goals = Reports.goals(s.categories, s.balances, today),
            insights = Reports.insights(txs, s.categories, insightsMonth, today),
            insightsMonth = insightsMonth,
        )
    }

    private fun monthly(s: FinanceSnapshot, p: StatsParams): MonthlyState {
        val report = Reports.monthReport(s.transactions, s.categories, p.month)
        val auto = report.spending.firstOrNull()?.categoryId ?: s.activeCategories.firstOrNull()?.id
        val compare = when (p.compareCategory) {
            null -> auto
            -1L -> null
            else -> p.compareCategory
        }
        val compareKey = if (p.compareCategory == null && auto == null) null else compare
        return MonthlyState(
            report = report,
            history = Reports.monthHistory(s.transactions, p.month, 6),
            compareCategory = compareKey,
            categorySeries = Reports.categoryMonthlySpending(s.transactions, compareKey, p.month, 6),
        )
    }

    fun setPeriod(period: StatPeriod) = params.update {
        it.copy(
            period = period,
            anchor = LocalDate.now(),
            granularity = when (period) {
                StatPeriod.WEEK, StatPeriod.MONTH -> Granularity.DAY
                StatPeriod.YEAR -> Granularity.MONTH
                StatPeriod.ALL -> Granularity.MONTH
            },
        )
    }

    fun shift(dir: Int) = params.update {
        val a = when (it.period) {
            StatPeriod.WEEK -> it.anchor.plusWeeks(dir.toLong())
            StatPeriod.MONTH -> it.anchor.plusMonths(dir.toLong())
            StatPeriod.YEAR -> it.anchor.plusYears(dir.toLong())
            StatPeriod.ALL -> it.anchor
        }
        it.copy(anchor = a)
    }

    fun setGranularity(g: Granularity) = params.update { it.copy(granularity = g) }
    fun setMonth(ym: YearMonth) = params.update { it.copy(month = ym) }
    fun setCompare(id: Long?) = params.update { it.copy(compareCategory = id ?: -1L) }

    fun exportPdf(uri: Uri, ym: YearMonth) = viewModelScope.launch {
        message.value = try {
            container.backup.exportMonthPdf(uri, ym)
            "Report for ${ym.format(Dates.monthYear)} saved"
        } catch (e: Exception) {
            "Couldn't save the report: ${e.message}"
        }
    }

    fun exportMonthCsv(uri: Uri, ym: YearMonth) = viewModelScope.launch {
        message.value = try {
            container.backup.exportCsv(uri, Dates.monthRange(ym))
            "Transactions for ${ym.format(Dates.monthYear)} exported"
        } catch (e: Exception) {
            "Couldn't export: ${e.message}"
        }
    }
}

@Composable
fun StatisticsScreen(nav: NavController, vm: StatisticsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(vm.initialTab) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Overview") }, modifier = Modifier.testTag("statsTab_0"))
                Tab(tab == 1, { tab = 1 }, text = { Text("Monthly") }, modifier = Modifier.testTag("statsTab_1"))
            }
            val s = state
            if (s == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            if (tab == 0) OverviewTab(s, vm, nav) else MonthlyTab(s, vm, nav)
        }
    }
}

// ------------------------------------------------------------------------------------ Overview

@Composable
private fun OverviewTab(s: StatsState, vm: StatisticsViewModel, nav: NavController) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    val o = s.overview
    LazyColumn(
        Modifier.fillMaxSize().testTag("overviewList"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                StatPeriod.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = s.params.period == p,
                        onClick = { vm.setPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, StatPeriod.entries.size),
                        modifier = Modifier.testTag("period_${p.name}"),
                    ) { Text(p.label) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                IconButton(onClick = { vm.shift(-1) }, enabled = s.params.period != StatPeriod.ALL) {
                    Icon(Icons.Rounded.ChevronLeft, "Previous period")
                }
                Text(o.periodLabel, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).testTag("periodLabel"))
                IconButton(onClick = { vm.shift(1) }, enabled = s.params.period != StatPeriod.ALL) {
                    Icon(Icons.Rounded.ChevronRight, "Next period")
                }
            }
        }
        item {
            AppCard {
                Row {
                    StatBlock("Income", money.format(o.summary.income), Modifier.weight(1f), valueColor = fc.income)
                    StatBlock("Expenses", money.format(o.summary.expense), Modifier.weight(1f), valueColor = fc.expense)
                }
                Spacer(Modifier.height(10.dp))
                StatBlock(
                    "Difference (income − expenses)",
                    money.format(o.summary.net),
                    valueColor = if (o.summary.net >= 0) fc.income else fc.expense,
                    valueStyle = MaterialTheme.typography.titleLarge,
                )
            }
        }
        item { SpendingBreakdown(s, nav) }
        item {
            AppCard {
                Text("Income vs expenses", style = MaterialTheme.typography.titleMedium)
                Text("Last 6 months · tap a month for details", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                BarChart(
                    groups = o.incomeVsExpense.map { BarGroup(it.month.format(Dates.shortMonth), listOf(it.summary.income, it.summary.expense)) },
                    series = listOf(BarSeries("Income", fc.income), BarSeries("Expenses", fc.expense)),
                    formatValue = money::format,
                    formatAxis = money::compact,
                    initiallySelected = o.incomeVsExpense.lastIndex,
                    testTag = "incomeExpenseChart",
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(fc.income, "Income")
                    LegendDot(fc.expense, "Expenses")
                }
                Spacer(Modifier.height(10.dp))
                o.incomeVsExpense.reversed().take(3).forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(m.month.format(Dates.monthYear), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            money.signed(m.summary.net, m.summary.net >= 0),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (m.summary.net >= 0) fc.income else fc.expense,
                        )
                    }
                }
            }
        }
        item {
            AppCard {
                Text("Spending trends", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Granularity.entries.forEach { g ->
                        FilterChip(
                            selected = s.params.granularity == g,
                            onClick = { vm.setGranularity(g) },
                            label = { Text(g.label) },
                            modifier = Modifier.testTag("gran_${g.name}"),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                BarChart(
                    groups = o.trend.map { BarGroup(it.label, listOf(it.expense)) },
                    series = listOf(BarSeries("Spent", fc.expense)),
                    formatValue = money::format,
                    formatAxis = money::compact,
                    testTag = "trendChart",
                )
                val total = o.trend.sumOf { it.expense }
                val avg = if (o.trend.isEmpty()) 0L else total / o.trend.size
                Spacer(Modifier.height(8.dp))
                Text(
                    "Average ${money.format(avg)} per ${
                        when (s.params.granularity) {
                            Granularity.DAY -> "day"
                            Granularity.WEEK -> "week"
                            Granularity.MONTH -> "month"
                            Granularity.YEAR -> "year"
                        }
                    } over this range",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            AppCard {
                Text("Savings", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row {
                    StatBlock("Total saved now", money.format(o.totalSaved), Modifier.weight(1f))
                    StatBlock(
                        "Saved this month",
                        money.format(o.insights.monthNet),
                        Modifier.weight(1f),
                        valueColor = if (o.insights.monthNet >= 0) fc.income else fc.expense,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Balance at the end of each month", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LineChart(
                    labels = o.savingsGrowth.map { it.first.format(Dates.shortMonth) },
                    values = o.savingsGrowth.map { it.second },
                    color = MaterialTheme.colorScheme.primary,
                    formatValue = money::format,
                    formatAxis = money::compact,
                    seriesName = "Balance",
                )
                if (o.goals.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Goals", style = MaterialTheme.typography.titleSmall)
                    o.goals.forEach { g ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${money.format(g.saved)} / ${money.format(g.target)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        ProgressBar(g.progress, Color(g.category.color))
                    }
                }
            }
        }
        item { InsightsCard(s) }
    }
}

@Composable
private fun SpendingBreakdown(s: StatsState, nav: NavController) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    val o = s.overview
    var selected by remember(o.spending) { mutableStateOf<Int?>(null) }
    AppCard(Modifier.testTag("spendingBreakdown")) {
        Text("Where your money goes", style = MaterialTheme.typography.titleMedium)
        Text("Tap a slice or a category for details", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (o.spending.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("No expenses in this period.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppCard
        }
        val slices = o.spending.map {
            ChartSlice(placeName(s.snapshot, it.categoryId), it.amount, categoryColor(s.snapshot, it.categoryId, fc.available))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(slices, selected, { selected = it }, size = 220.dp, thickness = 30.dp) {
                val sel = selected?.let { o.spending.getOrNull(it) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
                    Text(
                        if (sel == null) "Total spent" else slices[selected!!].label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        money.format(sel?.amount ?: o.summary.expense),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("donutCenterValue"),
                    )
                    if (sel != null) {
                        Text(pct(sel.share), style = MaterialTheme.typography.bodySmall, color = slices[selected!!].color)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        o.spending.forEachIndexed { i, sp ->
            val isSel = selected == i
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                    .clickable { selected = if (isSel) null else i }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("spendRow_${slices[i].label}"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(s.snapshot.category(sp.categoryId), size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(slices[i].label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${sp.count} transaction${if (sp.count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money.format(sp.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(pct(sp.share), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(sp.share, slices[i].color, height = 6.dp)
                if (isSel) {
                    val txs = o.spendingTx[sp.categoryId].orEmpty()
                    Spacer(Modifier.height(6.dp))
                    txs.take(5).forEach { t ->
                        TransactionRow(t, s.snapshot, showDate = true) { nav.navigate(Routes.add(txId = t.id)) }
                    }
                    if (txs.size > 5) {
                        Text("+ ${txs.size - 5} more", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(s: StatsState) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    val ins = s.overview.insights
    AppCard(Modifier.testTag("insightsCard")) {
        Text("Insights · ${s.overview.insightsMonth.format(Dates.monthYear)}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        InsightRow(Icons.AutoMirrored.Rounded.TrendingUp, fc.expense, "Highest spending category",
            ins.highest?.let { "${placeName(s.snapshot, it.categoryId)} · ${money.format(it.amount)}" } ?: "No category spending yet")
        InsightRow(Icons.AutoMirrored.Rounded.TrendingDown, fc.income, "Lowest spending category",
            ins.lowest?.let { "${placeName(s.snapshot, it.categoryId)} · ${money.format(it.amount)}" } ?: "Needs spending in 2+ categories")
        InsightRow(Icons.Rounded.Today, MaterialTheme.colorScheme.primary, "Average daily spending", money.format(ins.averageDaily))
        InsightRow(Icons.Rounded.Savings, fc.income, "Saved this month (income − expenses)", money.format(ins.monthNet))
        val change = ins.expenseChange
        InsightRow(
            if (change > 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
            if (change > 0) fc.expense else fc.income,
            "Compared with previous month",
            when {
                ins.previousExpense == 0L && ins.monthExpense == 0L -> "No spending in either month"
                change == 0L -> "You spent the same amount"
                else -> "You spent ${money.format(abs(change))} ${if (change > 0) "more" else "less"}" +
                    (ins.expenseChangePct?.let { " (${String.format(Locale.US, "%+.0f", it)}%)" } ?: "")
            },
        )
        InsightRow(
            Icons.Rounded.Warning,
            if (ins.exceeded.isEmpty()) fc.income else fc.expense,
            "Budgets exceeded",
            if (ins.exceeded.isEmpty()) "None — well done"
            else ins.exceeded.joinToString { "${it.category.name} (+${money.format(-it.remaining)})" },
        )
        if (ins.nearLimit.isNotEmpty()) {
            InsightRow(Icons.Rounded.Speed, fc.warning, "Close to the limit", ins.nearLimit.joinToString { it.category.name })
        }
    }
}

@Composable
private fun InsightRow(icon: ImageVector, color: Color, title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

private fun pct(share: Float) = String.format(Locale.US, "%.1f%%", share * 100)

// ------------------------------------------------------------------------------------ Monthly

@Composable
private fun MonthlyTab(s: StatsState, vm: StatisticsViewModel, nav: NavController) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    val m = s.monthly
    val r = m.report
    var picker by remember { mutableStateOf(false) }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.exportPdf(uri, r.month)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportMonthCsv(uri, r.month)
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("monthlyList"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MonthSwitcher(r.month, vm::setMonth, onTitleClick = { picker = true }, modifier = Modifier.testTag("monthSwitcher"))
        }
        item {
            AppCard(Modifier.testTag("monthSummary")) {
                Row {
                    StatBlock("Income", money.format(r.summary.income), Modifier.weight(1f), tag = "monthIncome", valueColor = fc.income)
                    StatBlock("Expenses", money.format(r.summary.expense), Modifier.weight(1f), tag = "monthExpense", valueColor = fc.expense)
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    StatBlock("Net savings", money.format(r.summary.net), Modifier.weight(1f), tag = "monthNet",
                        valueColor = if (r.summary.net >= 0) fc.income else fc.expense)
                    StatBlock("Transactions", r.summary.count.toString(), Modifier.weight(1f), tag = "monthCount")
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("vs ${r.month.minusMonths(1).format(Dates.monthYear)}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                CompareRow("Income", r.summary.income, r.previous.income, higherIsGood = true)
                CompareRow("Expenses", r.summary.expense, r.previous.expense, higherIsGood = false)
                CompareRow("Net savings", r.summary.net, r.previous.net, higherIsGood = true)
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row {
                    StatBlock("Balance at month end", money.format(r.totalAtEnd), Modifier.weight(1f),
                        valueStyle = MaterialTheme.typography.bodyLarge)
                    StatBlock("Unallocated at month end", money.format(r.availableAtEnd), Modifier.weight(1f),
                        valueStyle = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            AppCard {
                Text("Daily spending", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                BarChart(
                    groups = r.dailyExpense.mapIndexed { i, v -> BarGroup((i + 1).toString(), listOf(v)) },
                    series = listOf(BarSeries("Spent", fc.expense)),
                    formatValue = money::format,
                    formatAxis = money::compact,
                    testTag = "dailyChart",
                )
                val incomeDays = r.dailyIncome.count { it > 0 }
                Text(
                    "Money received on $incomeDays day${if (incomeDays == 1) "" else "s"} this month \u00B7 tap a bar to see that day's spending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { MonthCategoryTable(s) }
        item {
            AppCard {
                Text("Last 6 months", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                BarChart(
                    groups = m.history.map { BarGroup(it.month.format(Dates.shortMonth), listOf(it.summary.income, it.summary.expense)) },
                    series = listOf(BarSeries("Income", fc.income), BarSeries("Expenses", fc.expense)),
                    formatValue = money::format,
                    formatAxis = money::compact,
                    initiallySelected = m.history.lastIndex,
                    testTag = "historyChart",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(fc.income, "Income")
                    LegendDot(fc.expense, "Expenses")
                }
            }
        }
        item {
            AppCard {
                Text("Category spending across months", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = m.compareCategory == null,
                        onClick = { vm.setCompare(null) },
                        label = { Text("Available") },
                    )
                    s.snapshot.activeCategories.forEach { c ->
                        FilterChip(
                            selected = m.compareCategory == c.id,
                            onClick = { vm.setCompare(c.id) },
                            label = { Text(c.name) },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.color))) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val color = categoryColor(s.snapshot, m.compareCategory, fc.available)
                BarChart(
                    groups = m.categorySeries.map { BarGroup(it.first.format(Dates.shortMonth), listOf(it.second)) },
                    series = listOf(BarSeries(placeName(s.snapshot, m.compareCategory), color)),
                    formatValue = money::format,
                    formatAxis = money::compact,
                    initiallySelected = m.categorySeries.lastIndex,
                    testTag = "categoryHistoryChart",
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { pdfLauncher.launch("MoneyTracker-${r.month}.pdf") },
                    modifier = Modifier.weight(1f).testTag("exportPdf"),
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, null)
                    Spacer(Modifier.width(6.dp))
                    Text("PDF report")
                }
                OutlinedButton(
                    onClick = { csvLauncher.launch("MoneyTracker-${r.month}.csv") },
                    modifier = Modifier.weight(1f).testTag("exportMonthCsv"),
                ) {
                    Icon(Icons.Rounded.TableChart, null)
                    Spacer(Modifier.width(6.dp))
                    Text("CSV")
                }
            }
        }
    }
    if (picker) {
        MonthPickerDialog(r.month, onDismiss = { picker = false }) {
            picker = false
            vm.setMonth(it)
        }
    }
}

@Composable
private fun CompareRow(label: String, now: Long, before: Long, higherIsGood: Boolean) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    val diff = now - before
    val good = if (higherIsGood) diff >= 0 else diff <= 0
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(money.format(before), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(
            if (diff == 0L) "no change" else money.signed(diff, diff > 0),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (diff == 0L) MaterialTheme.colorScheme.onSurfaceVariant else if (good) fc.income else fc.expense,
        )
    }
}

@Composable
private fun MonthCategoryTable(s: StatsState) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    val r = s.monthly.report
    AppCard(Modifier.testTag("monthCategories")) {
        Text("Categories this month", style = MaterialTheme.typography.titleMedium)
        Text("Added = allocated or received during the month. Balance as it stood at month end.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Category", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1.4f))
            Text("Added", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("Spent", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("Balance", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
        r.rows.forEach { row ->
            val name = placeName(s.snapshot, row.categoryId)
            Column(Modifier.padding(vertical = 6.dp).testTag("monthRow_$name")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1.4f), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(s.snapshot.category(row.categoryId), size = 24.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(money.number(row.added), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(money.number(row.spent), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End, color = if (row.spent > 0) fc.expense else MaterialTheme.colorScheme.onSurface)
                    Text(money.number(row.balanceEnd), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                }
                row.limit?.let { limit ->
                    Spacer(Modifier.height(4.dp))
                    val ratio = row.spent.toFloat() / limit
                    ProgressBar(ratio, if (ratio > 1f) fc.expense else if (ratio >= 0.8f) fc.warning else MaterialTheme.colorScheme.primary, height = 5.dp)
                    Text(
                        "Budget ${money.format(limit)} · ${(ratio * 100).toInt()}% used",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ratio > 1f) fc.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text("Amounts in ${money.currency}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
