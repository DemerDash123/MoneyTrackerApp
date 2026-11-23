package com.moneytracker.app

import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneytracker.app.TestSupport.u
import com.moneytracker.app.data.TxDraft
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.i18n.AppLanguage
import com.moneytracker.app.i18n.ArStrings
import com.moneytracker.app.i18n.EnStrings
import com.moneytracker.app.i18n.I18n
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

private fun ComposeRule.clickTag(tag: String) {
    waitForTag(tag)
    onNodeWithTag(tag, useUnmergedTree = true).performClick()
}

private typealias ComposeRule = androidx.compose.ui.test.junit4.AndroidComposeTestRule<androidx.test.ext.junit.rules.ActivityScenarioRule<MainActivity>, MainActivity>

private fun ComposeRule.isRtl(): Boolean {
    var rtl = false
    activityRule.scenario.onActivity { rtl = it.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL }
    return rtl
}

/** Switching the language from Settings, in both directions, while the app is running. */
@RunWith(AndroidJUnit4::class)
class LanguageSwitchTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(BeforeLaunch { TestSupport.resetApp(language = AppLanguage.EN) })
        .around(compose)

    @After fun tearDown() = TestSupport.resetData()

    @Test fun switchEnglishToArabicAndBack() {
        compose.waitForText(EnStrings.navHome)
        assertTrue(!compose.isRtl())

        compose.clickTag("nav_settings")
        compose.clickTag("lang_AR")
        // The screen is rebuilt in Arabic, right-to-left.
        compose.waitForText(ArStrings.navSettings, timeoutMs = 15_000)
        compose.waitForText(ArStrings.navHome)
        compose.waitForText(ArStrings.appearance)
        assertEquals(AppLanguage.AR, TestSupport.container.settings.settings.value.language)
        assertEquals(AppLanguage.AR, I18n.language)
        compose.waitUntil(10_000) { compose.isRtl() }

        compose.clickTag("lang_EN")
        compose.waitForText(EnStrings.appearance, timeoutMs = 15_000)
        compose.waitForText(EnStrings.navHome)
        assertEquals(AppLanguage.EN, TestSupport.container.settings.settings.value.language)
        compose.waitUntil(10_000) { !compose.isRtl() }
    }
}

/** The first launch asks for a language. */
@RunWith(AndroidJUnit4::class)
class FirstLaunchLanguageTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(BeforeLaunch {
            runBlocking { TestSupport.container.finance.resetAll() }
            TestSupport.container.settings.reset() // forget the language choice
        })
        .around(compose)

    @After fun tearDown() = TestSupport.resetData()

    @Test fun pickerAppearsAndChoosingArabicApplies() {
        compose.waitForTag("pickLanguage_AR")
        compose.waitForTag("pickLanguage_EN")
        compose.clickTag("pickLanguage_AR")
        compose.waitForText(ArStrings.navHome, timeoutMs = 15_000)
        compose.waitForText(ArStrings.totalBalance)
        assertTrue(TestSupport.container.settings.settings.value.languageChosen)
        compose.waitUntil(10_000) { compose.isRtl() }
        // The picker is not shown again.
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("pickLanguage_AR"), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }
}

/** Real money flows performed entirely through the Arabic, right-to-left interface. */
@RunWith(AndroidJUnit4::class)
class ArabicFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(BeforeLaunch { TestSupport.resetApp(language = AppLanguage.AR) })
        .around(compose)

    @After fun tearDown() = TestSupport.resetData()

    private val fin get() = TestSupport.container.finance
    private fun snapshot() = runBlocking { fin.currentSnapshot() }

    private fun saveAndClose() {
        compose.onNodeWithTag("saveButton").performScrollTo().performClick()
        compose.waitForTagGone("amountField")
        compose.waitForIdle()
    }

    @Test fun incomeAllocateSpendInArabic() {
        compose.waitForText(ArStrings.totalBalance)
        assertTrue(compose.isRtl())

        compose.clickTag("quickIncome")
        compose.waitForText(ArStrings.newTransaction)
        // Arabic-Indic digits, as typed on an Arabic keyboard: ٥٠٠ = 500
        compose.onNodeWithTag("amountField").performTextReplacement("٥٠٠")
        compose.onNodeWithTag("descriptionField").performScrollTo().performTextInput("راتب")
        saveAndClose()
        compose.waitForTagContains("totalBalance", "500.000 OMR")
        assertTrue(snapshot().transactions.any { it.description == "راتب" && it.amount == u(500.0) })

        compose.clickTag("nav_money")
        compose.waitForText(ArStrings.tabCategories)
        val food = ArStrings.suggestionNames[1] // "طعام"
        compose.clickTag("suggest_$food")
        compose.clickTag("fund_$food")
        compose.clickTag("catAdd")
        compose.waitForTag("amountField")
        compose.onNodeWithTag("amountField").performTextReplacement("100")
        saveAndClose()
        compose.waitForTagContains("categoryBalance", "100.000 OMR")

        compose.clickTag("catSpend")
        compose.waitForTag("amountField")
        compose.onNodeWithTag("amountField").performTextReplacement("15")
        saveAndClose()
        compose.waitForTagContains("categoryBalance", "85.000 OMR")

        val s = snapshot()
        assertEquals(u(485.0), s.balances.total)
        val foodId = s.categories.single { it.name == food }.id
        assertEquals(u(85.0), s.balances.of(foodId))
        // Allocation description is stored in Arabic.
        assertTrue(s.transactions.any { it.description == ArStrings.allocatedTo(food) })

        compose.onNodeWithContentDescription(ArStrings.back).performClick()
        compose.waitForTagGone("categoryBalance")
        compose.clickTag("nav_home")
        compose.waitForTagContains("totalBalance", "485.000 OMR")
        compose.waitForTagContains("availableBalance", "400.000 OMR")
    }

    @Test fun arabicValidationMessages() {
        runBlocking { fin.addTransaction(TxDraft(TxType.INCOME, u(50.0), null, null, "", "", System.currentTimeMillis() - 60_000)) }
        compose.clickTag("quickExpense")
        compose.waitForTag("amountField")
        compose.onNodeWithTag("amountField").performTextReplacement("abc")
        compose.waitForText(ArStrings.invalidAmount)
        compose.onNodeWithTag("amountField").performTextReplacement("80")
        compose.onNodeWithTag("saveButton").performScrollTo().performClick()
        compose.waitForText("المال المتاح غير كافٍ", substring = true)
        assertEquals(1, snapshot().transactions.size)
    }

    @Test fun arabicStatisticsAndSettingsRender() {
        runBlocking {
            fin.addTransaction(TxDraft(TxType.INCOME, u(300.0), null, null, "راتب", "", System.currentTimeMillis() - 120_000))
            fin.addTransaction(TxDraft(TxType.EXPENSE, u(20.0), null, null, "قهوة", "", System.currentTimeMillis() - 60_000))
        }
        compose.clickTag("nav_stats")
        compose.waitForText(ArStrings.overview)
        compose.clickTag("statsTab_1")
        compose.waitForTagContains("monthIncome", "300.000 OMR")
        compose.waitForTagContains("monthExpense", "20.000 OMR")
        compose.waitForText(ArStrings.dailySpending)

        compose.clickTag("nav_activity")
        compose.waitForText("قهوة")
        compose.waitForText(ArStrings.today)

        compose.clickTag("nav_settings")
        compose.waitForText(ArStrings.yourData)
        compose.onNodeWithTag("settingsList").performScrollToNode(androidx.compose.ui.test.hasTestTag("resetData"))
        compose.waitForText(ArStrings.resetAllData)
    }
}
