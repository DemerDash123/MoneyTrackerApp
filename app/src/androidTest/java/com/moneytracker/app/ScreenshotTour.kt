package com.moneytracker.app

import android.graphics.Bitmap
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneytracker.app.TestSupport.u
import com.moneytracker.app.data.CategoryInput
import com.moneytracker.app.data.ThemeMode
import com.moneytracker.app.data.TxDraft
import com.moneytracker.app.data.db.TxType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Walks through every screen with a realistic three-month history, checks each screen renders,
 * and saves real screenshots (pulled by CI into docs/screenshots).
 * The history is TEST DATA entered through the app's own repository — the app itself ships empty.
 *
 * Instrumentation args: prefix=<name> (file prefix), full=true|false (all screens or a subset).
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val args = InstrumentationRegistry.getArguments()
    private val prefix = args.getString("prefix") ?: "phone"
    private val full = (args.getString("full") ?: "true") == "true"
    private val outDir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screenshots")

    @Before fun seed() {
        TestSupport.resetApp(ThemeMode.LIGHT)
        seedHistory()
        rule.waitForIdle()
    }

    @After fun cleanup() = TestSupport.resetApp()

    private fun shot(name: String) {
        rule.waitForIdle()
        // Force a fresh frame so the capture never shows a stale surface.
        rule.runOnUiThread { rule.activity.window.decorView.invalidate() }
        rule.waitForIdle()
        Thread.sleep(1200)
        rule.runOnUiThread { rule.activity.window.decorView.invalidate() }
        rule.waitForIdle()
        Thread.sleep(300)
        outDir.mkdirs()
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        FileOutputStream(File(outDir, "${prefix}_$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun click(tag: String) {
        rule.waitForTag(tag)
        rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
    }

    private fun back(goneTag: String) {
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForTagGone(goneTag)
        rule.waitForIdle()
    }

    @Test fun tour() {
        rule.waitForTag("totalBalance")
        rule.onNodeWithTag("homeList").performScrollToIndex(1)
        rule.onNodeWithTag("homeList").performScrollToIndex(0)
        shot("01_home")
        rule.onNodeWithTag("homeList").performScrollToIndex(4)
        shot("02_home_spending")
        rule.onNodeWithTag("homeList").performScrollToIndex(0)

        click("quickExpense")
        rule.waitForTag("amountField")
        shot("03_add_expense")
        back("amountField")

        click("nav_activity")
        rule.waitForTag("transactionList")
        shot("04_transactions")

        if (full) {
            val firstExpense = runBlocking { TestSupport.container.finance.currentSnapshot() }
                .transactions.first { it.type == TxType.EXPENSE }
            rule.onNodeWithTag("transactionList").performScrollToNode(hasTestTag("txRow_${firstExpense.id}"))
            click("txRow_${firstExpense.id}")
            rule.waitForTag("detailEdit")
            shot("05_transaction_detail")
            androidx.test.espresso.Espresso.pressBack()
            rule.waitForTagGone("detailEdit")
            rule.waitForIdle()

            click("openCalendar")
            rule.waitForTag("calendarGrid")
            shot("06_calendar")
            back("calendarGrid")
        }

        click("nav_money")
        rule.waitForTag("fundsList")
        shot("07_my_money")

        if (full) {
            rule.onNodeWithTag("fundsList").performScrollToNode(hasTestTag("fund_Food"))
            click("fund_Food")
            rule.waitForTag("categoryBalance")
            shot("08_category_detail")
            back("categoryBalance")
        }

        click("moneyTab_1")
        rule.waitForTag("goalsList")
        shot("09_goals")
        click("moneyTab_2")
        rule.waitForTag("budgetsList")
        shot("10_budgets")
        click("moneyTab_0")

        click("nav_stats")
        rule.waitForTag("overviewList")
        shot("11_statistics")
        rule.onNodeWithTag("overviewList").performScrollToNode(hasTestTag("incomeExpenseChart"))
        shot("12_statistics_charts")
        if (full) {
            rule.onNodeWithTag("overviewList").performScrollToNode(hasTestTag("insightsCard"))
            shot("13_insights")
        }

        click("statsTab_1")
        rule.waitForTag("monthSummary")
        shot("14_monthly")
        rule.onNodeWithTag("monthlyList").performScrollToNode(hasTestTag("monthCategories"))
        shot("15_monthly_categories")
        click("statsTab_0")

        click("nav_settings")
        rule.waitForTag("settingsList")
        shot("16_settings")

        if (full) {
            click("nav_home")
            click("totalBalance")
            rule.waitForTag("addMoney")
            shot("17_current_money")
            back("addMoney")

            // Dark mode
            TestSupport.container.settings.setTheme(ThemeMode.DARK)
            rule.waitForIdle()
            rule.onNodeWithTag("homeList").performScrollToIndex(0)
            shot("18_home_dark")
            click("nav_stats")
            rule.waitForTag("overviewList")
            shot("19_statistics_dark")
            click("nav_money")
            rule.waitForTag("fundsList")
            shot("20_my_money_dark")
            TestSupport.container.settings.setTheme(ThemeMode.LIGHT)
        }
    }

    /** Three months of plausible personal finances (OMR). */
    private fun seedHistory() = runBlocking {
        val f = TestSupport.container.finance
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        fun at(d: LocalDate, hour: Int = 12): Long {
            val dt = LocalDateTime.of(d, LocalTime.of(hour, 15))
            return dt.atZone(zone).toInstant().toEpochMilli()
        }
        val savings = f.createCategory(CategoryInput("Emergency Fund", "emergency", 0xFFEF4444.toInt(), targetAmount = u(1000.0)))
        val food = f.createCategory(CategoryInput("Food", "restaurant", 0xFFF59E0B.toInt(), monthlyLimit = u(120.0)))
        val shopping = f.createCategory(CategoryInput("Shopping", "shopping", 0xFFEC4899.toInt(), monthlyLimit = u(60.0)))
        val transport = f.createCategory(CategoryInput("Transportation", "car", 0xFF3B82F6.toInt(), monthlyLimit = u(45.0)))
        val education = f.createCategory(CategoryInput("Education", "school", 0xFF8B5CF6.toInt()))
        val phone = f.createCategory(
            CategoryInput("New Phone", "phone", 0xFF14B8A6.toInt(), targetAmount = u(320.0), targetDate = today.plusMonths(4)),
        )
        val fun_ = f.createCategory(CategoryInput("Entertainment", "movie", 0xFF6366F1.toInt(), monthlyLimit = u(40.0)))

        suspend fun tx(type: TxType, amount: Double, from: Long?, to: Long?, desc: String, d: LocalDate, hour: Int = 12) {
            if (d.isAfter(today)) return
            f.addTransaction(TxDraft(type, u(amount), from, to, desc, "", at(d, hour)))
        }

        for (back in 2 downTo 0) {
            val ym = YearMonth.from(today).minusMonths(back.toLong())
            fun day(n: Int) = ym.atDay(minOf(n, ym.lengthOfMonth()))
            val scale = 1.0 + back * 0.08
            tx(TxType.INCOME, 850.0, null, null, "Salary", day(1), 9)
            tx(TxType.TRANSFER, 150.0, null, savings, "", day(1), 10)
            tx(TxType.TRANSFER, 120.0, null, food, "", day(1), 10)
            tx(TxType.TRANSFER, 60.0, null, shopping, "", day(1), 10)
            tx(TxType.TRANSFER, 45.0, null, transport, "", day(1), 10)
            tx(TxType.TRANSFER, 40.0, null, phone, "", day(1), 11)
            tx(TxType.TRANSFER, 35.0, null, fun_, "", day(1), 11)
            tx(TxType.TRANSFER, 30.0, null, education, "", day(1), 11)
            tx(TxType.EXPENSE, 18.750 * scale, food, null, "Lulu Hypermarket", day(3))
            tx(TxType.EXPENSE, 6.5, food, null, "Shawarma dinner", day(5), 20)
            tx(TxType.EXPENSE, 12.0, transport, null, "Fuel", day(6), 8)
            tx(TxType.EXPENSE, 22.4 * scale, shopping, null, "Clothes", day(8), 17)
            tx(TxType.EXPENSE, 9.0, fun_, null, "Cinema", day(9), 21)
            if (back == 1) tx(TxType.INCOME, 120.0, null, null, "Freelance logo design", day(12), 15)
            tx(TxType.EXPENSE, 24.3 * scale, food, null, "Groceries", day(13))
            tx(TxType.EXPENSE, 12.0, transport, null, "Fuel", day(15), 8)
            tx(TxType.EXPENSE, 15.0, education, null, "Online course", day(16), 19)
            tx(TxType.EXPENSE, 4.2, null, null, "Coffee", day(17), 9)
            tx(TxType.EXPENSE, 31.6 * scale, food, null, "Restaurant with family", day(19), 21)
            tx(TxType.EXPENSE, 14.0 + back * 6, shopping, null, "Headphones", day(21), 18)
            tx(TxType.EXPENSE, 11.5, transport, null, "Car wash & fuel", day(22), 8)
            if (back == 0) tx(TxType.INCOME, 25.0, null, null, "Sold old books", day(18), 16)
            tx(TxType.EXPENSE, 7.0, fun_, null, "Games", day(23), 20)
            tx(TxType.EXPENSE, 16.9 * scale, food, null, "Groceries", day(24), 12)
            tx(TxType.EXPENSE, 8.0, null, null, "Gift for friend", day(25), 13)
        }
        // A move between categories, and a deliberate over-budget month for Shopping
        tx(TxType.TRANSFER, 20.0, fun_, shopping, "Moved for a sale", today, 9)
        tx(TxType.EXPENSE, 30.0, shopping, null, "Weekend sale", today, 10)
    }
}
