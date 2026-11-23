package com.moneytracker.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.moneytracker.app.data.ThemeMode
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
    fun resetApp(theme: ThemeMode = ThemeMode.LIGHT) = runBlocking {
        container.finance.resetAll()
        container.settings.setCurrency("OMR", 3)
        container.settings.setTheme(theme)
    }
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
