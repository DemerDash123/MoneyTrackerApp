package com.moneytracker.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.moneytracker.app.AppContainer
import com.moneytracker.app.core.Dates
import com.moneytracker.app.data.FinanceSnapshot
import com.moneytracker.app.data.db.MoneyTransaction
import com.moneytracker.app.domain.DayActivity
import com.moneytracker.app.domain.PeriodSummary
import com.moneytracker.app.domain.Reports
import com.moneytracker.app.ui.AppViewModelProvider
import com.moneytracker.app.i18n.S
import com.moneytracker.app.i18n.I18n
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class CalendarState(
    val snapshot: FinanceSnapshot,
    val month: YearMonth,
    val days: Map<LocalDate, DayActivity>,
    val monthSummary: PeriodSummary,
    val selected: LocalDate,
    val dayTransactions: List<MoneyTransaction>,
)

class CalendarViewModel(container: AppContainer) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val selected = MutableStateFlow(LocalDate.now())

    val state: StateFlow<CalendarState?> = combine(container.finance.snapshot, month, selected) { s, ym, day ->
        val dayRange = Dates.dayRange(day)
        CalendarState(
            snapshot = s,
            month = ym,
            days = Reports.dailyActivity(s.transactions, ym),
            monthSummary = Reports.summary(s.transactions, Dates.monthRange(ym)),
            selected = day,
            dayTransactions = s.transactions.filter { it.timestamp in dayRange },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMonth(ym: YearMonth) {
        month.value = ym
        selected.value = if (ym == YearMonth.now()) LocalDate.now() else ym.atDay(1)
    }

    fun select(d: LocalDate) {
        selected.value = d
    }
}

@Composable
fun CalendarScreen(nav: NavController, vm: CalendarViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    var picker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S.calendar) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = S.back)
                    }
                },
                actions = {
                    TextButton(onClick = { vm.setMonth(YearMonth.now()) }) { Text(S.today) }
                },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("calendarList"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        ) {
            item {
                MonthSwitcher(s.month, vm::setMonth, onTitleClick = { picker = true })
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    StatBlock(S.income, money.format(s.monthSummary.income), Modifier.weight(1f), valueColor = fc.income,
                        valueStyle = MaterialTheme.typography.bodyLarge)
                    StatBlock(S.expenses, money.format(s.monthSummary.expense), Modifier.weight(1f), valueColor = fc.expense,
                        valueStyle = MaterialTheme.typography.bodyLarge)
                    StatBlock(S.net, money.format(s.monthSummary.net), Modifier.weight(1f),
                        valueStyle = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
                MonthGrid(s, onSelect = vm::select)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                    LegendDot(fc.income, S.moneyInLegend)
                    LegendDot(fc.expense, S.moneyOutLegend)
                    LegendDot(fc.transfer, S.transfer)
                }
            }
            item {
                val act = s.days[s.selected]
                AppCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(s.selected.format(Dates.fullDate), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("selectedDay"))
                    Spacer(Modifier.height(8.dp))
                    Row {
                        StatBlock(S.received, money.format(act?.income ?: 0), Modifier.weight(1f), valueColor = fc.income)
                        StatBlock(S.spent, money.format(act?.expense ?: 0), Modifier.weight(1f), valueColor = fc.expense)
                        StatBlock(S.transactions, (act?.count ?: 0).toString(), Modifier.weight(1f))
                    }
                }
            }
            if (s.dayTransactions.isEmpty()) {
                item {
                    Text(
                        S.noTxOnDay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(s.dayTransactions, key = { it.id }) { t ->
                TransactionRow(t, s.snapshot, modifier = Modifier.padding(horizontal = 6.dp)) { nav.navigate(Routes.add(txId = t.id)) }
            }
        }
    }
    if (picker) {
        MonthPickerDialog(state?.month ?: YearMonth.now(), onDismiss = { picker = false }) {
            picker = false
            vm.setMonth(it)
        }
    }
}

@Composable
private fun MonthGrid(s: CalendarState, onSelect: (LocalDate) -> Unit) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    val first = Dates.firstDayOfWeek()
    val headers = (0 until 7).map { first.plus(it.toLong()) }
    val start = s.month.atDay(1)
    val offset = ((start.dayOfWeek.value - first.value) + 7) % 7
    val cells = offset + s.month.lengthOfMonth()
    val rows = (cells + 6) / 7
    val today = LocalDate.now()

    Column(Modifier.fillMaxWidth().testTag("calendarGrid")) {
        Row(Modifier.fillMaxWidth()) {
            headers.forEach {
                Text(
                    it.getDisplayName(if (I18n.isRtl) TextStyle.SHORT_STANDALONE else TextStyle.SHORT, I18n.locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - offset
                    if (idx < 0 || idx >= s.month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).height(58.dp))
                    } else {
                        val date = s.month.atDay(idx + 1)
                        val act = s.days[date]
                        val sel = date == s.selected
                        val isToday = date == today
                        Column(
                            Modifier
                                .weight(1f)
                                .height(58.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        sel -> MaterialTheme.colorScheme.primaryContainer
                                        act != null -> MaterialTheme.colorScheme.surfaceContainer
                                        else -> MaterialTheme.colorScheme.background
                                    }
                                )
                                .then(
                                    if (isToday && !sel) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                                .clickable { onSelect(date) }
                                .testTag("day_${date}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                (idx + 1).toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (sel || isToday) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (act != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    if (act.income > 0) Box(Modifier.size(6.dp).clip(CircleShape).background(fc.income))
                                    if (act.expense > 0) Box(Modifier.size(6.dp).clip(CircleShape).background(fc.expense))
                                    if (act.transfers > 0) Box(Modifier.size(6.dp).clip(CircleShape).background(fc.transfer))
                                }
                                val net = act.income - act.expense
                                if (net != 0L) {
                                    Text(
                                        money.compact(net),
                                        fontSize = 9.sp,
                                        // keep "-12" as one left-to-right number in Arabic
                                        style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                                        maxLines = 1,
                                        color = if (net > 0) fc.income else fc.expense,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
