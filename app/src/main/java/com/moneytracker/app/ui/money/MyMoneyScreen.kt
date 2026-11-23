package com.moneytracker.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.moneytracker.app.AppContainer
import com.moneytracker.app.core.AmountParse
import com.moneytracker.app.core.Dates
import com.moneytracker.app.data.CategoryInput
import com.moneytracker.app.data.FinanceSnapshot
import com.moneytracker.app.data.db.Category
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.domain.Balances
import com.moneytracker.app.domain.BudgetState
import com.moneytracker.app.domain.BudgetStatus
import com.moneytracker.app.domain.FinanceException
import com.moneytracker.app.domain.GoalStatus
import com.moneytracker.app.domain.Ledger
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class FundItem(
    val category: Category,
    val balance: Long,
    val spentThisMonth: Long,
    val totalAdded: Long,
    val totalSpent: Long,
)

data class MyMoneyState(
    val snapshot: FinanceSnapshot,
    val balances: Balances,
    val funds: List<FundItem>,
    val goals: List<GoalStatus>,
    val budgets: List<BudgetStatus>,
)

class MyMoneyViewModel(private val container: AppContainer) : ViewModel() {
    val message = MutableStateFlow<String?>(null)

    val state: StateFlow<MyMoneyState?> = container.finance.snapshot.map { s ->
        val ym = YearMonth.now()
        val month = Dates.monthRange(ym)
        MyMoneyState(
            snapshot = s,
            balances = s.balances,
            funds = s.activeCategories.map { c ->
                val all = Ledger.categoryStats(c.id, s.transactions)
                FundItem(
                    category = c,
                    balance = s.balances.of(c.id),
                    spentThisMonth = Ledger.categoryStats(c.id, s.transactions, month).spent,
                    totalAdded = all.totalAdded,
                    totalSpent = all.spent,
                )
            },
            goals = Reports.goals(s.categories, s.balances, LocalDate.now()),
            budgets = Reports.budgets(s.transactions, s.categories, ym),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createSuggested(s: CategoryIcons.Suggestion) {
        viewModelScope.launch {
            message.value = try {
                container.finance.createCategory(CategoryInput(s.name, s.icon, s.color))
                "“${s.name}” created"
            } catch (e: FinanceException) {
                e.message
            }
        }
    }

    /** Sets or clears (null) the monthly budget of a category. */
    fun setBudget(category: Category, limit: Long?) {
        viewModelScope.launch {
            message.value = try {
                container.finance.updateCategory(
                    category.id,
                    CategoryInput(
                        name = category.name,
                        icon = category.icon,
                        color = category.color,
                        monthlyLimit = limit,
                        targetAmount = category.targetAmount,
                        targetDate = category.targetDate?.let { LocalDate.ofEpochDay(it) },
                    ),
                )
                if (limit == null) "Budget removed" else "Budget saved"
            } catch (e: FinanceException) {
                e.message
            }
        }
    }
}

@Composable
fun MyMoneyScreen(nav: NavController, vm: MyMoneyViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var budgetFor by remember { mutableStateOf<Category?>(null) }
    var pickBudgetCategory by remember { mutableStateOf(false) }

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
                title = { Text("My Money") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            when (tab) {
                0 -> ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Routes.categoryEdit()) },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("New category") },
                    modifier = Modifier.testTag("newCategoryFab"),
                )
                1 -> ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Routes.categoryEdit(goal = true)) },
                    icon = { Icon(Icons.Rounded.Flag, null) },
                    text = { Text("New goal") },
                    modifier = Modifier.testTag("newGoalFab"),
                )
                else -> ExtendedFloatingActionButton(
                    onClick = { pickBudgetCategory = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Set budget") },
                    modifier = Modifier.testTag("newBudgetFab"),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Categories", "Goals", "Budgets").forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(label) },
                        modifier = Modifier.testTag("moneyTab_$i"),
                    )
                }
            }
            val s = state
            if (s == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            when (tab) {
                0 -> FundsTab(s, nav, vm)
                1 -> GoalsTab(s, nav)
                else -> BudgetsTab(s, nav) { budgetFor = it }
            }
        }
    }

    if (pickBudgetCategory) {
        val cats = state?.snapshot?.activeCategories.orEmpty()
        AlertDialog(
            onDismissRequest = { pickBudgetCategory = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickBudgetCategory = false }) { Text("Cancel") } },
            title = { Text("Budget for which category?") },
            text = {
                if (cats.isEmpty()) {
                    Text("Create a category first.")
                } else {
                    Column {
                        cats.forEach { c ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        pickBudgetCategory = false
                                        budgetFor = c
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryBadge(c, size = 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(c.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
        )
    }

    budgetFor?.let { c ->
        BudgetDialog(
            category = c,
            onDismiss = { budgetFor = null },
            onSave = { limit ->
                budgetFor = null
                vm.setBudget(c, limit)
            },
        )
    }
}

@Composable
private fun FundsTab(s: MyMoneyState, nav: NavController, vm: MyMoneyViewModel) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().testTag("fundsList"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppCard {
                Row {
                    StatBlock("Total", money.format(s.balances.total), Modifier.weight(1f))
                    StatBlock("Allocated", money.format(s.balances.allocated), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                val segs = s.funds.map { it.balance.toFloat() to Color(it.category.color) } +
                    listOf(s.balances.available.toFloat() to fc.available.copy(alpha = 0.5f))
                SegmentedBar(segs, height = 12.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Allocating money doesn't spend it — your total stays the same until you add income or record an expense.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            AppCard(onClick = { nav.navigate(Routes.CURRENT) }, modifier = Modifier.testTag("availableCard")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Available", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Not assigned to any category",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        money.format(s.balances.available),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("fundBalance_Available"),
                    )
                }
            }
        }
        if (s.funds.isEmpty()) {
            item {
                AppCard {
                    Text("Start with some categories", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap a suggestion to create it, or make your own with “New category”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryIcons.suggestions.forEach { sug ->
                            AssistChip(
                                onClick = { vm.createSuggested(sug) },
                                label = { Text(sug.name) },
                                leadingIcon = {
                                    Icon(CategoryIcons.get(sug.icon), null, Modifier.size(18.dp), tint = Color(sug.color))
                                },
                                modifier = Modifier.testTag("suggest_${sug.name}"),
                            )
                        }
                    }
                }
            }
        }
        items(s.funds, key = { it.category.id }) { f -> FundCard(f) { nav.navigate(Routes.category(f.category.id)) } }
    }
}

@Composable
private fun FundCard(f: FundItem, onClick: () -> Unit) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    val c = f.category
    AppCard(onClick = onClick, modifier = Modifier.testTag("fund_${c.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(c)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Spent this month ${money.format(f.spentThisMonth)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                money.format(f.balance),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("fundBalance_${c.name}"),
            )
        }
        val limit = c.monthlyLimit
        if (limit != null) {
            Spacer(Modifier.height(12.dp))
            val ratio = f.spentThisMonth.toFloat() / limit
            ProgressBar(
                ratio,
                when {
                    ratio > 1f -> fc.expense
                    ratio >= 0.8f -> fc.warning
                    else -> Color(c.color)
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${money.format(f.spentThisMonth)} of ${money.format(limit)} monthly budget",
                style = MaterialTheme.typography.bodySmall,
                color = if (ratio > 1f) fc.expense else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val target = c.targetAmount
        if (target != null) {
            Spacer(Modifier.height(12.dp))
            ProgressBar(f.balance.toFloat() / target, Color(c.color))
            Spacer(Modifier.height(4.dp))
            Text(
                "Goal: ${money.format(f.balance)} of ${money.format(target)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalsTab(s: MyMoneyState, nav: NavController) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().testTag("goalsList"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (s.goals.isEmpty()) {
            item {
                AppCard {
                    EmptyState(
                        Icons.Rounded.Flag,
                        "No goals yet",
                        "Create a goal like “New phone — 300 OMR” or “Emergency fund” and add money to it over time.",
                        actionLabel = "New goal",
                        onAction = { nav.navigate(Routes.categoryEdit(goal = true)) },
                    )
                }
            }
        }
        items(s.goals, key = { it.category.id }) { g ->
            val c = g.category
            AppCard(onClick = { nav.navigate(Routes.category(c.id)) }, modifier = Modifier.testTag("goal_${c.name}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { g.progress },
                            modifier = Modifier.size(64.dp),
                            color = Color(c.color),
                            strokeWidth = 7.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text("${(g.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${money.format(g.saved)} of ${money.format(g.target)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (g.reached) "Goal reached 🎉" else "${money.format(g.remaining)} to go",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (g.reached) fc.income else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val date = g.targetDate
                if (date != null) {
                    Spacer(Modifier.height(10.dp))
                    val days = g.daysLeft ?: 0L
                    Text(
                        buildString {
                            append("Target date ${date.format(Dates.mediumDate)}")
                            if (!g.reached) {
                                append(if (days >= 0) " · $days days left" else " · ${-days} days overdue")
                                g.perMonthNeeded?.let { append(" · save ${money.format(it)}/month") }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (days < 0 && !g.reached) fc.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!g.reached) {
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { nav.navigate(Routes.add(TxType.TRANSFER, from = Routes.AVAILABLE, to = c.id)) },
                        modifier = Modifier.testTag("goalAdd_${c.name}"),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add money")
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetsTab(s: MyMoneyState, nav: NavController, onEdit: (Category) -> Unit) {
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().testTag("budgetsList"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Monthly spending limits for ${YearMonth.now().format(Dates.monthYear)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (s.budgets.isEmpty()) {
            item {
                AppCard {
                    EmptyState(
                        Icons.Rounded.Speed,
                        "No budgets",
                        "Set a monthly limit for any category, e.g. Food — 100 OMR. You'll be warned at 80% and when you go over.",
                    )
                }
            }
        }
        items(s.budgets, key = { it.category.id }) { b ->
            val color = when (b.state) {
                BudgetState.EXCEEDED -> fc.expense
                BudgetState.WARNING -> fc.warning
                BudgetState.OK -> Color(b.category.color)
            }
            AppCard(onClick = { onEdit(b.category) }, modifier = Modifier.testTag("budget_${b.category.name}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(b.category, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(b.category.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (b.state != BudgetState.OK) {
                        Icon(Icons.Rounded.Warning, contentDescription = "Budget warning", tint = color)
                    }
                }
                Spacer(Modifier.height(12.dp))
                ProgressBar(b.ratio, color, height = 10.dp)
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        "${money.format(b.spent)} spent",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (b.state == BudgetState.EXCEEDED) "${money.format(-b.remaining)} over"
                        else "${money.format(b.remaining)} left of ${money.format(b.limit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (b.state == BudgetState.EXCEEDED) fc.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetDialog(category: Category, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    val money = LocalMoney.current
    var text by remember { mutableStateOf(category.monthlyLimit?.let { money.input(it) } ?: "") }
    val parse = money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly budget — ${category.name}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Limit per month") },
                suffix = { Text(money.currency) },
                isError = parse is AmountParse.Error,
                supportingText = { if (parse is AmountParse.Error) Text(parse.message) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.testTag("budgetField"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { (parse as? AmountParse.Ok)?.let { onSave(it.milli) } },
                enabled = parse is AmountParse.Ok,
                modifier = Modifier.testTag("budgetSave"),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (category.monthlyLimit != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
