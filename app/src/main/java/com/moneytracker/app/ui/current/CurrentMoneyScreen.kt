package com.moneytracker.app.ui.current

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.ui.AppViewModelProvider
import com.moneytracker.app.i18n.S
import com.moneytracker.app.ui.LocalMoney
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.*
import com.moneytracker.app.ui.home.HomeViewModel
import com.moneytracker.app.ui.theme.AmountStyle
import com.moneytracker.app.ui.theme.FinanceTheme

/** "Current money": the balance, plus the quickest way to add or subtract money. */
@Composable
fun CurrentMoneyScreen(nav: NavController, vm: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    var filter by remember { mutableStateOf<TxType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S.currentMoney) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = S.back)
                    }
                },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val records = s.snapshot.transactions.filter {
            it.type != TxType.TRANSFER && (filter == null || it.type == filter)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(fc.heroBrush)
                        .padding(22.dp),
                ) {
                    Text(S.totalBalance, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    Text(money.format(s.balances.total), color = Color.White, style = AmountStyle.copy(fontSize = 32.sp),
                        modifier = Modifier.testTag("currentTotal"))
                    Spacer(Modifier.height(14.dp))
                    Row {
                        StatBlock(S.available, money.format(s.balances.available), Modifier.weight(1f),
                            valueColor = Color.White, labelColor = Color.White.copy(alpha = 0.75f))
                        StatBlock(S.inCategories, money.format(s.balances.allocated), Modifier.weight(1f),
                            valueColor = Color.White, labelColor = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { nav.navigate(Routes.add(TxType.INCOME)) },
                        modifier = Modifier.weight(1f).height(56.dp).testTag("addMoney"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = fc.income, contentColor = Color.White),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text(S.addMoney)
                    }
                    Button(
                        onClick = { nav.navigate(Routes.add(TxType.EXPENSE)) },
                        modifier = Modifier.weight(1f).height(56.dp).testTag("subtractMoney"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = fc.expense, contentColor = Color.White),
                    ) {
                        Icon(Icons.Rounded.Remove, null)
                        Spacer(Modifier.width(6.dp))
                        Text(S.subtract)
                    }
                }
            }
            item {
                Text(
                    S.currentMoneyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == null, { filter = null }, { Text(S.all) })
                    FilterChip(filter == TxType.INCOME, { filter = TxType.INCOME }, { Text(S.moneyInLegend) })
                    FilterChip(filter == TxType.EXPENSE, { filter = TxType.EXPENSE }, { Text(S.moneyOutLegend) })
                }
            }
            if (records.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.Payments, S.nothingHereYet, S.incomeExpensesAppearHere)
                }
            }
            items(records, key = { it.id }) { t ->
                AppCard(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                    TransactionRow(t, s.snapshot, showDate = true) { nav.navigate(Routes.add(txId = t.id)) }
                }
            }
        }
    }
}
