package com.moneytracker.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.moneytracker.app.data.ThemeMode
import com.moneytracker.app.i18n.AppLanguage
import org.junit.rules.ExternalResource
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToLong

/**
 * Compose's test framework can throw "performMeasureAndLayout called during measure layout"
 * when the database changes on a background thread while the test is polling the UI.
 * That is a test-harness race, not an app failure: treat it as "not yet" and poll again.
 */
private fun pollSafely(condition: () -> Boolean): Boolean = try {
    condition()
} catch (e: RuntimeException) {
    if (generateSequence<Throwable>(e) { it.cause }.any { it.message?.contains("during measure layout") == true }) false
    else throw e
}

object TestSupport {
    val app: MoneyTrackerApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MoneyTrackerApplication

    val container get() = app.container

    fun u(x: Double) = (x * 1000).roundToLong()

    /** Clean slate in the real app database + default settings. */
    fun resetApp(theme: ThemeMode = ThemeMode.LIGHT, language: AppLanguage = AppLanguage.EN) = runBlocking {
        container.settings.setLanguage(language)
        container.finance.resetAll()
        container.settings.setCurrency("OMR", 3)
        container.settings.setTheme(theme)
    }

    /** Clears financial data only (keeps the language, so no screen rebuild during teardown). */
    fun resetData() = runBlocking { container.finance.resetAll() }

    /** Language passed with `-e lang ar|en` (defaults to English). */
    fun languageArg(): AppLanguage =
        if (InstrumentationRegistry.getArguments().getString("lang") == "ar") AppLanguage.AR else AppLanguage.EN
}

/** Runs [action] before the activity is launched (place it outside the compose rule in a RuleChain). */
class BeforeLaunch(private val action: () -> Unit) : ExternalResource() {
    override fun before() = action()
}

fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) { pollSafely { onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }
}

fun ComposeTestRule.waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) { pollSafely { onAllNodes(hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }
}

fun ComposeTestRule.waitForTextGone(text: String, timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) { pollSafely { onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() } }
}

/** Waits until a node with [tag] shows exactly [expected] (data flows are asynchronous). */
fun ComposeTestRule.waitForTagText(tag: String, expected: String, timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) {
        pollSafely { onAllNodes(hasTestTag(tag) and hasText(expected), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
}

/** Waits until no node carries [tag] (e.g. a screen finished its exit animation). */
fun ComposeTestRule.waitForTagGone(tag: String, timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) { pollSafely { onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() } }
}

/** Like [waitForTagText] but matches a substring (Arabic amounts are wrapped in bidi isolates). */
fun ComposeTestRule.waitForTagContains(tag: String, expected: String, timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) {
        pollSafely {
            onAllNodes(hasTestTag(tag) and hasText(expected, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
