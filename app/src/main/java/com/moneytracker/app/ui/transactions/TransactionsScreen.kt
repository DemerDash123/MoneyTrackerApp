package com.moneytracker.app.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.moneytracker.app.domain.FinanceException
import com.moneytracker.app.domain.Ledger
import com.moneytracker.app.domain.PeriodSummary
import com.moneytracker.app.domain.Reports
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

enum class PeriodFilter(val label: String) {
    ALL("All time"), TODAY("Today"), WEEK("This week"), MONTH("This month"),
    LAST_MONTH("Last month"), YEAR("This year"), CUSTOM("Custom range"),
}

sealed interface CategoryFilter {
    data object All : CategoryFilter
    data object Available : CategoryFilter
    data class One(val id: Long) : CategoryFilter
}

data class TxFilters(
    val query: String = "",
    val type: TxType? = null,
    val category: CategoryFilter = CategoryFilter.All,
    val period: PeriodFilter = PeriodFilter.ALL,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
)

sealed interface TxListItem {
    data class Header(val date: LocalDate, val income: Long, val expense: Long) : TxListItem
    data class Row(val tx: MoneyTransaction) : TxListItem
}

data class TransactionsState(
    val snapshot: FinanceSnapshot,
    val items: List<TxListItem>,
    val summary: PeriodSummary,
    val total: Int,
)

class TransactionsViewModel(private val container: AppContainer) : ViewModel() {
    val filters = MutableStateFlow(TxFilters())
    val message = MutableStateFlow<String?>(null)

    val state: StateFlow<TransactionsState?> = combine(container.finance.snapshot, filters) { s, f ->
        val range = rangeFor(f)
        val q = f.query.trim().lowercase()
        val filtered = s.transactions.filter { t ->
            t.timestamp in range &&
                (f.type == null || t.type == f.type) &&
                when (val c = f.category) {
                    CategoryFilter.All -> true
                    CategoryFilter.Available -> Ledger.touches(t, null)
                    is CategoryFilter.One -> Ledger.touches(t, c.id)
                } &&
                (q.isEmpty() || matches(t, s, q))
        }
        val items = ArrayList<TxListItem>(filtered.size + 16)
        filtered.groupBy { Dates.localDate(it.timestamp) }.forEach { (date, list) ->
            items += TxListItem.Header(
                date,
                list.filter { it.type == TxType.INCOME }.sumOf { it.amount },
                list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
            )
            list.forEach { items += TxListItem.Row(it) }
        }
        TransactionsState(s, items, Reports.summary(filtered, TimeRange.ALL), filtered.size)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun matches(t: MoneyTransaction, s: FinanceSnapshot, q: String): Boolean {
        if (t.description.lowercase().contains(q) || t.note.lowercase().contains(q)) return true
        if (placeName(s, t.categoryId).lowercase().contains(q)) return true
        if (t.type == TxType.TRANSFER && placeName(s, t.toCategoryId).lowercase().contains(q)) return true
        if (t.type.name.lowercase().contains(q)) return true
        val fmt = container.settings.settings.value.formatter
        return fmt.number(t.amount).replace(",", "").contains(q.replace(",", ""))
    }

    private fun rangeFor(f: TxFilters): TimeRange {
        val today = LocalDate.now()
        return when (f.period) {
            PeriodFilter.ALL -> TimeRange.ALL
            PeriodFilter.TODAY -> Dates.dayRange(today)
            PeriodFilter.WEEK -> Dates.rangeOf(Dates.weekStart(today), Dates.weekStart(today).plusDays(6))
            PeriodFilter.MONTH -> Dates.monthRange(YearMonth.now())
            PeriodFilter.LAST_MONTH -> Dates.monthRange(YearMonth.now().minusMonths(1))
            PeriodFilter.YEAR -> Dates.yearRange(today.year)
            PeriodFilter.CUSTOM -> {
                val a = f.customStart ?: return TimeRange.ALL
                val b = f.customEnd ?: a
                Dates.rangeOf(a, b)
            }
        }
    }

    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun setType(t: TxType?) = filters.update { it.copy(type = t) }
    fun setCategory(c: CategoryFilter) = filters.update { it.copy(category = c) }
    fun setPeriod(p: PeriodFilter) = filters.update { it.copy(period = p) }
    fun setCustom(a: LocalDate, b: LocalDate) =
        filters.update { it.copy(period = PeriodFilter.CUSTOM, customStart = a, customEnd = b) }
    fun clear() = filters.update { TxFilters() }

    fun delete(id: Long) {
        viewModelScope.launch {
            message.value = try {
                container.finance.deleteTransaction(id)
                "Transaction deleted"
            } catch (e: FinanceException) {
                e.message
            }
        }
    }
}

@Composable
fun TransactionsScreen(nav: NavController, vm: TransactionsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var detail by remember { mutableStateOf<MoneyTransaction?>(null) }
    var showRange by remember { mutableStateOf(false) }

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
                title = { Text("Transactions") },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.CALENDAR) }, modifier = Modifier.testTag("openCalendar")) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = "Calendar view")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.add(TxType.EXPENSE)) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add") },
                modifier = Modifier.testTag("addTransactionFab"),
            )
        },
    ) { padding ->
        val s = state
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filters.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("searchField"),
                placeholder = { Text("Search description, category, amount…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (filters.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Clear, "Clear search") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            FilterBar(filters, s?.snapshot, vm, onCustom = { showRange = true })
            if (s == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            SummaryStrip(s)
            if (s.items.isEmpty()) {
                EmptyState(
                    Icons.Rounded.SearchOff,
                    if (s.snapshot.transactions.isEmpty()) "No transactions yet" else "Nothing matches",
                    if (s.snapshot.transactions.isEmpty()) "Tap Add to record income, an expense or a transfer."
                    else "Try another search or clear the filters.",
                    actionLabel = if (s.snapshot.transactions.isEmpty()) null else "Clear filters",
                    onAction = if (s.snapshot.transactions.isEmpty()) null else vm::clear,
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("transactionList"),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                ) {
                    items(s.items, key = { item ->
                        when (item) {
                            is TxListItem.Header -> "h_${item.date}"
                            is TxListItem.Row -> "t_${item.tx.id}"
                        }
                    }) { item ->
                        when (item) {
                            is TxListItem.Header -> DayHeader(item)
                            is TxListItem.Row -> TransactionRow(
                                item.tx, s.snapshot,
                                modifier = Modifier.animateItem().testTag("txRow_${item.tx.id}"),
                            ) { detail = item.tx }
                        }
                    }
                }
            }
        }
    }

    detail?.let { t ->
        val snap = state?.snapshot
        if (snap != null) {
            TransactionDetailSheet(
                t, snap,
                onDismiss = { detail = null },
                onEdit = {
                    detail = null
                    nav.navigate(Routes.add(txId = t.id))
                },
                onDelete = {
                    detail = null
                    vm.delete(t.id)
                },
            )
        }
    }

    if (showRange) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val a = rangeState.selectedStartDateMillis
                    val b = rangeState.selectedEndDateMillis
                    if (a != null) {
                        vm.setCustom(
                            Instant.ofEpochMilli(a).atZone(ZoneOffset.UTC).toLocalDate(),
                            Instant.ofEpochMilli(b ?: a).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                    showRange = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(rangeState, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FilterBar(f: TxFilters, snapshot: FinanceSnapshot?, vm: TransactionsViewModel, onCustom: () -> Unit) {
    var periodMenu by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            FilterChip(
                selected = f.period != PeriodFilter.ALL,
                onClick = { periodMenu = true },
                label = {
                    Text(
                        if (f.period == PeriodFilter.CUSTOM && f.customStart != null)
                            "${f.customStart.format(Dates.shortDate)} – ${(f.customEnd ?: f.customStart).format(Dates.shortDate)}"
                        else f.period.label
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
                modifier = Modifier.testTag("periodFilter"),
            )
            DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                PeriodFilter.entries.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.label) },
                        onClick = {
                            periodMenu = false
                            if (p == PeriodFilter.CUSTOM) onCustom() else vm.setPeriod(p)
                        },
                    )
                }
            }
        }
        listOf<Pair<TxType?, String>>(null to "All", TxType.INCOME to "Income", TxType.EXPENSE to "Expenses", TxType.TRANSFER to "Transfers")
            .forEach { (t, label) ->
                FilterChip(
                    selected = f.type == t,
                    onClick = { vm.setType(t) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("typeFilter_${t?.name ?: "ALL"}"),
                )
            }
        Box {
            val catLabel = when (val c = f.category) {
                CategoryFilter.All -> "All categories"
                CategoryFilter.Available -> "Available"
                is CategoryFilter.One -> snapshot?.let { placeName(it, c.id) } ?: "Category"
            }
            FilterChip(
                selected = f.category != CategoryFilter.All,
                onClick = { catMenu = true },
                label = { Text(catLabel) },
                leadingIcon = { Icon(Icons.Rounded.Category, null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
                modifier = Modifier.testTag("categoryFilter"),
            )
            DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                DropdownMenuItem(text = { Text("All categories") }, onClick = { catMenu = false; vm.setCategory(CategoryFilter.All) })
                DropdownMenuItem(text = { Text("Available") }, onClick = { catMenu = false; vm.setCategory(CategoryFilter.Available) })
                snapshot?.categories?.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(if (c.archived) "${c.name} (deleted)" else c.name) },
                        leadingIcon = { Icon(CategoryIcons.get(c.icon), null, tint = Color(c.color)) },
                        onClick = { catMenu = false; vm.setCategory(CategoryFilter.One(c.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStrip(s: TransactionsState) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatBlock("${s.total} transactions", "", Modifier.weight(1f), valueStyle = MaterialTheme.typography.bodySmall)
        StatBlock("In", money.format(s.summary.income), valueColor = fc.income, valueStyle = MaterialTheme.typography.bodyMedium)
        StatBlock("Out", money.format(s.summary.expense), valueColor = fc.expense, valueStyle = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DayHeader(h: TxListItem.Header) {
    val money = LocalMoney.current
    val today = LocalDate.now()
    val label = when (h.date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> h.date.format(Dates.dayHeader)
    }
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        val net = h.income - h.expense
        if (h.income != 0L || h.expense != 0L) {
            Text(
                money.signed(net, net >= 0),
                style = MaterialTheme.typography.labelLarge,
                color = if (net >= 0) FinanceTheme.colors.income else FinanceTheme.colors.expense,
            )
        }
    }
}

@Composable
fun TransactionDetailSheet(
    t: MoneyTransaction,
    snapshot: FinanceSnapshot,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val money = LocalMoney.current
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(typeIcon(t.type), typeColor(t.type), size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(transactionTitle(t, snapshot), style = MaterialTheme.typography.titleMedium)
                    Text(
                        t.type.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = typeColor(t.type),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                when (t.type) {
                    TxType.INCOME -> money.signed(t.amount, true)
                    TxType.EXPENSE -> money.signed(t.amount, false)
                    TxType.TRANSFER -> money.format(t.amount)
                },
                style = MaterialTheme.typography.headlineMedium,
                color = typeColor(t.type),
                modifier = Modifier.testTag("detailAmount"),
            )
            Spacer(Modifier.height(16.dp))
            val dt = Dates.localDateTime(t.timestamp)
            DetailRow("Date", "${dt.toLocalDate().format(Dates.fullDate)} at ${dt.toLocalTime().format(Dates.time)}")
            when (t.type) {
                TxType.INCOME -> DetailRow("Added to", placeName(snapshot, t.categoryId))
                TxType.EXPENSE -> DetailRow("Paid from", placeName(snapshot, t.categoryId))
                TxType.TRANSFER -> {
                    DetailRow("From", placeName(snapshot, t.categoryId))
                    DetailRow("To", placeName(snapshot, t.toCategoryId))
                }
            }
            if (t.description.isNotBlank()) DetailRow("Description", t.description)
            if (t.note.isNotBlank()) DetailRow("Notes", t.note)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { confirm = true },
                    modifier = Modifier.weight(1f).testTag("detailDelete"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.Delete, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
                Button(onClick = onEdit, modifier = Modifier.weight(1f).testTag("detailEdit")) {
                    Icon(Icons.Rounded.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            "Delete transaction?",
            "Its effect on your balances will be reversed. This can't be undone.",
            "Delete",
            onConfirm = {
                confirm = false
                onDelete()
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
