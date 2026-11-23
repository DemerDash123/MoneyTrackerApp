package com.moneytracker.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.moneytracker.app.AppContainer
import com.moneytracker.app.core.Dates
import com.moneytracker.app.data.DeleteCategoryResult
import com.moneytracker.app.data.FinanceSnapshot
import com.moneytracker.app.data.db.Category
import com.moneytracker.app.data.db.MoneyTransaction
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.domain.CategoryStats
import com.moneytracker.app.domain.FinanceException
import com.moneytracker.app.domain.Ledger
import com.moneytracker.app.ui.AppViewModelProvider
import com.moneytracker.app.i18n.S
import com.moneytracker.app.ui.LocalMoney
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.*
import com.moneytracker.app.ui.theme.AmountStyle
import com.moneytracker.app.ui.theme.FinanceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class CategoryDetailState(
    val snapshot: FinanceSnapshot,
    val category: Category?,
    val balance: Long,
    val lifetime: CategoryStats,
    val month: CategoryStats,
    val history: List<MoneyTransaction>,
)

class CategoryDetailViewModel(handle: SavedStateHandle, private val container: AppContainer) : ViewModel() {
    val id: Long = handle.get<Long>("id") ?: 0L
    val message = MutableStateFlow<String?>(null)

    val state: StateFlow<CategoryDetailState?> = container.finance.snapshot.map { s ->
        CategoryDetailState(
            snapshot = s,
            category = s.category(id),
            balance = s.balances.of(id),
            lifetime = Ledger.categoryStats(id, s.transactions),
            month = Ledger.categoryStats(id, s.transactions, Dates.monthRange(YearMonth.now())),
            history = s.transactions.filter { Ledger.touches(it, id) },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val money = container.settings.settings.value.formatter
                val msg = when (val r = container.finance.deleteCategory(id)) {
                    DeleteCategoryResult.Deleted -> S.categoryDeleted
                    is DeleteCategoryResult.Archived ->
                        if (r.returnedToAvailable > 0) S.categoryDeletedReturned(money.format(r.returnedToAvailable))
                        else S.categoryDeletedHistoryKept
                }
                onDone(msg)
            } catch (e: FinanceException) {
                message.value = e.message
            }
        }
    }
}

@Composable
fun CategoryDetailScreen(nav: NavController, vm: CategoryDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    val money = LocalMoney.current
    val fc = FinanceTheme.colors

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }

    val s = state
    val c = s?.category
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(c?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = S.back)
                    }
                },
                actions = {
                    if (c != null && !c.archived) {
                        IconButton(onClick = { nav.navigate(Routes.categoryEdit(c.id)) }, modifier = Modifier.testTag("editCategory")) {
                            Icon(Icons.Rounded.Edit, contentDescription = S.editCategory)
                        }
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("deleteCategory")) {
                            Icon(Icons.Rounded.Delete, contentDescription = S.deleteCategory)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (s == null || c == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (s == null) CircularProgressIndicator() else Text(S.categoryNotExists)
            }
            return@Scaffold
        }
        val color = Color(c.color)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("categoryDetail"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.72f))))
                        .padding(22.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(CategoryIcons.get(c.icon), null, tint = Color.White) }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (c.archived) S.deletedCategory else S.currentBalance,
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            money.format(s.balance),
                            color = Color.White,
                            style = AmountStyle.copy(fontSize = 32.sp),
                            modifier = Modifier.testTag("categoryBalance"),
                        )
                        Spacer(Modifier.height(14.dp))
                        Row {
                            StatBlock(S.totalAdded, money.format(s.lifetime.totalAdded), Modifier.weight(1f),
                                valueColor = Color.White, labelColor = Color.White.copy(alpha = 0.8f),
                                valueStyle = MaterialTheme.typography.bodyLarge)
                            StatBlock(S.totalSpent, money.format(s.lifetime.spent), Modifier.weight(1f),
                                valueColor = Color.White, labelColor = Color.White.copy(alpha = 0.8f),
                                valueStyle = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            StatBlock(S.movedOut, money.format(s.lifetime.transfersOut), Modifier.weight(1f),
                                valueColor = Color.White, labelColor = Color.White.copy(alpha = 0.8f),
                                valueStyle = MaterialTheme.typography.bodyLarge)
                            StatBlock(S.remaining, money.format(s.balance), Modifier.weight(1f),
                                valueColor = Color.White, labelColor = Color.White.copy(alpha = 0.8f),
                                valueStyle = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (!c.archived) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionTile(S.addMoney, Icons.Rounded.AddCircle, fc.income, Modifier.weight(1f).testTag("catAdd")) {
                            nav.navigate(Routes.add(TxType.TRANSFER, from = Routes.AVAILABLE, to = c.id))
                        }
                        ActionTile(S.spend, Icons.Rounded.ShoppingCart, fc.expense, Modifier.weight(1f).testTag("catSpend")) {
                            nav.navigate(Routes.add(TxType.EXPENSE, from = c.id))
                        }
                        ActionTile(S.remove, Icons.Rounded.RemoveCircle, fc.available, Modifier.weight(1f).testTag("catRemove")) {
                            nav.navigate(Routes.add(TxType.TRANSFER, from = c.id, to = Routes.AVAILABLE))
                        }
                        ActionTile(S.transfer, Icons.Rounded.SwapHoriz, fc.transfer, Modifier.weight(1f).testTag("catTransfer")) {
                            nav.navigate(Routes.add(TxType.TRANSFER, from = c.id))
                        }
                    }
                }
            }
            item {
                AppCard {
                    Text(S.thisMonth, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        StatBlock(S.added, money.format(s.month.totalAdded), Modifier.weight(1f), valueColor = fc.income)
                        StatBlock(S.spent, money.format(s.month.spent), Modifier.weight(1f), valueColor = fc.expense)
                        StatBlock(S.movedOut, money.format(s.month.transfersOut), Modifier.weight(1f))
                    }
                    c.monthlyLimit?.let { limit ->
                        Spacer(Modifier.height(12.dp))
                        val ratio = s.month.spent.toFloat() / limit
                        ProgressBar(ratio, if (ratio > 1f) fc.expense else if (ratio >= 0.8f) fc.warning else color)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (s.month.spent > limit) S.overBudgetBy(money.format(s.month.spent - limit))
                            else S.leftOfBudget(money.format(limit - s.month.spent), money.format(limit)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (s.month.spent > limit) fc.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    c.targetAmount?.let { target ->
                        Spacer(Modifier.height(12.dp))
                        ProgressBar(s.balance.toFloat() / target, color)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            S.goalProgress(money.format(s.balance), money.format(target)) +
                                (c.targetDate?.let { S.byDate(java.time.LocalDate.ofEpochDay(it).format(Dates.mediumDate)) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { SectionHeader(S.history(s.history.size)) }
            if (s.history.isEmpty()) {
                item {
                    Text(
                        S.noActivityYet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(s.history, key = { it.id }) { t ->
                TransactionRow(t, s.snapshot, showDate = true, perspectiveOf = c.id, usePerspective = true) {
                    nav.navigate(Routes.add(txId = t.id))
                }
            }
        }
    }

    if (confirmDelete && c != null) {
        val hasBalance = (s?.balance ?: 0L) > 0
        ConfirmDialog(
            title = S.deleteCategoryTitle(c.name),
            message = if ((s?.history?.size ?: 0) == 0) S.categoryNoTxRemoved
            else S.pastTxStay +
                if (hasBalance) S.remainingReturned(money.format(s!!.balance)) else "",
            confirmLabel = S.delete,
            onConfirm = {
                confirmDelete = false
                vm.delete { msg ->
                    nav.previousBackStackEntry?.savedStateHandle?.set("message", msg)
                    nav.popBackStack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, color, size = 38.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
