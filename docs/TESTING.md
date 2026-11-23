# Testing report

Every number below comes from an actual automated run on GitHub Actions. Nothing is marked as passed unless it ran. The full raw output of the latest run (build log, JUnit XML, instrumentation output, logcat, screenshots, release APK) is on the [`ci-results`](https://github.com/DemerDash123/MoneyTrackerApp/tree/ci-results) branch.

**Reference runs:** run #6 (commit `f647850`, release **v1.0.0**) and run #10 (commit `3a67cf4`, release **v1.1.0** with Arabic), both fully green, 23–24 Nov 2026. Emulator: Android 14 (API 34, Google APIs, x86_64, Pixel 6 profile).

## Results summary

| Area | Suite | Result |
|---|---|---|
| Build | Debug, test and **minified release** APKs + Android lint (release) | ✅ pass |
| Unit (JVM) | `LedgerTest` — balance algebra and validation | ✅ 11/11 |
| Unit (JVM) | `ReportsTest` — monthly reports, trends, budgets, goals, insights, calendar | ✅ 9/9 |
| Unit (JVM) | `MoneyFormatterTest` — amount parsing/formatting | ✅ 8/8 |
| Unit (JVM) | `BackupCodecTest` — backup JSON and CSV | ✅ 4/4 |
| Unit (JVM) | `LocalizationTest` — every Arabic text translated, Arabic dates/plurals/amounts | ✅ 4/4 |
| Integration (device) | `FinanceRepositoryTest` — real Room database + repository | ✅ 9/9 |
| End-to-end UI (device) | `UiFlowTest` — real app driven through its UI | ✅ 10/10 |
| Arabic (device) | `FirstLaunchLanguageTest`, `LanguageSwitchTest` (EN→AR→EN), `ArabicFlowTest` (3 flows in RTL) | ✅ 5/5 |
| Screens (device) | `ScreenshotTour` in English (phone, small, tablet) and Arabic (phone, small) | ✅ 5/5 |
| Persistence (device) | write → force-stop app → verify | ✅ |
| Persistence (device) | write → **reboot emulator** → verify | ✅ |
| Release APK (device) | install the actual minified release APK and use it, incl. language picker and Arabic switch (20 checks) | ✅ 20/20 |

Total (v1.1.0): **36 unit tests, 27 instrumented tests, 4 persistence checks, 20 release-APK checks — all passing.**

## What was tested

### Financial operations and accuracy
Covered by `LedgerTest` (pure logic) and repeated against the real database in `FinanceRepositoryTest`:
- The specification's example: 500 OMR split into Savings 150 / Food 100 / Shopping 50 / Transportation 50 / Available 150; spending 15 from Food leaves Food at exactly 85.
- Adding 50 increases the total by 50; spending 20 decreases it by 20.
- Allocating 100 to Savings doesn't change the total; moving 30 Savings → Shopping doesn't change it either.
- Spending 10 from Savings lowers Savings and the total by 10.
- Editing a transaction (amount, source category, even its type from expense to income) recalculates every balance; deleting reverses its effect completely.
- The invariant *total = available + all category balances* holds after a mixed sequence of operations.
- No duplicates: two identical incomes produce two rows with distinct ids and the exact sum.
- Rejected operations leave the database byte-for-byte unchanged (checked by comparing every row before and after): spending more than Available, spending 1 baisa more than a category holds, over-allocating, transferring to the same place, zero and negative amounts, future dates, unknown categories, edits that would overdraw, and deleting an income that was already allocated.
- Exact baisa precision (amounts like 12.345 and 7.250 survive storage, backup and CSV unchanged).

### Categories, goals and budgets
- Create (with optional immediate allocation), rename/edit, duplicate-name and reserved-name rejection.
- Delete a category with history: remaining money returns to Available, total unchanged, history kept, category can't be used for new transactions, can be restored. Delete an empty category: removed completely.
- Through the UI: create a category from a suggestion, add money, spend, transfer between categories, set a monthly budget, exceed it and see the "over" warning, create a goal, add money to it and see the progress.

### Statistics and monthly reports
- Monthly report for October vs November with interleaved data: income, expenses, net, count, per-category added/spent/removed, balance at month end, unallocated at month end and daily totals are exact for each month.
- **Historical stability:** adding December transactions leaves the October report identical (object equality).
- Spending breakdown order, counts and percentages (sum to 100 %).
- Daily, weekly (Monday start), monthly and yearly trend buckets.
- Savings growth = balance at the end of each month.
- Budget states: OK, warning at ≥ 80 %, exceeded above 100 %.
- Goals: progress, remaining, days left and "save per month" amount.
- Insights: highest/lowest category, average daily spending for current and past months, change vs previous month in amount and %.
- Through the UI: Monthly tab shows this month's figures, the previous-month arrow shows last month's figures only, and the donut centre shows the period's spending.

### Other features
- Calendar: tapping today shows that day's transactions.
- Search ("coffee" finds only the matching record), type filter, period filter ("Last month" correctly shows nothing).
- Current money screen: add 25, subtract 5, balance shows 20.
- Theme switching (dark/light) and currency change (USD with 2 decimals changes how balances are displayed).
- Invalid input in the form: letters, zero and too many decimals each show a specific message; an overspend shows "Not enough available money" and nothing is saved.
- Backup → reset → restore through `BackupManager` with real files: identical data afterwards. A truncated backup file is rejected and existing data is untouched. An internally inconsistent backup is refused.
- CSV export: header, quoting of commas/quotes, signed amounts. PDF monthly report: valid PDF file is produced.

### Reliability
- App process killed (`am force-stop`) and data re-read in a new process: identical, no duplicates.
- **Emulator rebooted** and data re-read: identical.
- A file-based Room database closed and reopened: identical.
- All screens rendered without errors on a phone, a small phone (360×640 dp) and a tablet (800×1280 dp), in light mode, and the main screens in dark mode. Screenshots: [`docs/screenshots`](screenshots).

### Arabic language (v1.1.0)
- **Completeness:** a unit test reads every text in the string table and fails if any Arabic text is missing, still in English, or identical to the English one. The table itself is an abstract class, so a text without an Arabic version doesn't even compile.
- **Formatting:** Arabic month and day names with Western digits, Arabic plural forms (معاملة واحدة / معاملتان / 5 معاملات / 24 معاملة), amounts wrapped in bidi isolates so "−7.000 OMR" never turns around inside Arabic text, and amounts typed with Arabic-Indic digits (٢٥ = 25).
- **First launch:** with no saved choice the app shows "Choose your language · اختر لغتك"; choosing العربية switches the whole app to Arabic and right-to-left, and the picker doesn't appear again.
- **Switching both ways:** from Settings, English → العربية → English while the app runs; each time the screen is rebuilt in the new language and the layout direction is checked (RTL for Arabic, LTR for English).
- **Arabic flows through the UI:** income typed as ٥٠٠ with an Arabic description, a category created from an Arabic suggestion ("طعام"), allocation and spending (balances 500 → 485, Food 85); Arabic validation messages ("أدخل مبلغاً صحيحاً", "المال المتاح غير كافٍ…") with nothing saved; Arabic statistics, monthly report, activity and settings screens.
- **Release APK:** picker shown on first launch, switch to Arabic in Settings, Arabic dashboard shows the right balance, Arabic remembered after restart, switch back to English.
- Every screen was captured in Arabic (light and dark, phone and small phone) and reviewed: mirrored layout, navigation and arrows, right-to-left calendar, readable amounts. One issue was found and fixed (negative day totals in the calendar showed as "12-").

### Release APK (black-box)
The minified, signed `app-release.apk` was installed on a clean emulator and driven with `adb`/uiautomator only: it launches to the dashboard, an income of 250 entered through the real form appears on the dashboard, all five tabs open, the data is still there after force-stop + relaunch, and logcat contains no crash.

## What failed during development and was fixed

| Run | Problem found | Fix |
|---|---|---|
| #1 | Release lint error: invalid `<exclude>` path in backup rules | Removed the line |
| #1 | `UiFlowTest` expected 4 transactions where the flow creates 3 (category creation isn't a transaction) | Corrected the test's expectation; the app's balances were already right |
| #1 | UI tests tapped the next screen while the previous one was still animating out, sending input to the closing form | Tests now wait until the form screen has fully closed |
| #1 | First screenshot captured a stale first frame | Force a redraw before each capture |
| #2 (visual review) | Empty "Where your money goes" card didn't fill the width; axis ticks like 63/188; daily chart dominated by the salary day; "My Money" tab label cut to "My" on small phones; content stretched edge-to-edge on tablets | Full-width cards; round axis steps; spending-only daily chart; compact nav labels; 760 dp max content width |
| #3 | Flaky build: debug and release Room annotation processing wrote the same schema file in parallel ("Empty schema file") | Schema export disabled (single-version database) |
| #8–#9 | Arabic tests looked for rows that were off-screen in scrolling lists, assumed allocations store an Arabic description, and an Espresso back-press waited for window focus behind a bottom sheet | Tests corrected (all were test mistakes, not app bugs) |
| #8 (visual review) | Negative daily totals in the Arabic calendar rendered as "12-" | Calendar numbers forced left-to-right |
| #3 | Compose test harness race ("performMeasureAndLayout called during measure layout") when the DB changes while the test polls | Test polling retries on that specific harness error |

Runs #1–#3 are visible in the repository's Actions history; runs #4 and #6 are all green, and #6 produced the v1.0.0 release APK.

## Remaining limitations

- **Not tested on a physical phone**, only on the Android 14 emulator. The app has no hardware-specific code, but device-specific skins/fonts were not checked.
- Android versions 8–13 were not run in CI (minSdk is 26); only API 34.
- The system file picker (Storage Access Framework) can't be automated in instrumented tests, so backup/restore/CSV/PDF were tested through `BackupManager` with real files rather than by tapping through the picker.
- Charts are verified to render and to receive taps (tooltips), but tooltip positions are checked visually from screenshots, not asserted pixel by pixel.
- Balance validation checks the final state after each change, not every intermediate day; back-dating an expense before the allocation that funded it is allowed as long as the current balances stay non-negative.
- Release signing: CI could not store a private signing key in repository secrets from this session, so the release APK is signed with the CI runner's debug key. It installs fine, but a future release signed with a different key won't install over it (uninstall first, after making a backup). Adding the four `RELEASE_*` secrets described in the README fixes this permanently.
