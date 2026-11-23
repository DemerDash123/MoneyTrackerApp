package com.moneytracker.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.moneytracker.app.domain.Balances
import com.moneytracker.app.domain.BudgetState
import com.moneytracker.app.domain.BudgetStatus
import com.moneytracker.app.domain.CategorySpend
import com.moneytracker.app.domain.PeriodSummary
import com.moneytracker.app.domain.Reports
import com.moneytracker.app.ui.AppViewModelProvider
import com.moneytracker.app.i18n.S
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.moneytracker.app.ui.LocalMoney
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.*
import com.moneytracker.app.ui.theme.AmountStyle
import com.moneytracker.app.ui.theme.FinanceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class HomeState(
    val snapshot: FinanceSnapshot,
    val balances: Balances,
    val lifetime: PeriodSummary,
    val month: PeriodSummary,
    val monthSpending: List<CategorySpend>,
    val budgets: List<BudgetStatus>,
    val recent: List<MoneyTransaction>,
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<HomeState?> = container.finance.snapshot
        .map { s ->
            val ym = YearMonth.now()
            val monthRange = Dates.monthRange(ym)
            HomeState(
                snapshot = s,
                balances = s.balances,
                lifetime = Reports.summary(s.transactions, TimeRange.ALL),
                month = Reports.summary(s.transactions, monthRange),
                monthSpending = Reports.spendingByCategory(s.transactions, monthRange),
                budgets = Reports.budgets(s.transactions, s.categories, ym),
                recent = s.transactions.take(6),
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HomeScreen(nav: NavController, vm: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> S.goodMorning
        in 12..16 -> S.goodAfternoon
        else -> S.goodEvening
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(greeting, style = MaterialTheme.typography.titleLarge)
                        Text(
                            LocalDate.now().format(Dates.fullDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.CALENDAR) }) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = S.calendar)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("homeList"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { BalanceHero(s.balances) { nav.navigate(Routes.CURRENT) } }
            item { QuickActions(nav) }
            item { MonthCard(s, onOpen = { nav.navigate(Routes.stats(1)) }) }
            val alerts = s.budgets.filter { it.state != BudgetState.OK }
            if (alerts.isNotEmpty()) {
                item { BudgetAlerts(alerts) { nav.navigate(Routes.category(it)) } }
            }
            item { AllocationCard(s, onOpen = { nav.navigate(Routes.MONEY) }) }
            item { SpendingCard(s, onOpen = { nav.navigate(Routes.stats(0)) }) }
            item {
                SectionHeader(S.recentTransactions, action = S.seeAll, onAction = { nav.navigate(Routes.TRANSACTIONS) })
            }
            if (s.recent.isEmpty()) {
                item {
                    AppCard {
                        EmptyState(
                            Icons.Rounded.Receipt,
                            S.noTransactionsYet,
                            S.addFirstIncome,
                            actionLabel = S.addIncome,
                            onAction = { nav.navigate(Routes.add(TxType.INCOME)) },
                        )
                    }
                }
            } else {
                item {
                    AppCard(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        s.recent.forEach { t ->
                            TransactionRow(t, s.snapshot, showDate = true) { nav.navigate(Routes.add(txId = t.id)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHero(b: Balances, onClick: () -> Unit) {
    val money = LocalMoney.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(FinanceTheme.colors.heroBrush)
            .clickable(onClick = onClick)
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.totalBalance, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.8f))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                money.format(b.total),
                color = Color.White,
                style = AmountStyle.copy(fontSize = 34.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("totalBalance"),
            )
            Spacer(Modifier.height(18.dp))
            Row {
                StatBlock(
                    S.available,
                    money.format(b.available),
                    Modifier.weight(1f),
                    tag = "availableBalance",
                    valueColor = Color.White,
                    labelColor = Color.White.copy(alpha = 0.75f),
                )
                StatBlock(
                    S.allocated,
                    money.format(b.allocated),
                    Modifier.weight(1f),
                    tag = "allocatedBalance",
                    valueColor = Color.White,
                    labelColor = Color.White.copy(alpha = 0.75f),
                )
            }
            if (b.total > 0) {
                Spacer(Modifier.height(14.dp))
                SegmentedBar(
                    listOf(
                        b.allocated.toFloat() to Color.White,
                        b.available.toFloat() to Color.White.copy(alpha = 0.35f),
                    ),
                    height = 8.dp,
                )
            }
        }
    }
}

@Composable
private fun QuickActions(nav: NavController) {
    val fc = FinanceTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickAction(S.income, Icons.Rounded.ArrowDownward, fc.income, Modifier.weight(1f).testTag("quickIncome")) {
            nav.navigate(Routes.add(TxType.INCOME))
        }
        QuickAction(S.expense, Icons.Rounded.ArrowUpward, fc.expense, Modifier.weight(1f).testTag("quickExpense")) {
            nav.navigate(Routes.add(TxType.EXPENSE))
        }
        QuickAction(S.transfer, Icons.Rounded.SwapHoriz, fc.transfer, Modifier.weight(1f).testTag("quickTransfer")) {
            nav.navigate(Routes.add(TxType.TRANSFER))
        }
        QuickAction(S.allocate, Icons.Rounded.Savings, MaterialTheme.colorScheme.primary, Modifier.weight(1f).testTag("quickAllocate")) {
            nav.navigate(Routes.add(TxType.TRANSFER, from = Routes.AVAILABLE))
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, color, size = 40.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun MonthCard(s: HomeState, onOpen: () -> Unit) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    AppCard(onClick = onOpen, modifier = Modifier.testTag("monthCard")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(S.thisMonth, style = MaterialTheme.typography.titleMedium)
                Text(
                    YearMonth.now().format(Dates.monthYear),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(S.report, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(14.dp))
        Row {
            StatBlock(S.income, money.format(s.month.income), Modifier.weight(1f), valueColor = fc.income)
            StatBlock(S.expenses, money.format(s.month.expense), Modifier.weight(1f), valueColor = fc.expense)
        }
        Spacer(Modifier.height(12.dp))
        StatBlock(
            S.remainingThisMonth,
            money.format(s.month.net),
            valueColor = if (s.month.net >= 0) MaterialTheme.colorScheme.onSurface else fc.expense,
            valueStyle = MaterialTheme.typography.titleLarge,
        )
        if (s.month.income > 0) {
            Spacer(Modifier.height(10.dp))
            val used = (s.month.expense.toFloat() / s.month.income).coerceIn(0f, 1f)
            ProgressBar(used, if (used > 0.9f) fc.expense else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                S.incomeSpentPct((used * 100).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row {
            StatBlock(S.totalIncomeAllTime, money.format(s.lifetime.income), Modifier.weight(1f),
                valueStyle = MaterialTheme.typography.bodyLarge)
            StatBlock(S.totalExpensesAllTime, money.format(s.lifetime.expense), Modifier.weight(1f),
                valueStyle = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun BudgetAlerts(alerts: List<BudgetStatus>, onOpen: (Long) -> Unit) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = fc.warning)
            Spacer(Modifier.width(8.dp))
            Text(S.budgetAlerts, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        alerts.forEach { b ->
            val over = b.state == BudgetState.EXCEEDED
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpen(b.category.id) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(b.category, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        if (over) S.overBudgetBy(money.format(-b.remaining))
                        else S.usedLeft((b.ratio * 100).toInt(), money.format(b.remaining)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) fc.expense else fc.warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllocationCard(s: HomeState, onOpen: () -> Unit) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    val cats = s.snapshot.activeCategories
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.whereMoneyIs, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        if (cats.isEmpty()) {
            Text(
                S.createCategoriesHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }
        val segs = cats.map { s.balances.of(it.id).toFloat() to Color(it.color) } +
            listOf(s.balances.available.toFloat() to fc.available.copy(alpha = 0.5f))
        SegmentedBar(segs, height = 14.dp)
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(cats, key = { it.id }) { c ->
                Column(
                    Modifier
                        .width(132.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                ) {
                    CategoryBadge(c, size = 34.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        money.format(s.balances.of(c.id)),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                Column(
                    Modifier
                        .width(132.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                ) {
                    CategoryBadge(null, size = 34.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(S.available, style = MaterialTheme.typography.bodyMedium)
                    Text(money.format(s.balances.available), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SpendingCard(s: HomeState, onOpen: () -> Unit) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    var selected by remember { mutableStateOf<Int?>(null) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.spendingThisMonth, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpen) { Text(S.navStats) }
        }
        if (s.monthSpending.isEmpty()) {
            Text(
                S.noSpendingThisMonth,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }
        val slices = s.monthSpending.map { sp ->
            ChartSlice(
                placeName(s.snapshot, sp.categoryId),
                sp.amount,
                categoryColor(s.snapshot, sp.categoryId, fc.available),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(slices, selected, { selected = it }, size = 132.dp, thickness = 18.dp) {
                val sel = selected?.let { slices.getOrNull(it) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        sel?.label ?: S.spent,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        money.compact(sel?.value ?: s.month.expense),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.monthSpending.take(5).forEachIndexed { i, sp ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selected = if (selected == i) null else i },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(slices[i].color))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            slices[i].label,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${(sp.share * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
