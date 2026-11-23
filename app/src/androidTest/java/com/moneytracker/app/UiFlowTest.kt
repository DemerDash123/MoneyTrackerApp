package com.moneytracker.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneytracker.app.TestSupport.u
import com.moneytracker.app.data.CategoryInput
import com.moneytracker.app.data.ThemeMode
import com.moneytracker.app.data.TxDraft
import com.moneytracker.app.data.db.TxType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * End-to-end tests that drive the real UI (real activity, real database) the way a user would.
 */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val fin get() = TestSupport.container.finance
    private val now get() = System.currentTimeMillis() - 60_000

    @Before fun setUp() {
        TestSupport.resetApp()
        rule.waitForIdle()
    }

    @After fun tearDown() {
        TestSupport.resetApp()
    }

    private fun snapshot() = runBlocking { fin.currentSnapshot() }
    private fun enterAmount(v: String) = rule.onNodeWithTag("amountField").performTextReplacement(v)
    private fun saveForm() = rule.onNodeWithTag("saveButton").performScrollTo().performClick()
    private fun clickTag(tag: String) {
        rule.waitForTag(tag)
        rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
    }

    @Test fun addIncomeAllocateAndSpendThroughUi() {
        clickTag("quickIncome")
        rule.waitForTag("amountField")
        enterAmount("500")
        rule.onNodeWithTag("descriptionField").performScrollTo().performTextInput("Salary")
        saveForm()
        rule.waitForTagText("totalBalance", "500.000 OMR")
        rule.waitForTagText("availableBalance", "500.000 OMR")

        clickTag("nav_money")
        clickTag("suggest_Food")
        clickTag("fund_Food")
        clickTag("catAdd")
        rule.waitForTag("amountField")
        enterAmount("100")
        saveForm()
        rule.waitForTagText("categoryBalance", "100.000 OMR")

        clickTag("catSpend")
        rule.waitForTag("amountField")
        enterAmount("15")
        rule.onNodeWithTag("descriptionField").performScrollTo().performTextInput("Lunch")
        saveForm()
        rule.waitForTagText("categoryBalance", "85.000 OMR")

        rule.onNodeWithContentDescription("Back").performClick()
        clickTag("nav_home")
        rule.waitForTagText("totalBalance", "485.000 OMR")
        rule.waitForTagText("availableBalance", "400.000 OMR")
        rule.waitForTagText("allocatedBalance", "85.000 OMR")

        val s = snapshot()
        assertEquals(4, s.transactions.size)
        assertEquals(u(485.0), s.balances.total)
    }

    @Test fun invalidInputsAreRejectedAndNothingIsSaved() {
        runBlocking { fin.addTransaction(TxDraft(TxType.INCOME, u(50.0), null, null, "Seed", "", now)) }
        rule.waitForTagText("totalBalance", "50.000 OMR")
        clickTag("quickExpense")
        rule.waitForTag("amountField")

        enterAmount("abc")
        rule.waitForText("Enter a valid amount")
        enterAmount("0")
        rule.waitForText("Amount must be greater than zero")
        enterAmount("1.2345")
        rule.waitForText("Use at most 3 decimal places")
        saveForm()
        rule.waitForTag("formError")

        enterAmount("80")
        saveForm()
        rule.waitForText("Not enough available money", substring = true)

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForTagText("totalBalance", "50.000 OMR")
        assertEquals(1, snapshot().transactions.size)
    }

    @Test fun editAndDeleteTransactionFromHistory() {
        val id = runBlocking { fin.addTransaction(TxDraft(TxType.INCOME, u(100.0), null, null, "Gift", "", now)) }
        clickTag("nav_activity")
        clickTag("txRow_$id")
        clickTag("detailEdit")
        rule.waitForTag("amountField")
        rule.waitForTagText("amountField", "100", timeoutMs = 5_000)
        enterAmount("120")
        saveForm()
        rule.waitUntil(10_000) { snapshot().balances.total == u(120.0) }
        assertEquals(1, snapshot().transactions.size)

        clickTag("txRow_$id")
        clickTag("detailDelete")
        clickTag("confirmButton")
        rule.waitUntil(10_000) { snapshot().transactions.isEmpty() }
        assertEquals(0L, snapshot().balances.total)
    }

    @Test fun searchAndFilterTransactions() {
        runBlocking {
            fin.addTransaction(TxDraft(TxType.INCOME, u(200.0), null, null, "Salary", "", now))
            fin.addTransaction(TxDraft(TxType.EXPENSE, u(20.0), null, null, "Coffee beans", "", now))
            fin.addTransaction(TxDraft(TxType.EXPENSE, u(5.0), null, null, "Bus ticket", "", now))
        }
        clickTag("nav_activity")
        rule.waitForText("Bus ticket")
        rule.onNodeWithTag("searchField").performTextInput("coffee")
        rule.waitForTextGone("Bus ticket")
        rule.waitForText("Coffee beans")
        rule.waitForTextGone("Salary")

        rule.onNodeWithTag("searchField").performTextClearance()
        clickTag("typeFilter_INCOME")
        rule.waitForTextGone("Coffee beans")
        rule.waitForText("Salary")

        clickTag("typeFilter_ALL")
        rule.waitForText("Bus ticket")
        clickTag("periodFilter")
        rule.onNodeWithText("Last month").performClick()
        rule.waitForText("Nothing matches")
    }

    @Test fun themeAndCurrencySettings() {
        clickTag("nav_settings")
        clickTag("theme_DARK")
        rule.waitUntil(5_000) { TestSupport.container.settings.settings.value.theme == ThemeMode.DARK }
        clickTag("theme_LIGHT")
        rule.waitUntil(5_000) { TestSupport.container.settings.settings.value.theme == ThemeMode.LIGHT }

        clickTag("currencySetting")
        rule.waitForTag("currencyCode")
        rule.onNodeWithTag("currencyCode").performTextReplacement("USD")
        clickTag("decimals_2")
        clickTag("currencySave")
        rule.waitUntil(5_000) {
            val s = TestSupport.container.settings.settings.value
            s.currency == "USD" && s.decimals == 2
        }
        runBlocking { fin.addTransaction(TxDraft(TxType.INCOME, u(10.5), null, null, "", "", now)) }
        clickTag("nav_home")
        rule.waitForTagText("totalBalance", "10.50 USD")
    }

    @Test fun transferBudgetAndGoalFlows() {
        runBlocking {
            fin.addTransaction(TxDraft(TxType.INCOME, u(300.0), null, null, "Salary", "", now))
            val savings = fin.createCategory(CategoryInput("Savings", "savings", 0xFF10B981.toInt()))
            fin.createCategory(CategoryInput("Shopping", "shopping", 0xFFEC4899.toInt()))
            fin.addTransaction(TxDraft(TxType.TRANSFER, u(100.0), null, savings, "", "", now))
        }
        clickTag("nav_money")
        clickTag("fund_Savings")
        clickTag("catTransfer")
        clickTag("dest_Shopping")
        enterAmount("30")
        saveForm()
        rule.waitForTagText("categoryBalance", "70.000 OMR")
        val s = snapshot()
        val shopping = s.categories.first { it.name == "Shopping" }.id
        assertEquals(u(30.0), s.balances.of(shopping))
        assertEquals(u(300.0), s.balances.total)

        rule.onNodeWithContentDescription("Back").performClick()
        clickTag("moneyTab_2")
        clickTag("newBudgetFab")
        rule.waitForText("Budget for which category?")
        rule.onNodeWithText("Shopping").performClick()
        rule.waitForTag("budgetField")
        rule.onNodeWithTag("budgetField").performTextReplacement("20")
        clickTag("budgetSave")
        rule.waitForTag("budget_Shopping")
        runBlocking { fin.addTransaction(TxDraft(TxType.EXPENSE, u(25.0), shopping, null, "Shoes", "", now)) }
        rule.waitForText("5.000 OMR over", substring = true)

        clickTag("moneyTab_1")
        clickTag("newGoalFab")
        rule.waitForTag("categoryName")
        rule.onNodeWithTag("categoryName").performTextInput("New phone")
        rule.onNodeWithTag("targetField").performScrollTo().performTextInput("300")
        rule.onNodeWithTag("saveCategory").performScrollTo().performClick()
        clickTag("goalAdd_New phone")
        rule.waitForTag("amountField")
        enterAmount("50")
        saveForm()
        rule.waitForText("50.000 OMR of 300.000 OMR")
        val phone = snapshot().categories.first { it.name == "New phone" }
        assertEquals(u(300.0), phone.targetAmount)
        assertEquals(u(50.0), snapshot().balances.of(phone.id))
    }

    @Test fun deletingCategoryReturnsMoneyAndKeepsHistory() {
        runBlocking {
            fin.addTransaction(TxDraft(TxType.INCOME, u(100.0), null, null, "", "", now))
            val food = fin.createCategory(CategoryInput("Food", "restaurant", 0xFFF59E0B.toInt()))
            fin.addTransaction(TxDraft(TxType.TRANSFER, u(40.0), null, food, "", "", now))
            fin.addTransaction(TxDraft(TxType.EXPENSE, u(10.0), food, null, "Dinner", "", now))
        }
        clickTag("nav_money")
        clickTag("fund_Food")
        clickTag("deleteCategory")
        clickTag("confirmButton")
        rule.waitUntil(10_000) { snapshot().categories.first().archived }
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("fund_Food"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        val s = snapshot()
        assertEquals(u(90.0), s.balances.total)
        assertEquals(u(90.0), s.balances.available)
        assertTrue(s.transactions.any { it.description == "Dinner" })
    }

    @Test fun monthlyReportShowsTheSelectedMonthOnly() {
        val zone = ZoneId.systemDefault()
        val last = YearMonth.now().minusMonths(1)
        fun at(d: LocalDate) = LocalDateTime.of(d, LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        runBlocking {
            fin.addTransaction(TxDraft(TxType.INCOME, u(400.0), null, null, "Old salary", "", at(last.atDay(10))))
            fin.addTransaction(TxDraft(TxType.EXPENSE, u(50.0), null, null, "Old spend", "", at(last.atDay(11))))
            fin.addTransaction(TxDraft(TxType.INCOME, u(300.0), null, null, "Salary", "", at(YearMonth.now().atDay(1))))
            fin.addTransaction(TxDraft(TxType.EXPENSE, u(20.0), null, null, "Snacks", "", now))
        }
        clickTag("nav_stats")
        clickTag("statsTab_1")
        rule.waitForTagText("monthIncome", "300.000 OMR")
        rule.waitForTagText("monthExpense", "20.000 OMR")
        rule.waitForTagText("monthCount", "2")
        rule.onNodeWithContentDescription("Previous month").performClick()
        rule.waitForTagText("monthIncome", "400.000 OMR")
        rule.waitForTagText("monthExpense", "50.000 OMR")
        rule.waitForTagText("monthNet", "350.000 OMR")

        clickTag("statsTab_0")
        rule.waitForTag("overviewList")
        rule.onNodeWithTag("overviewList").performScrollToNode(hasTestTag("spendingBreakdown"))
        rule.waitForTagText("donutCenterValue", "20.000 OMR")
    }

    @Test fun calendarShowsTransactionsOfSelectedDay() {
        runBlocking {
            fin.addTransaction(TxDraft(TxType.INCOME, u(10.0), null, null, "Pocket money", "", now))
            fin.addTransaction(TxDraft(TxType.EXPENSE, u(3.0), null, null, "Lunch", "", now))
        }
        rule.onNodeWithContentDescription("Calendar").performClick()
        rule.waitForTag("calendarGrid")
        clickTag("day_${LocalDate.now()}")
        rule.waitForText("Lunch")
        rule.waitForText("Pocket money")
    }

    @Test fun currentMoneyAddAndSubtract() {
        clickTag("totalBalance")
        clickTag("addMoney")
        rule.waitForTag("amountField")
        enterAmount("25")
        saveForm()
        rule.waitForTagText("currentTotal", "25.000 OMR")
        clickTag("subtractMoney")
        rule.waitForTag("amountField")
        enterAmount("5")
        saveForm()
        rule.waitForTagText("currentTotal", "20.000 OMR")
        assertEquals(2, snapshot().transactions.size)
        assertTrue(rule.onAllNodes(hasText("Money in")).fetchSemanticsNodes().isNotEmpty())
    }
}
