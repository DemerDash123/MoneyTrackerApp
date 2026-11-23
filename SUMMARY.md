# CI summary

Gradle build + unit tests exit code: `0`

## JVM unit tests


| Suite | Tests | Failed |
|---|---:|---:|
| BackupCodecTest | 4 | 0 |
| LedgerTest | 11 | 0 |
| LocalizationTest | 4 | 0 |
| MoneyFormatterTest | 8 | 0 |
| ReportsTest | 9 | 0 |

**36/36 passed**

## Device tests (Android emulator)

| Suite | Result | Detail |
|---|---|---|
| repository | PASS | 9 passed, 0 failed |
| ui_flows | FAIL | 9 passed, 1 failed |
| language_first_launch | PASS | 1 passed, 0 failed |
| language_switch | PASS | 1 passed, 0 failed |
| arabic_flows | PASS | 3 passed, 0 failed |
| screens_phone | PASS | 1 passed, 0 failed |
| screens_phone_ar | PASS | 1 passed, 0 failed |
| screens_small | PASS | 1 passed, 0 failed |
| screens_small_ar | PASS | 1 passed, 0 failed |
| screens_tablet | PASS | 1 passed, 0 failed |
| persist_write | PASS | 1 passed, 0 failed |
| persist_after_force_stop | PASS | 1 passed, 0 failed |
| persist_after_reboot | PASS | 1 passed, 0 failed |
| release_apk_smoke | PASS | 20 passed, 0 failed |

<details><summary>android-version.txt</summary>

```
14

```
</details>

<details><summary>arabic_flows.txt</summary>

```
PASS   ArabicFlowTest#arabicStatisticsAndSettingsRender
PASS   ArabicFlowTest#incomeAllocateSpendInArabic
PASS   ArabicFlowTest#arabicValidationMessages
3 passed, 0 failed

```
</details>

<details><summary>boot_after_reboot.txt</summary>

```
1

```
</details>

<details><summary>install.txt</summary>

```
Performing Streamed Install
Success
Performing Streamed Install
Success

```
</details>

<details><summary>language_first_launch.txt</summary>

```
PASS   FirstLaunchLanguageTest#pickerAppearsAndChoosingArabicApplies
1 passed, 0 failed

```
</details>

<details><summary>language_switch.txt</summary>

```
PASS   LanguageSwitchTest#switchEnglishToArabicAndBack
1 passed, 0 failed

```
</details>

<details><summary>persist_after_force_stop.txt</summary>

```
PASS   PersistenceVerifyTest#dataIsStillThere
1 passed, 0 failed

```
</details>

<details><summary>persist_after_reboot.txt</summary>

```
PASS   PersistenceVerifyTest#dataIsStillThere
1 passed, 0 failed

```
</details>

<details><summary>persist_write.txt</summary>

```
PASS   PersistenceWriteTest#writeKnownData
1 passed, 0 failed

```
</details>

<details><summary>release-install.txt</summary>

```
Performing Streamed Install
Success

```
</details>

<details><summary>release_smoke.txt</summary>

```
PASS first launch shows the language picker (English / Arabic)
PASS release app launches and shows the dashboard
PASS quick 'Income' action found
PASS add transaction screen opens
PASS save button found
PASS income saved through release build (dashboard shows 250.000 OMR)
PASS tab 'Activity' opens and app is alive
PASS tab 'My Money' opens and app is alive
PASS tab 'Statistics' opens and app is alive
PASS tab 'Settings' opens and app is alive
PASS tab 'Home' opens and app is alive
PASS data still there after force-stop + relaunch
PASS language picker not shown again
PASS Arabic option present in Settings
PASS interface switched to Arabic (الإعدادات / الرئيسية)
PASS Arabic dashboard shows the balance
PASS Arabic is remembered after restart
PASS switched back to English
PASS still alive after language switches
PASS no crash recorded in logcat
20 passed, 0 failed

```
</details>

<details><summary>repository.txt</summary>

```
PASS   FinanceRepositoryTest#editRecalculatesAndDeleteReverses
PASS   FinanceRepositoryTest#backupRoundTripAndInvalidRestoreKeepsData
PASS   FinanceRepositoryTest#fileDatabaseSurvivesReopen
PASS   FinanceRepositoryTest#categoryLifecycle
PASS   FinanceRepositoryTest#noDuplicates
PASS   FinanceRepositoryTest#deletingAllocatedIncomeIsRejected
PASS   FinanceRepositoryTest#specScenario
PASS   FinanceRepositoryTest#backupManagerFilesCsvAndPdf
PASS   FinanceRepositoryTest#rejectsInvalidOperationsAtomically
9 passed, 0 failed

```
</details>

<details><summary>screen.txt</summary>

```
Physical size: 1080x2400

```
</details>

<details><summary>screens_phone.txt</summary>

```
PASS   ScreenshotTour#tour
1 passed, 0 failed

```
</details>

<details><summary>screens_phone_ar.txt</summary>

```
PASS   ScreenshotTour#tour
1 passed, 0 failed

```
</details>

<details><summary>screens_small.txt</summary>

```
PASS   ScreenshotTour#tour
1 passed, 0 failed

```
</details>

<details><summary>screens_small_ar.txt</summary>

```
PASS   ScreenshotTour#tour
1 passed, 0 failed

```
</details>

<details><summary>screens_tablet.txt</summary>

```
PASS   ScreenshotTour#tour
1 passed, 0 failed

```
</details>

<details><summary>ui_flows.txt</summary>

```
PASS   UiFlowTest#calendarShowsTransactionsOfSelectedDay
PASS   UiFlowTest#monthlyReportShowsTheSelectedMonthOnly
FAIL   UiFlowTest#searchAndFilterTransactions
       java.lang.IllegalArgumentException: performMeasureAndLayout called during measure layout
       	at androidx.compose.ui.internal.InlineClassHelperKt.throwIllegalArgumentException(InlineClassHelper.kt:34)
       	at androidx.compose.ui.node.MeasureAndLayoutDelegate.measureAndLayout(MeasureAndLayoutDelegate.kt:779)
       	at androidx.compose.ui.platform.AndroidComposeView.measureAndLayout(AndroidComposeView.android.kt:1268)
       	at androidx.compose.ui.node.Owner.measureAndLayout$default(Owner.kt:248)
       	at androidx.compose.ui.platform.AndroidComposeView.measureAndLayoutForTest(AndroidComposeView.android.kt:1343)
       	at androidx.compose.ui.test.AndroidComposeUiTestEnvironment$1.invoke(ComposeUiTest.android.kt:253)
       	at androidx.compose.ui.test.AndroidComposeUiTestEnvironment$1.invoke(ComposeUiTest.android.kt:251)
       	at androidx.compose.ui.test.TestMonotonicFrameClock$performFrame$1.invoke(TestMonotonicFrameClock.jvm.kt:152)
       	at androidx.compose.ui.test.TestMonotonicFrameClock$performFrame$1.invoke(TestMonotonicFrameClock.jvm.kt:132)
       	at androidx.compose.ui.test.FrameDeferringContinuationInterceptor.runWithoutResumingCoroutines(FrameDeferringContinuationInterceptor.jvm.kt:60)
       	at androidx.compose.ui.test.TestMonotonicFrameClock.performFrame(TestMonotonicFrameClock.jvm.kt:132)
       	at androidx.compose.ui.test.TestMonotonicFrameClock.access$performFrame(TestMonotonicFrameClock.jvm.kt:53)
       	at androidx.compose.ui.test.TestMonotonicFrameClock$withFrameNanos$2$1$2.invokeSuspend(TestMonotonicFrameClock.jvm.kt:110)
       	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
       	at kotlinx.coroutines.internal.DispatchedContinuationKt.resumeCancellableWith(DispatchedContinuation.kt:359)
       	at kotlinx.coroutines.intrinsics.CancellableKt.startCoroutineCancellable(Cancellable.kt:26)
       	at kotlinx.coroutines.CoroutineStart.invoke(CoroutineStart.kt:358)
       	at kotlinx.coroutines.AbstractCoroutine.start(AbstractCoroutine.kt:124)
       	at kotlinx.coroutines.BuildersKt__Builders_commonKt.launch(Builders.common.kt:52)
       	at kotlinx.coroutines.BuildersKt.launch(Unknown Source:1)
       	at kotlinx.coroutines.BuildersKt__Builders_commonKt.launch$default(Builders.common.kt:43)
       	at kotlinx.coroutines.BuildersKt.launch$default(Unknown Source:1)
       	at androidx.compose.ui.test.TestMonotonicFrameClock.withFrameNanos(TestMonotonicFrameClock.jvm.kt:108)
       	at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.invokeSuspend(Recomposer.kt:585)
PASS   UiFlowTest#transferBudgetAndGoalFlows
PASS   UiFlowTest#currentMoneyAddAndSubtract
PASS   UiFlowTest#addIncomeAllocateAndSpendThroughUi
PASS   UiFlowTest#deletingCategoryReturnsMoneyAndKeepsHistory
PASS   UiFlowTest#themeAndCurrencySettings
PASS   UiFlowTest#editAndDeleteTransactionFromHistory
PASS   UiFlowTest#invalidInputsAreRejectedAndNothingIsSaved
9 passed, 1 failed

```
</details>

## Screenshots (77)

- device/screens/files/screenshots/FAILED_searchAndFilterTransactions.png
- device/screens/files/screenshots/phone_01_home.png
- device/screens/files/screenshots/phone_02_home_spending.png
- device/screens/files/screenshots/phone_03_add_expense.png
- device/screens/files/screenshots/phone_04_transactions.png
- device/screens/files/screenshots/phone_05_transaction_detail.png
- device/screens/files/screenshots/phone_06_calendar.png
- device/screens/files/screenshots/phone_07_my_money.png
- device/screens/files/screenshots/phone_08_category_detail.png
- device/screens/files/screenshots/phone_09_goals.png
- device/screens/files/screenshots/phone_10_budgets.png
- device/screens/files/screenshots/phone_11_statistics.png
- device/screens/files/screenshots/phone_12_statistics_charts.png
- device/screens/files/screenshots/phone_13_insights.png
- device/screens/files/screenshots/phone_14_monthly.png
- device/screens/files/screenshots/phone_15_monthly_categories.png
- device/screens/files/screenshots/phone_16_settings.png
- device/screens/files/screenshots/phone_17_current_money.png
- device/screens/files/screenshots/phone_18_home_dark.png
- device/screens/files/screenshots/phone_19_statistics_dark.png
- device/screens/files/screenshots/phone_20_my_money_dark.png
- device/screens/files/screenshots/phone_ar_01_home.png
- device/screens/files/screenshots/phone_ar_02_home_spending.png
- device/screens/files/screenshots/phone_ar_03_add_expense.png
- device/screens/files/screenshots/phone_ar_04_transactions.png
- device/screens/files/screenshots/phone_ar_05_transaction_detail.png
- device/screens/files/screenshots/phone_ar_06_calendar.png
- device/screens/files/screenshots/phone_ar_07_my_money.png
- device/screens/files/screenshots/phone_ar_08_category_detail.png
- device/screens/files/screenshots/phone_ar_09_goals.png
- device/screens/files/screenshots/phone_ar_10_budgets.png
- device/screens/files/screenshots/phone_ar_11_statistics.png
- device/screens/files/screenshots/phone_ar_12_statistics_charts.png
- device/screens/files/screenshots/phone_ar_13_insights.png
- device/screens/files/screenshots/phone_ar_14_monthly.png
- device/screens/files/screenshots/phone_ar_15_monthly_categories.png
- device/screens/files/screenshots/phone_ar_16_settings.png
- device/screens/files/screenshots/phone_ar_17_current_money.png
- device/screens/files/screenshots/phone_ar_18_home_dark.png
- device/screens/files/screenshots/phone_ar_19_statistics_dark.png
- device/screens/files/screenshots/phone_ar_20_my_money_dark.png
- device/screens/files/screenshots/small_01_home.png
- device/screens/files/screenshots/small_02_home_spending.png
- device/screens/files/screenshots/small_03_add_expense.png
- device/screens/files/screenshots/small_04_transactions.png
- device/screens/files/screenshots/small_07_my_money.png
- device/screens/files/screenshots/small_09_goals.png
- device/screens/files/screenshots/small_10_budgets.png
- device/screens/files/screenshots/small_11_statistics.png
- device/screens/files/screenshots/small_12_statistics_charts.png
- device/screens/files/screenshots/small_14_monthly.png
- device/screens/files/screenshots/small_15_monthly_categories.png
- device/screens/files/screenshots/small_16_settings.png
- device/screens/files/screenshots/small_ar_01_home.png
- device/screens/files/screenshots/small_ar_02_home_spending.png
- device/screens/files/screenshots/small_ar_03_add_expense.png
- device/screens/files/screenshots/small_ar_04_transactions.png
- device/screens/files/screenshots/small_ar_07_my_money.png
- device/screens/files/screenshots/small_ar_09_goals.png
- device/screens/files/screenshots/small_ar_10_budgets.png
- device/screens/files/screenshots/small_ar_11_statistics.png
- device/screens/files/screenshots/small_ar_12_statistics_charts.png
- device/screens/files/screenshots/small_ar_14_monthly.png
- device/screens/files/screenshots/small_ar_15_monthly_categories.png
- device/screens/files/screenshots/small_ar_16_settings.png
- device/screens/files/screenshots/tablet_01_home.png
- device/screens/files/screenshots/tablet_02_home_spending.png
- device/screens/files/screenshots/tablet_03_add_expense.png
- device/screens/files/screenshots/tablet_04_transactions.png
- device/screens/files/screenshots/tablet_07_my_money.png
- device/screens/files/screenshots/tablet_09_goals.png
- device/screens/files/screenshots/tablet_10_budgets.png
- device/screens/files/screenshots/tablet_11_statistics.png
- device/screens/files/screenshots/tablet_12_statistics_charts.png
- device/screens/files/screenshots/tablet_14_monthly.png
- device/screens/files/screenshots/tablet_15_monthly_categories.png
- device/screens/files/screenshots/tablet_16_settings.png
