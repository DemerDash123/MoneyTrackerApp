# Money Tracker (MoneyTrackerApp)

A native Android app for managing personal money: see where every amount goes, organise money into your own categories and goals, control monthly budgets, and understand your finances month by month.

Built with **Kotlin, Jetpack Compose (Material 3) and Room**. Fully offline, no permissions, no ads, no accounts.

**Languages:** English and **Arabic (العربية)** with a full right-to-left interface. Pick a language on first launch or any time in Settings → Language.

[![Android CI](https://github.com/DemerDash123/MoneyTrackerApp/actions/workflows/android.yml/badge.svg)](https://github.com/DemerDash123/MoneyTrackerApp/actions/workflows/android.yml)

**Download:** get the latest APK from [Releases](https://github.com/DemerDash123/MoneyTrackerApp/releases/latest) (Android 8.0 or newer).

## Screenshots

All screenshots are taken automatically by the test suite on an Android 14 emulator, from the real app, with test data entered through the app's own repository layer (the app itself starts empty).

| Dashboard | Spending | Add expense | Transactions |
|---|---|---|---|
| ![](docs/screenshots/phone_01_home.png) | ![](docs/screenshots/phone_02_home_spending.png) | ![](docs/screenshots/phone_03_add_expense.png) | ![](docs/screenshots/phone_04_transactions.png) |

| My Money | Category | Goals | Budgets |
|---|---|---|---|
| ![](docs/screenshots/phone_07_my_money.png) | ![](docs/screenshots/phone_08_category_detail.png) | ![](docs/screenshots/phone_09_goals.png) | ![](docs/screenshots/phone_10_budgets.png) |

| Statistics | Charts | Insights | Monthly report |
|---|---|---|---|
| ![](docs/screenshots/phone_11_statistics.png) | ![](docs/screenshots/phone_12_statistics_charts.png) | ![](docs/screenshots/phone_13_insights.png) | ![](docs/screenshots/phone_14_monthly.png) |

| Monthly categories | Calendar | Transaction details | Settings |
|---|---|---|---|
| ![](docs/screenshots/phone_15_monthly_categories.png) | ![](docs/screenshots/phone_06_calendar.png) | ![](docs/screenshots/phone_05_transaction_detail.png) | ![](docs/screenshots/phone_16_settings.png) |

| Dark: dashboard | Dark: statistics | Dark: My Money | Current money |
|---|---|---|---|
| ![](docs/screenshots/phone_18_home_dark.png) | ![](docs/screenshots/phone_19_statistics_dark.png) | ![](docs/screenshots/phone_20_my_money_dark.png) | ![](docs/screenshots/phone_17_current_money.png) |

### Arabic (right-to-left)

| الرئيسية | المعاملات | أموالي | الإحصائيات |
|---|---|---|---|
| ![](docs/screenshots/phone_ar_01_home.png) | ![](docs/screenshots/phone_ar_04_transactions.png) | ![](docs/screenshots/phone_ar_07_my_money.png) | ![](docs/screenshots/phone_ar_11_statistics.png) |

| التقرير الشهري | التقويم | الإعدادات | الوضع الداكن |
|---|---|---|---|
| ![](docs/screenshots/phone_ar_14_monthly.png) | ![](docs/screenshots/phone_ar_06_calendar.png) | ![](docs/screenshots/phone_ar_16_settings.png) | ![](docs/screenshots/phone_ar_18_home_dark.png) |

More (all Arabic screens, small phone and tablet layouts) are in [`docs/screenshots`](docs/screenshots).

## Features

**Dashboard (Home)**
- Total balance, available (unallocated) money and allocated money, with an allocation bar
- This month's income, expenses and remaining money, plus all-time totals
- Quick actions: Income, Expense, Transfer, Allocate
- Budget alerts, "where your money is" overview of every category, this month's spending donut
- Recent transactions; everything updates instantly after any change

**My Money: organise money your way**
- Unlimited custom categories / funds, each with its own name, icon (45 choices), colour, optional monthly spending limit and optional savings target + date
- Per category: current balance, total added, total spent, moved out, remaining, this month's figures and the full history
- Add money (allocate from Available), spend, remove money back to Available, transfer to another category, edit, delete
- One-tap starter suggestions (Savings, Food, Shopping, Transportation…), all optional

**Current money**
- Add money (salary, freelancing, gifts, selling something…) to Available or straight into a category
- Subtract money from Available or from a category
- Every record has amount, description/source, date & time, destination/category and notes, and can be edited or deleted later

**Transactions**
- Income, expenses and transfers in one history, grouped by day with daily totals
- Search (description, notes, category, amount), filter by type, category and period (today, week, month, last month, year, custom range)
- Details sheet with edit and delete

**Statistics**
- Interactive donut of where money goes (tap a slice or row to see amount, % and its transactions)
- Income vs expenses for the last 6 months with the difference
- Spending trends: daily, weekly, monthly, yearly
- Savings: total saved, balance at the end of each month (growth line), goal progress
- Insights: highest and lowest spending category, average daily spending, monthly savings, change vs last month, budgets exceeded or close to the limit
- Period selector (week, month, year, all time) with previous/next navigation. Every number comes from your data.

**Monthly tracking**
- Pick any month (arrows or month grid)
- Income, expenses, net savings, number of transactions, comparison with the previous month
- Daily spending chart, per-category table (added, spent, budget usage, balance at month end)
- Last-6-months comparison and any category's spending across months
- Export the month as a **PDF report** or **CSV**

**Goals and budgets**
- Goals with target amount, optional target date, progress ring, remaining amount, days left and "save X per month" guidance; add money in one tap
- Monthly budgets per category with progress bars, a warning at 80 % and an alert when exceeded

**Calendar**
- Month grid with markers for money in, money out and transfers, plus each day's net amount
- Tap a day to see its totals and transactions

**Data**
- Stored locally with Room (SQLite), survives closing the app and restarting the phone
- Backup to a JSON file and restore (the file is fully validated before anything is replaced)
- Export all transactions to CSV (opens in Excel / Google Sheets)
- Safe reset (type RESET to confirm); deleted categories can be restored
- Currency is configurable (code/symbol and 0, 2 or 3 decimal places; OMR with 3 by default)

**Languages**
- English and Arabic, chosen on first launch and switchable any time in Settings
- Arabic uses a complete right-to-left layout (mirrored navigation, arrows, calendar), Arabic month/day names and Arabic plural forms
- Amounts stay readable inside Arabic text; amounts can be typed with Arabic-Indic digits (٠١٢٣…)
- Error messages, CSV export and the PDF monthly report (laid out right-to-left) are translated too
- Every text is in one compile-checked string table, so a missing translation can't slip through

**Design**
- Material 3, custom teal/indigo palette, light, dark and system themes
- Custom interactive charts with animations and tooltips
- Bottom navigation: Home, Activity, My Money, Statistics, Settings

## How the money logic works

Correctness comes from one rule: **balances are never stored, they are always calculated from the transaction ledger.**

| Transaction | Effect |
|---|---|
| Income | Total + amount, into Available or straight into a category |
| Expense | Total − amount, out of Available or a category |
| Transfer | Moves money between Available and categories, or between categories. **Total never changes.** |

- *Allocating* 100 OMR to Savings is a transfer Available → Savings: the total stays the same.
- Spending 10 OMR from Savings is an expense from Savings: both Savings and the total drop by 10.
- Editing a transaction changes the ledger entry, so every affected balance is recalculated automatically; deleting removes its effect completely. Nothing can be counted twice.
- Amounts are stored as whole numbers of **milli-units** (1/1000 of the currency, i.e. baisa for OMR), so there are no rounding errors.
- Every change runs in a single SQLite transaction. Before committing, the entire ledger is re-checked: if Available or any category would become negative, the change is rolled back and you get a clear message ("Not enough money in Food…").
- Deleting a category that has history doesn't break past reports: its remaining balance goes back to Available (recorded as a transfer) and the category is archived with its transactions kept.
- Monthly reports filter by date and calculate "balance at month end" from the ledger as it stood then, so past months never change when you move on to a new month.

## Architecture

```
app/src/main/java/com/moneytracker/app
├── core/          Money (integer milli-units, parsing/formatting), Dates
├── data/          Room entities & DAOs, FinanceRepository (atomic, validated),
│                  SettingsRepository, BackupCodec (JSON/CSV), BackupManager (files, PDF)
├── domain/        Ledger (balances), Validation, Reports (summaries, trends,
│                  monthly reports, budgets, goals, insights) — pure Kotlin
└── ui/            Compose screens + ViewModels (MVVM, StateFlow), theme, charts
```

- **MVVM**: each screen has a ViewModel that turns the repository's `Flow` of data into immutable UI state on a background dispatcher.
- **Room** with foreign keys (`RESTRICT`) between transactions and categories, indices on dates and categories.
- **Charts** are drawn with Compose `Canvas` (donut, grouped bars, line) — no third-party chart library, so they follow the theme exactly and support tap tooltips.
- No network, analytics or permissions. Files are written through the system file picker.

## Build from source

Requirements: Android Studio (Ladybug or newer) or the Android SDK command-line tools, JDK 17.

```bash
git clone https://github.com/DemerDash123/MoneyTrackerApp.git
cd MoneyTrackerApp
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew connectedDebugAndroidTest   # device tests (emulator or phone connected)
./gradlew assembleRelease        # minified release APK
```

Versions: AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, Room 2.6.1, compileSdk/targetSdk 35, minSdk 26.

Release signing: CI signs the release with the key in the repository secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Without them it falls back to the debug key.

## Install on your phone

1. Download `MoneyTracker-v*.apk` from [Releases](https://github.com/DemerDash123/MoneyTrackerApp/releases/latest).
2. Open it; when Android asks, allow your browser or Files app to install unknown apps.
3. Open **Money Tracker**, add your first income, then create categories in **My Money**.

## Testing

Every push runs the full suite on GitHub Actions: JVM unit tests, Room integration tests, end-to-end UI tests, a screenshot tour on three screen sizes, persistence checks across app kill and emulator reboot, and a black-box smoke test of the minified release APK. See **[docs/TESTING.md](docs/TESTING.md)** for exactly what was tested and the results.

## License

MIT
