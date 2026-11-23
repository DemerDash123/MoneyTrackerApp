package com.moneytracker.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moneytracker.app.AppContainer
import com.moneytracker.app.MoneyTrackerApplication
import com.moneytracker.app.core.MoneyFormatter
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.ui.addedit.AddEditTransactionScreen
import com.moneytracker.app.ui.addedit.AddEditViewModel
import com.moneytracker.app.ui.calendar.CalendarScreen
import com.moneytracker.app.ui.calendar.CalendarViewModel
import com.moneytracker.app.ui.current.CurrentMoneyScreen
import com.moneytracker.app.ui.home.HomeScreen
import com.moneytracker.app.ui.home.HomeViewModel
import com.moneytracker.app.ui.money.CategoryDetailScreen
import com.moneytracker.app.ui.money.CategoryDetailViewModel
import com.moneytracker.app.ui.money.CategoryEditScreen
import com.moneytracker.app.ui.money.CategoryEditViewModel
import com.moneytracker.app.ui.money.MyMoneyScreen
import com.moneytracker.app.ui.money.MyMoneyViewModel
import com.moneytracker.app.ui.reports.StatisticsScreen
import com.moneytracker.app.ui.reports.StatisticsViewModel
import com.moneytracker.app.ui.settings.SettingsScreen
import com.moneytracker.app.ui.settings.SettingsViewModel
import com.moneytracker.app.ui.transactions.TransactionsScreen
import com.moneytracker.app.ui.transactions.TransactionsViewModel

val LocalMoney = staticCompositionLocalOf { MoneyFormatter("OMR", 3) }

fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as MoneyTrackerApplication).container

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(container()) }
        initializer { TransactionsViewModel(container()) }
        initializer { AddEditViewModel(createSavedStateHandle(), container()) }
        initializer { MyMoneyViewModel(container()) }
        initializer { CategoryDetailViewModel(createSavedStateHandle(), container()) }
        initializer { CategoryEditViewModel(createSavedStateHandle(), container()) }
        initializer { StatisticsViewModel(createSavedStateHandle(), container()) }
        initializer { CalendarViewModel(container()) }
        initializer { SettingsViewModel(container()) }
    }
}

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val MONEY = "money"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val CALENDAR = "calendar"
    const val CURRENT = "current"

    /** Sentinel values for optional category args: -2 = not preset, -1 = Available. */
    const val UNSET = -2L
    const val AVAILABLE = -1L

    fun add(
        type: TxType? = null,
        from: Long = UNSET,
        to: Long = UNSET,
        txId: Long = -1L,
    ) = "add?type=${type?.name ?: ""}&from=$from&to=$to&txId=$txId"

    fun place(id: Long?): Long = id ?: AVAILABLE
    fun category(id: Long) = "category/$id"
    fun categoryEdit(id: Long = -1L, goal: Boolean = false) = "categoryEdit?id=$id&goal=$goal"
    fun stats(tab: Int, month: String = "") = "stats?tab=$tab&ym=$month"
}

private data class TopLevel(val route: String, val pattern: String, val label: String, val icon: ImageVector, val tag: String)

private val topLevel = listOf(
    TopLevel(Routes.HOME, Routes.HOME, "Home", Icons.Rounded.Home, "nav_home"),
    TopLevel(Routes.TRANSACTIONS, Routes.TRANSACTIONS, "Activity", Icons.Rounded.Receipt, "nav_activity"),
    TopLevel(Routes.MONEY, Routes.MONEY, "My Money", Icons.Rounded.AccountBalanceWallet, "nav_money"),
    TopLevel(Routes.stats(0), "stats?tab={tab}&ym={ym}", "Statistics", Icons.Rounded.Insights, "nav_stats"),
    TopLevel(Routes.SETTINGS, Routes.SETTINGS, "Settings", Icons.Rounded.Settings, "nav_settings"),
)

@Composable
fun MoneyTrackerRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = topLevel.any { t -> destination?.hierarchy?.any { it.route == t.pattern } == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        val selected = destination?.hierarchy?.any { it.route == item.pattern } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    nav.navigate(item.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag(item.tag),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // On tablets keep content at a comfortable reading width.
        Box(
            Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            contentAlignment = Alignment.TopCenter,
        ) {
            AppNavHost(nav, Modifier.fillMaxSize().widthIn(max = 760.dp))
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, modifier: Modifier) {
    val longArg = { name: String, def: Long -> navArgument(name) { type = NavType.LongType; defaultValue = def } }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 14 } },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 14 } },
    ) {
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.TRANSACTIONS) { TransactionsScreen(nav) }
        composable(Routes.MONEY) { MyMoneyScreen(nav) }
        composable(
            "stats?tab={tab}&ym={ym}",
            arguments = listOf(
                navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                navArgument("ym") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { StatisticsScreen(nav) }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(Routes.CALENDAR) { CalendarScreen(nav) }
        composable(Routes.CURRENT) { CurrentMoneyScreen(nav) }
        composable(
            "add?type={type}&from={from}&to={to}&txId={txId}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType; defaultValue = "" },
                longArg("from", Routes.UNSET),
                longArg("to", Routes.UNSET),
                longArg("txId", -1L),
            ),
            enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 6 } },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
        ) { AddEditTransactionScreen(nav) }
        composable(
            "category/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { CategoryDetailScreen(nav) }
        composable(
            "categoryEdit?id={id}&goal={goal}",
            arguments = listOf(
                longArg("id", -1L),
                navArgument("goal") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { CategoryEditScreen(nav) }
    }
}
