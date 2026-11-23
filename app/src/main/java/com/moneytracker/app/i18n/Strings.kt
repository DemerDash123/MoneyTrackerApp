package com.moneytracker.app.i18n

import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.domain.Granularity
import java.util.Locale

enum class AppLanguage(val tag: String, val nativeName: String) {
    EN("en", "English"),
    AR("ar", "العربية"),
}

/** Current UI language. Set at startup and whenever the user changes it (the activity is then recreated). */
object I18n {
    @Volatile
    var language: AppLanguage = AppLanguage.EN

    val strings: Strings get() = if (language == AppLanguage.AR) ArStrings else EnStrings
    val locale: Locale get() = Locale.forLanguageTag(language.tag)
    val isRtl: Boolean get() = language == AppLanguage.AR

    fun of(language: AppLanguage): Strings = if (language == AppLanguage.AR) ArStrings else EnStrings
}

/** Shorthand used everywhere: `S.save`, `S.notEnoughIn(name, amount)`. */
val S: Strings get() = I18n.strings

/**
 * Every piece of text in the app. Both languages must implement every member,
 * so a missing translation is a compile error, not a runtime surprise.
 */
abstract class Strings {
    // General
    abstract val appName: String
    abstract val navHome: String
    abstract val navActivity: String
    abstract val navMoney: String
    abstract val navStats: String
    abstract val navSettings: String
    abstract val available: String
    abstract val unknown: String
    abstract fun deletedName(name: String): String
    abstract val income: String
    abstract val expense: String
    abstract val expenses: String
    abstract val transfer: String
    abstract val transfers: String
    abstract val allocate: String
    abstract fun allocatedTo(name: String): String
    abstract val movedToAvailable: String
    abstract val arrow: String
    abstract fun incomeTo(place: String): String
    abstract fun expenseFrom(place: String): String
    abstract fun typeName(type: TxType): String
    abstract val previousMonth: String
    abstract val nextMonth: String
    abstract val previousYear: String
    abstract val nextYear: String
    abstract val cancel: String
    abstract val ok: String
    abstract val back: String
    abstract val save: String
    abstract val saveChanges: String
    abstract val delete: String
    abstract val edit: String
    abstract val create: String
    abstract val remove: String
    abstract val restore: String
    abstract val apply: String
    abstract val add: String
    abstract val all: String
    abstract val today: String
    abstract val yesterday: String
    abstract fun transactionsCount(n: Int): String
    abstract fun categoriesCount(n: Int): String
    abstract fun days(n: Long): String

    // Home
    abstract val goodMorning: String
    abstract val goodAfternoon: String
    abstract val goodEvening: String
    abstract val calendar: String
    abstract val noTransactionsYet: String
    abstract val addFirstIncome: String
    abstract val addIncome: String
    abstract val totalBalance: String
    abstract val allocated: String
    abstract val thisMonth: String
    abstract val report: String
    abstract val remainingThisMonth: String
    abstract fun incomeSpentPct(pct: Int): String
    abstract val totalIncomeAllTime: String
    abstract val totalExpensesAllTime: String
    abstract val budgetAlerts: String
    abstract fun overBudgetBy(amount: String): String
    abstract fun usedLeft(pct: Int, left: String): String
    abstract val whereMoneyIs: String
    abstract val createCategoriesHint: String
    abstract val spendingThisMonth: String
    abstract val noSpendingThisMonth: String
    abstract val spent: String
    abstract val recentTransactions: String
    abstract val seeAll: String

    // Add / edit transaction
    abstract val txNotExists: String
    abstract val enterAmount: String
    abstract val chooseTwoPlaces: String
    abstract val editTransaction: String
    abstract val newTransaction: String
    abstract val deleteTransaction: String
    abstract val amount: String
    abstract val addTo: String
    abstract val payFrom: String
    abstract val from: String
    abstract val to: String
    abstract val incomeSuggestions: List<String>
    abstract val swapFromTo: String
    abstract val sourceDescription: String
    abstract val description: String
    abstract val notesOptional: String
    abstract val deleteTxTitle: String
    abstract val deleteTxMessage: String
    abstract val tipCreateCategories: String
    abstract val notEnoughSuffix: String

    // Transactions
    abstract val periodAllTime: String
    abstract val periodThisWeek: String
    abstract val periodLastMonth: String
    abstract val periodThisYear: String
    abstract val periodCustom: String
    abstract val transactionDeleted: String
    abstract val transactions: String
    abstract val calendarView: String
    abstract val searchHint: String
    abstract val clearSearch: String
    abstract val nothingMatches: String
    abstract val tapAddToRecord: String
    abstract val tryAnotherSearch: String
    abstract val clearFilters: String
    abstract val allCategories: String
    abstract val category: String
    abstract val moneyIn: String
    abstract val moneyOut: String
    abstract val dateLabel: String
    abstract fun dateAt(date: String, time: String): String
    abstract val addedTo: String
    abstract val paidFrom: String
    abstract val notes: String

    // Calendar
    abstract val net: String
    abstract val moneyInLegend: String
    abstract val moneyOutLegend: String
    abstract val received: String
    abstract val noTxOnDay: String

    // Current money
    abstract val currentMoney: String
    abstract val inCategories: String
    abstract val addMoney: String
    abstract val subtract: String
    abstract val currentMoneyHint: String
    abstract val nothingHereYet: String
    abstract val incomeExpensesAppearHere: String

    // Category detail
    abstract val categoryDeleted: String
    abstract fun categoryDeletedReturned(amount: String): String
    abstract val categoryDeletedHistoryKept: String
    abstract val editCategory: String
    abstract val deleteCategory: String
    abstract val categoryNotExists: String
    abstract val deletedCategory: String
    abstract val currentBalance: String
    abstract val totalAdded: String
    abstract val totalSpent: String
    abstract val movedOut: String
    abstract val remaining: String
    abstract val spend: String
    abstract val added: String
    abstract fun leftOfBudget(left: String, limit: String): String
    abstract fun goalProgress(saved: String, target: String): String
    abstract fun byDate(date: String): String
    abstract fun history(n: Int): String
    abstract val noActivityYet: String
    abstract fun deleteCategoryTitle(name: String): String
    abstract val categoryNoTxRemoved: String
    abstract val pastTxStay: String
    abstract fun remainingReturned(amount: String): String

    // My Money
    abstract fun categoryCreated(name: String): String
    abstract val budgetRemoved: String
    abstract val budgetSaved: String
    abstract val newCategory: String
    abstract val newGoal: String
    abstract val setBudget: String
    abstract val tabCategories: String
    abstract val tabGoals: String
    abstract val tabBudgets: String
    abstract val budgetForWhich: String
    abstract val createCategoryFirst: String
    abstract val total: String
    abstract val allocatingHint: String
    abstract val notAssigned: String
    abstract val startWithCategories: String
    abstract val tapSuggestion: String
    abstract fun spentThisMonth(amount: String): String
    abstract fun ofMonthlyBudget(spent: String, limit: String): String
    abstract val noGoalsYet: String
    abstract val goalsHint: String
    abstract fun xOfY(a: String, b: String): String
    abstract val goalReached: String
    abstract fun toGo(amount: String): String
    abstract fun targetDate(date: String): String
    abstract fun daysLeft(n: Long): String
    abstract fun daysOverdue(n: Long): String
    abstract fun savePerMonth(amount: String): String
    abstract fun monthlyLimitsFor(month: String): String
    abstract val noBudgets: String
    abstract val budgetsHint: String
    abstract val budgetWarning: String
    abstract fun amountSpent(amount: String): String
    abstract fun amountOver(amount: String): String
    abstract fun leftOf(left: String, limit: String): String
    abstract fun monthlyBudgetFor(name: String): String
    abstract val limitPerMonth: String
    abstract val suggestionNames: List<String>

    // Category edit
    abstract val monthlyLimit: String
    abstract val target: String
    abstract val startingAmount: String
    abstract val enterTargetForGoal: String
    abstract val goalNameHint: String
    abstract val name: String
    abstract val icon: String
    abstract val color: String
    abstract val targetAmount: String
    abstract val savingsTargetOptional: String
    abstract fun targetDateColon(date: String): String
    abstract val addTargetDate: String
    abstract val clearDate: String
    abstract val monthlyLimitOptional: String
    abstract val allocateNowOptional: String
    abstract fun availableColon(amount: String): String

    // Settings
    abstract val enterCurrency: String
    abstract val currencyUpdated: String
    abstract fun somethingWrong(msg: String): String
    abstract val backupSaved: String
    abstract val backupRestored: String
    abstract val csvExported: String
    abstract val categoryRestored: String
    abstract val allDataDeleted: String
    abstract val appearance: String
    abstract val themeSystem: String
    abstract val themeLight: String
    abstract val themeDark: String
    abstract val language: String
    abstract val languageHint: String
    abstract val currency: String
    abstract fun currencySummary(code: String, decimals: Int): String
    abstract val yourData: String
    abstract fun dataSummary(tx: Int, cats: Int): String
    abstract val backUp: String
    abstract val backUpSub: String
    abstract val restoreSub: String
    abstract val exportCsv: String
    abstract val exportCsvSub: String
    abstract val monthlyPdf: String
    abstract val monthlyPdfSub: String
    abstract val deletedCategories: String
    abstract val deletedCategoriesSub: String
    abstract val resetAllData: String
    abstract val resetSub: String
    abstract fun aboutLine(version: String): String
    abstract val restoreTitle: String
    abstract val restoreMessage: String
    abstract val currencyCodeHint: String
    abstract val decimalPlaces: String
    abstract val decimalsHint: String
    abstract val deleteAllTitle: String
    abstract val deleteAllMessage: String
    abstract val deleteEverything: String

    // Statistics
    abstract val periodWeek: String
    abstract val periodMonth: String
    abstract val periodYear: String
    abstract fun reportSaved(month: String): String
    abstract fun couldntSaveReport(msg: String): String
    abstract fun monthExported(month: String): String
    abstract fun couldntExport(msg: String): String
    abstract val overview: String
    abstract val monthly: String
    abstract val previousPeriod: String
    abstract val nextPeriod: String
    abstract val difference: String
    abstract val incomeVsExpenses: String
    abstract val last6Hint: String
    abstract val spendingTrends: String
    abstract fun granularity(g: Granularity): String
    abstract fun averagePer(amount: String, g: Granularity): String
    abstract val savings: String
    abstract val totalSavedNow: String
    abstract val savedThisMonth: String
    abstract val balanceEndEachMonth: String
    abstract val balance: String
    abstract val whereMoneyGoes: String
    abstract val tapSliceHint: String
    abstract val noExpensesPeriod: String
    abstract val totalSpentLabel: String
    abstract fun more(n: Int): String
    abstract fun insightsFor(month: String): String
    abstract val highestCategory: String
    abstract val noCategorySpending: String
    abstract val lowestCategory: String
    abstract val needsTwoCategories: String
    abstract val averageDaily: String
    abstract val savedThisMonthFull: String
    abstract val comparedPrevious: String
    abstract val noSpendingEither: String
    abstract val spentSame: String
    abstract fun spentMoreLess(amount: String, more: Boolean, pct: String?): String
    abstract val budgetsExceeded: String
    abstract val noneWellDone: String
    abstract val closeToLimit: String
    abstract val netSavings: String
    abstract fun vsMonth(month: String): String
    abstract val balanceMonthEnd: String
    abstract val unallocatedMonthEnd: String
    abstract val dailySpending: String
    abstract fun moneyReceivedDays(n: Int): String
    abstract val last6Months: String
    abstract val categoryAcrossMonths: String
    abstract val pdfReport: String
    abstract val noChange: String
    abstract val categoriesThisMonth: String
    abstract val categoriesTableHint: String
    abstract fun budgetUsed(limit: String, pct: Int): String
    abstract fun amountsIn(currency: String): String

    // Amount input
    abstract val amountGtZero: String
    abstract val invalidAmount: String
    abstract val wholeOnly: String
    abstract fun maxDecimals(n: Int): String
    abstract val amountTooLarge: String

    // Repository / validation
    abstract val futureDate: String
    abstract val descTooLong: String
    abstract val noteTooLong: String
    abstract fun categoryWasDeleted(name: String): String
    abstract val enterName: String
    abstract val nameTooLong: String
    abstract val availableReserved: String
    abstract fun categoryExists(name: String): String
    abstract val limitGtZero: String
    abstract val targetGtZero: String
    abstract fun returnedFrom(name: String): String
    abstract fun activeExists(name: String): String
    abstract fun backupInconsistent(msg: String): String
    abstract fun notEnoughAvailable(amount: String): String
    abstract fun notEnoughIn(name: String, amount: String): String

    // Backup / export
    abstract val notBackupFile: String
    abstract fun unsupportedVersion(v: Int): String
    abstract val backupDuplicateCategories: String
    abstract val backupInvalidCategoryIds: String
    abstract val backupUnknownType: String
    abstract val backupInvalidAmount: String
    abstract val backupMissingCategory: String
    abstract val backupInvalidTransfer: String
    abstract val backupDuplicateTransactions: String
    abstract val backupDamaged: String
    abstract val cantOpenFile: String
    abstract val cantWriteFile: String
    abstract val csvHeader: List<String>
    abstract fun pdfTitle(month: String): String
    abstract fun pdfGenerated(date: String): String
    abstract val summary: String
    abstract val totalIncome: String
    abstract val totalExpenses: String
    abstract val prevIncome: String
    abstract val prevExpenses: String
    abstract val budget: String
    abstract val balanceAtEnd: String
    abstract val availableUnallocated: String
    abstract val deletedWord: String
    abstract val spendingByCategory: String
    abstract val noExpensesThisMonth: String

    // Language picker
    abstract val chooseLanguageTitle: String
}

object EnStrings : Strings() {
    override val appName = "Money Tracker"
    override val navHome = "Home"
    override val navActivity = "Activity"
    override val navMoney = "My Money"
    override val navStats = "Statistics"
    override val navSettings = "Settings"
    override val available = "Available"
    override val unknown = "Unknown"
    override fun deletedName(name: String) = "$name (deleted)"
    override val income = "Income"
    override val expense = "Expense"
    override val expenses = "Expenses"
    override val transfer = "Transfer"
    override val transfers = "Transfers"
    override val allocate = "Allocate"
    override fun allocatedTo(name: String) = "Allocated to $name"
    override val movedToAvailable = "Moved to Available"
    override val arrow = "→"
    override fun incomeTo(place: String) = "Income → $place"
    override fun expenseFrom(place: String) = "Expense · $place"
    override fun typeName(type: TxType) = when (type) {
        TxType.INCOME -> "Income"
        TxType.EXPENSE -> "Expense"
        TxType.TRANSFER -> "Transfer"
    }
    override val previousMonth = "Previous month"
    override val nextMonth = "Next month"
    override val previousYear = "Previous year"
    override val nextYear = "Next year"
    override val cancel = "Cancel"
    override val ok = "OK"
    override val back = "Back"
    override val save = "Save"
    override val saveChanges = "Save changes"
    override val delete = "Delete"
    override val edit = "Edit"
    override val create = "Create"
    override val remove = "Remove"
    override val restore = "Restore"
    override val apply = "Apply"
    override val add = "Add"
    override val all = "All"
    override val today = "Today"
    override val yesterday = "Yesterday"
    override fun transactionsCount(n: Int) = if (n == 1) "1 transaction" else "$n transactions"
    override fun categoriesCount(n: Int) = if (n == 1) "1 category" else "$n categories"
    override fun days(n: Long) = if (n == 1L) "1 day" else "$n days"

    override val goodMorning = "Good morning"
    override val goodAfternoon = "Good afternoon"
    override val goodEvening = "Good evening"
    override val calendar = "Calendar"
    override val noTransactionsYet = "No transactions yet"
    override val addFirstIncome = "Add your first income to start tracking where your money goes."
    override val addIncome = "Add income"
    override val totalBalance = "Total balance"
    override val allocated = "Allocated"
    override val thisMonth = "This month"
    override val report = "Report"
    override val remainingThisMonth = "Remaining this month"
    override fun incomeSpentPct(pct: Int) = "$pct% of this month's income spent"
    override val totalIncomeAllTime = "Total income (all time)"
    override val totalExpensesAllTime = "Total expenses (all time)"
    override val budgetAlerts = "Budget alerts"
    override fun overBudgetBy(amount: String) = "Over budget by $amount"
    override fun usedLeft(pct: Int, left: String) = "$pct% used · $left left"
    override val whereMoneyIs = "Where your money is"
    override val createCategoriesHint = "Create categories in My Money to organise your money (Savings, Food, Travel…)."
    override val spendingThisMonth = "Spending this month"
    override val noSpendingThisMonth = "No spending recorded this month."
    override val spent = "Spent"
    override val recentTransactions = "Recent transactions"
    override val seeAll = "See all"

    override val txNotExists = "This transaction no longer exists."
    override val enterAmount = "Enter an amount"
    override val chooseTwoPlaces = "Choose two different places to move money between."
    override val editTransaction = "Edit transaction"
    override val newTransaction = "New transaction"
    override val deleteTransaction = "Delete transaction"
    override val amount = "Amount"
    override val addTo = "Add to"
    override val payFrom = "Pay from"
    override val from = "From"
    override val to = "To"
    override val incomeSuggestions = listOf("Salary", "Freelancing", "Gift", "Sold item", "Received money", "Other income")
    override val swapFromTo = "Swap from and to"
    override val sourceDescription = "Source / description"
    override val description = "Description"
    override val notesOptional = "Notes (optional)"
    override val deleteTxTitle = "Delete transaction?"
    override val deleteTxMessage = "Its effect on your balances will be reversed. This can't be undone."
    override val tipCreateCategories = "Tip: create categories in My Money to organise your money."
    override val notEnoughSuffix = "  (not enough money)"

    override val periodAllTime = "All time"
    override val periodThisWeek = "This week"
    override val periodLastMonth = "Last month"
    override val periodThisYear = "This year"
    override val periodCustom = "Custom range"
    override val transactionDeleted = "Transaction deleted"
    override val transactions = "Transactions"
    override val calendarView = "Calendar view"
    override val searchHint = "Search description, category, amount…"
    override val clearSearch = "Clear search"
    override val nothingMatches = "Nothing matches"
    override val tapAddToRecord = "Tap Add to record income, an expense or a transfer."
    override val tryAnotherSearch = "Try another search or clear the filters."
    override val clearFilters = "Clear filters"
    override val allCategories = "All categories"
    override val category = "Category"
    override val moneyIn = "In"
    override val moneyOut = "Out"
    override val dateLabel = "Date"
    override fun dateAt(date: String, time: String) = "$date at $time"
    override val addedTo = "Added to"
    override val paidFrom = "Paid from"
    override val notes = "Notes"

    override val net = "Net"
    override val moneyInLegend = "Money in"
    override val moneyOutLegend = "Money out"
    override val received = "Received"
    override val noTxOnDay = "No transactions on this day."

    override val currentMoney = "Current money"
    override val inCategories = "In categories"
    override val addMoney = "Add money"
    override val subtract = "Subtract"
    override val currentMoneyHint = "Add money for salary, freelancing, gifts or anything you sold. Subtract money for spending — " +
        "from Available or from a category. Tap a record to edit or delete it."
    override val nothingHereYet = "Nothing here yet"
    override val incomeExpensesAppearHere = "Your income and expenses will appear here."

    override val categoryDeleted = "Category deleted"
    override fun categoryDeletedReturned(amount: String) = "Category deleted. $amount returned to Available; history kept."
    override val categoryDeletedHistoryKept = "Category deleted; its history is kept in reports."
    override val editCategory = "Edit category"
    override val deleteCategory = "Delete category"
    override val categoryNotExists = "This category no longer exists."
    override val deletedCategory = "Deleted category"
    override val currentBalance = "Current balance"
    override val totalAdded = "Total added"
    override val totalSpent = "Total spent"
    override val movedOut = "Moved out"
    override val remaining = "Remaining"
    override val spend = "Spend"
    override val added = "Added"
    override fun leftOfBudget(left: String, limit: String) = "$left left of $limit budget"
    override fun goalProgress(saved: String, target: String) = "Goal: $saved of $target"
    override fun byDate(date: String) = " by $date"
    override fun history(n: Int) = "History ($n)"
    override val noActivityYet = "No activity yet. Use Add money to allocate from Available."
    override fun deleteCategoryTitle(name: String) = "Delete “$name”?"
    override val categoryNoTxRemoved = "This category has no transactions and will be removed."
    override val pastTxStay = "Its past transactions stay in your history and reports."
    override fun remainingReturned(amount: String) = " The remaining $amount will be returned to Available."

    override fun categoryCreated(name: String) = "“$name” created"
    override val budgetRemoved = "Budget removed"
    override val budgetSaved = "Budget saved"
    override val newCategory = "New category"
    override val newGoal = "New goal"
    override val setBudget = "Set budget"
    override val tabCategories = "Categories"
    override val tabGoals = "Goals"
    override val tabBudgets = "Budgets"
    override val budgetForWhich = "Budget for which category?"
    override val createCategoryFirst = "Create a category first."
    override val total = "Total"
    override val allocatingHint = "Allocating money doesn't spend it — your total stays the same until you add income or record an expense."
    override val notAssigned = "Not assigned to any category"
    override val startWithCategories = "Start with some categories"
    override val tapSuggestion = "Tap a suggestion to create it, or make your own with “New category”."
    override fun spentThisMonth(amount: String) = "Spent this month $amount"
    override fun ofMonthlyBudget(spent: String, limit: String) = "$spent of $limit monthly budget"
    override val noGoalsYet = "No goals yet"
    override val goalsHint = "Create a goal like “New phone — 300 OMR” or “Emergency fund” and add money to it over time."
    override fun xOfY(a: String, b: String) = "$a of $b"
    override val goalReached = "Goal reached 🎉"
    override fun toGo(amount: String) = "$amount to go"
    override fun targetDate(date: String) = "Target date $date"
    override fun daysLeft(n: Long) = "${days(n)} left"
    override fun daysOverdue(n: Long) = "${days(n)} overdue"
    override fun savePerMonth(amount: String) = "save $amount/month"
    override fun monthlyLimitsFor(month: String) = "Monthly spending limits for $month"
    override val noBudgets = "No budgets"
    override val budgetsHint = "Set a monthly limit for any category, e.g. Food — 100 OMR. You'll be warned at 80% and when you go over."
    override val budgetWarning = "Budget warning"
    override fun amountSpent(amount: String) = "$amount spent"
    override fun amountOver(amount: String) = "$amount over"
    override fun leftOf(left: String, limit: String) = "$left left of $limit"
    override fun monthlyBudgetFor(name: String) = "Monthly budget — $name"
    override val limitPerMonth = "Limit per month"
    override val suggestionNames = listOf(
        "Savings", "Food", "Shopping", "Transportation", "Education",
        "Emergency Fund", "Entertainment", "Bills", "Travel",
    )

    override val monthlyLimit = "Monthly limit"
    override val target = "Target"
    override val startingAmount = "Starting amount"
    override val enterTargetForGoal = "Enter a target amount for the goal."
    override val goalNameHint = "Goal name (e.g. New phone)"
    override val name = "Name"
    override val icon = "Icon"
    override val color = "Color"
    override val targetAmount = "Target amount"
    override val savingsTargetOptional = "Savings target (optional)"
    override fun targetDateColon(date: String) = "Target date: $date"
    override val addTargetDate = "Add a target date (optional)"
    override val clearDate = "Clear date"
    override val monthlyLimitOptional = "Monthly spending limit (optional)"
    override val allocateNowOptional = "Allocate now from Available (optional)"
    override fun availableColon(amount: String) = "Available: $amount"

    override val enterCurrency = "Enter a currency code or symbol"
    override val currencyUpdated = "Currency updated"
    override fun somethingWrong(msg: String) = "Something went wrong: $msg"
    override val backupSaved = "Backup saved"
    override val backupRestored = "Backup restored"
    override val csvExported = "Transactions exported to CSV"
    override val categoryRestored = "Category restored"
    override val allDataDeleted = "All financial data was deleted"
    override val appearance = "Appearance"
    override val themeSystem = "System"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val language = "Language"
    override val languageHint = "Choose the app language"
    override val currency = "Currency"
    override fun currencySummary(code: String, decimals: Int) = "$code · $decimals decimal places"
    override val yourData = "Your data"
    override fun dataSummary(tx: Int, cats: Int) =
        "${transactionsCount(tx)} · ${categoriesCount(cats)}. Everything is stored only on this phone and works offline."
    override val backUp = "Back up"
    override val backUpSub = "Save all data to a file you choose"
    override val restoreSub = "Replace current data with a backup file"
    override val exportCsv = "Export transactions (CSV)"
    override val exportCsvSub = "Open in Excel or Google Sheets"
    override val monthlyPdf = "Monthly PDF report"
    override val monthlyPdfSub = "Statistics → Monthly → PDF report"
    override val deletedCategories = "Deleted categories"
    override val deletedCategoriesSub = "Kept so your history and monthly reports stay accurate."
    override val resetAllData = "Reset all data"
    override val resetSub = "Delete every transaction and category"
    override fun aboutLine(version: String) = "Money Tracker $version · offline, no permissions, no ads"
    override val restoreTitle = "Restore this backup?"
    override val restoreMessage = "All current transactions and categories will be replaced by the backup's contents. " +
        "The file is checked first — if it's invalid nothing changes."
    override val currencyCodeHint = "Code or symbol (e.g. OMR, USD, €)"
    override val decimalPlaces = "Decimal places"
    override val decimalsHint = "Amounts are stored exactly to 1/1000 of a unit, so changing this only changes how they are shown and entered."
    override val deleteAllTitle = "Delete all data?"
    override val deleteAllMessage = "This permanently deletes every transaction and category. Make a backup first if you might need them. Type RESET to confirm."
    override val deleteEverything = "Delete everything"

    override val periodWeek = "Week"
    override val periodMonth = "Month"
    override val periodYear = "Year"
    override fun reportSaved(month: String) = "Report for $month saved"
    override fun couldntSaveReport(msg: String) = "Couldn't save the report: $msg"
    override fun monthExported(month: String) = "Transactions for $month exported"
    override fun couldntExport(msg: String) = "Couldn't export: $msg"
    override val overview = "Overview"
    override val monthly = "Monthly"
    override val previousPeriod = "Previous period"
    override val nextPeriod = "Next period"
    override val difference = "Difference (income − expenses)"
    override val incomeVsExpenses = "Income vs expenses"
    override val last6Hint = "Last 6 months · tap a month for details"
    override val spendingTrends = "Spending trends"
    override fun granularity(g: Granularity) = when (g) {
        Granularity.DAY -> "Daily"
        Granularity.WEEK -> "Weekly"
        Granularity.MONTH -> "Monthly"
        Granularity.YEAR -> "Yearly"
    }
    override fun averagePer(amount: String, g: Granularity) = "Average $amount per " + when (g) {
        Granularity.DAY -> "day"
        Granularity.WEEK -> "week"
        Granularity.MONTH -> "month"
        Granularity.YEAR -> "year"
    } + " over this range"
    override val savings = "Savings"
    override val totalSavedNow = "Total saved now"
    override val savedThisMonth = "Saved this month"
    override val balanceEndEachMonth = "Balance at the end of each month"
    override val balance = "Balance"
    override val whereMoneyGoes = "Where your money goes"
    override val tapSliceHint = "Tap a slice or a category for details"
    override val noExpensesPeriod = "No expenses in this period."
    override val totalSpentLabel = "Total spent"
    override fun more(n: Int) = "+ $n more"
    override fun insightsFor(month: String) = "Insights · $month"
    override val highestCategory = "Highest spending category"
    override val noCategorySpending = "No category spending yet"
    override val lowestCategory = "Lowest spending category"
    override val needsTwoCategories = "Needs spending in 2+ categories"
    override val averageDaily = "Average daily spending"
    override val savedThisMonthFull = "Saved this month (income − expenses)"
    override val comparedPrevious = "Compared with previous month"
    override val noSpendingEither = "No spending in either month"
    override val spentSame = "You spent the same amount"
    override fun spentMoreLess(amount: String, more: Boolean, pct: String?) =
        "You spent $amount ${if (more) "more" else "less"}" + (pct?.let { " ($it%)" } ?: "")
    override val budgetsExceeded = "Budgets exceeded"
    override val noneWellDone = "None — well done"
    override val closeToLimit = "Close to the limit"
    override val netSavings = "Net savings"
    override fun vsMonth(month: String) = "vs $month"
    override val balanceMonthEnd = "Balance at month end"
    override val unallocatedMonthEnd = "Unallocated at month end"
    override val dailySpending = "Daily spending"
    override fun moneyReceivedDays(n: Int) = "Money received on ${days(n.toLong())} this month · tap a bar to see that day's spending"
    override val last6Months = "Last 6 months"
    override val categoryAcrossMonths = "Category spending across months"
    override val pdfReport = "PDF report"
    override val noChange = "no change"
    override val categoriesThisMonth = "Categories this month"
    override val categoriesTableHint = "Added = allocated or received during the month. Balance as it stood at month end."
    override fun budgetUsed(limit: String, pct: Int) = "Budget $limit · $pct% used"
    override fun amountsIn(currency: String) = "Amounts in $currency"

    override val amountGtZero = "Amount must be greater than zero"
    override val invalidAmount = "Enter a valid amount"
    override val wholeOnly = "Use whole amounts only"
    override fun maxDecimals(n: Int) = "Use at most $n decimal places"
    override val amountTooLarge = "Amount is too large"

    override val futureDate = "The date can't be in the future."
    override val descTooLong = "Description is too long (max 80 characters)."
    override val noteTooLong = "Note is too long (max 500 characters)."
    override fun categoryWasDeleted(name: String) = "“$name” was deleted. Choose another category."
    override val enterName = "Please enter a name."
    override val nameTooLong = "Name is too long (max 40 characters)."
    override val availableReserved = "“Available” is reserved for unallocated money."
    override fun categoryExists(name: String) = "A category called “$name” already exists."
    override val limitGtZero = "Monthly limit must be greater than zero."
    override val targetGtZero = "Target must be greater than zero."
    override fun returnedFrom(name: String) = "Returned from $name (category deleted)"
    override fun activeExists(name: String) = "An active category called “$name” already exists. Rename it first."
    override fun backupInconsistent(msg: String) = "The backup is inconsistent: $msg"
    override fun notEnoughAvailable(amount: String) =
        "Not enough available money. This would leave Available at $amount. Add income first, or take the money from a category."
    override fun notEnoughIn(name: String, amount: String) = "Not enough money in “$name”. This would leave it at $amount."

    override val notBackupFile = "This file is not a Money Tracker backup."
    override fun unsupportedVersion(v: Int) = "Unsupported backup version ($v)."
    override val backupDuplicateCategories = "The backup contains duplicate categories."
    override val backupInvalidCategoryIds = "The backup contains invalid category ids."
    override val backupUnknownType = "Unknown transaction type in backup."
    override val backupInvalidAmount = "The backup contains an invalid amount."
    override val backupMissingCategory = "The backup references a missing category."
    override val backupInvalidTransfer = "The backup contains an invalid transfer."
    override val backupDuplicateTransactions = "The backup contains duplicate or invalid transactions."
    override val backupDamaged = "The backup file is damaged or incomplete."
    override val cantOpenFile = "Couldn't open the selected file."
    override val cantWriteFile = "Couldn't write to the selected file."
    override val csvHeader = listOf(
        "Date", "Time", "Type", "Amount", "Signed Amount", "Currency", "Description", "From / Category", "To", "Notes",
    )
    override fun pdfTitle(month: String) = "Monthly report — $month"
    override fun pdfGenerated(date: String) = "Generated $date by Money Tracker"
    override val summary = "Summary"
    override val totalIncome = "Total income"
    override val totalExpenses = "Total expenses"
    override val prevIncome = "Previous month income"
    override val prevExpenses = "Previous month expenses"
    override val budget = "Budget"
    override val balanceAtEnd = "Balance at end"
    override val availableUnallocated = "Available (unallocated)"
    override val deletedWord = "Deleted"
    override val spendingByCategory = "Spending by category"
    override val noExpensesThisMonth = "No expenses this month."

    override val chooseLanguageTitle = "Choose your language"
}

/** Arabic plural forms: 1, 2, 3–10, 11+ (and 0). */
private fun arCount(n: Long, one: String, two: String, few: String, many: String): String = when {
    n == 1L -> one
    n == 2L -> two
    n in 3L..10L -> "$n $few"
    else -> "$n $many"
}

object ArStrings : Strings() {
    override val appName = "متتبع المال"
    override val navHome = "الرئيسية"
    override val navActivity = "النشاط"
    override val navMoney = "أموالي"
    override val navStats = "الإحصائيات"
    override val navSettings = "الإعدادات"
    override val available = "المتاح"
    override val unknown = "غير معروف"
    override fun deletedName(name: String) = "$name (محذوفة)"
    override val income = "الدخل"
    override val expense = "مصروف"
    override val expenses = "المصروفات"
    override val transfer = "تحويل"
    override val transfers = "التحويلات"
    override val allocate = "تخصيص"
    override fun allocatedTo(name: String) = "تخصيص إلى $name"
    override val movedToAvailable = "نقل إلى المتاح"
    override val arrow = "←"
    override fun incomeTo(place: String) = "دخل ← $place"
    override fun expenseFrom(place: String) = "مصروف · $place"
    override fun typeName(type: TxType) = when (type) {
        TxType.INCOME -> "دخل"
        TxType.EXPENSE -> "مصروف"
        TxType.TRANSFER -> "تحويل"
    }
    override val previousMonth = "الشهر السابق"
    override val nextMonth = "الشهر التالي"
    override val previousYear = "السنة السابقة"
    override val nextYear = "السنة التالية"
    override val cancel = "إلغاء"
    override val ok = "موافق"
    override val back = "رجوع"
    override val save = "حفظ"
    override val saveChanges = "حفظ التغييرات"
    override val delete = "حذف"
    override val edit = "تعديل"
    override val create = "إنشاء"
    override val remove = "إزالة"
    override val restore = "استعادة"
    override val apply = "تطبيق"
    override val add = "إضافة"
    override val all = "الكل"
    override val today = "اليوم"
    override val yesterday = "أمس"
    override fun transactionsCount(n: Int) = arCount(n.toLong(), "معاملة واحدة", "معاملتان", "معاملات", "معاملة")
    override fun categoriesCount(n: Int) = arCount(n.toLong(), "فئة واحدة", "فئتان", "فئات", "فئة")
    override fun days(n: Long) = arCount(n, "يوم واحد", "يومان", "أيام", "يوماً")

    override val goodMorning = "صباح الخير"
    override val goodAfternoon = "طاب يومك"
    override val goodEvening = "مساء الخير"
    override val calendar = "التقويم"
    override val noTransactionsYet = "لا توجد معاملات بعد"
    override val addFirstIncome = "أضف أول دخل لك لتبدأ بمعرفة أين يذهب مالك."
    override val addIncome = "إضافة دخل"
    override val totalBalance = "الرصيد الإجمالي"
    override val allocated = "المخصص"
    override val thisMonth = "هذا الشهر"
    override val report = "التقرير"
    override val remainingThisMonth = "المتبقي هذا الشهر"
    override fun incomeSpentPct(pct: Int) = "تم صرف $pct% من دخل هذا الشهر"
    override val totalIncomeAllTime = "إجمالي الدخل (كل الوقت)"
    override val totalExpensesAllTime = "إجمالي المصروفات (كل الوقت)"
    override val budgetAlerts = "تنبيهات الميزانية"
    override fun overBudgetBy(amount: String) = "تجاوز الميزانية بمقدار $amount"
    override fun usedLeft(pct: Int, left: String) = "مستخدم $pct% · متبقٍ $left"
    override val whereMoneyIs = "أين أموالك"
    override val createCategoriesHint = "أنشئ فئات في «أموالي» لتنظيم مالك (ادخار، طعام، سفر…)."
    override val spendingThisMonth = "الإنفاق هذا الشهر"
    override val noSpendingThisMonth = "لا يوجد إنفاق مسجل هذا الشهر."
    override val spent = "المصروف"
    override val recentTransactions = "أحدث المعاملات"
    override val seeAll = "عرض الكل"

    override val txNotExists = "هذه المعاملة لم تعد موجودة."
    override val enterAmount = "أدخل المبلغ"
    override val chooseTwoPlaces = "اختر مكانين مختلفين لنقل المال بينهما."
    override val editTransaction = "تعديل المعاملة"
    override val newTransaction = "معاملة جديدة"
    override val deleteTransaction = "حذف المعاملة"
    override val amount = "المبلغ"
    override val addTo = "إضافة إلى"
    override val payFrom = "الدفع من"
    override val from = "من"
    override val to = "إلى"
    override val incomeSuggestions = listOf("راتب", "عمل حر", "هدية", "بيع غرض", "مبلغ مستلم", "دخل آخر")
    override val swapFromTo = "تبديل «من» و«إلى»"
    override val sourceDescription = "المصدر / الوصف"
    override val description = "الوصف"
    override val notesOptional = "ملاحظات (اختياري)"
    override val deleteTxTitle = "حذف المعاملة؟"
    override val deleteTxMessage = "سيتم عكس أثرها على أرصدتك. لا يمكن التراجع عن ذلك."
    override val tipCreateCategories = "نصيحة: أنشئ فئات في «أموالي» لتنظيم مالك."
    override val notEnoughSuffix = "  (المبلغ غير كافٍ)"

    override val periodAllTime = "كل الوقت"
    override val periodThisWeek = "هذا الأسبوع"
    override val periodLastMonth = "الشهر الماضي"
    override val periodThisYear = "هذه السنة"
    override val periodCustom = "فترة مخصصة"
    override val transactionDeleted = "تم حذف المعاملة"
    override val transactions = "المعاملات"
    override val calendarView = "عرض التقويم"
    override val searchHint = "ابحث بالوصف أو الفئة أو المبلغ…"
    override val clearSearch = "مسح البحث"
    override val nothingMatches = "لا توجد نتائج"
    override val tapAddToRecord = "اضغط «إضافة» لتسجيل دخل أو مصروف أو تحويل."
    override val tryAnotherSearch = "جرّب بحثاً آخر أو امسح عوامل التصفية."
    override val clearFilters = "مسح التصفية"
    override val allCategories = "كل الفئات"
    override val category = "الفئة"
    override val moneyIn = "الوارد"
    override val moneyOut = "الصادر"
    override val dateLabel = "التاريخ"
    override fun dateAt(date: String, time: String) = "$date الساعة $time"
    override val addedTo = "أُضيف إلى"
    override val paidFrom = "دُفع من"
    override val notes = "ملاحظات"

    override val net = "الصافي"
    override val moneyInLegend = "وارد"
    override val moneyOutLegend = "صادر"
    override val received = "المستلم"
    override val noTxOnDay = "لا توجد معاملات في هذا اليوم."

    override val currentMoney = "المال الحالي"
    override val inCategories = "في الفئات"
    override val addMoney = "إضافة مال"
    override val subtract = "خصم"
    override val currentMoneyHint = "أضف المال من الراتب أو العمل الحر أو الهدايا أو أي شيء بعته. واخصم المال عند الإنفاق — " +
        "من المتاح أو من إحدى الفئات. اضغط على أي سجل لتعديله أو حذفه."
    override val nothingHereYet = "لا شيء هنا بعد"
    override val incomeExpensesAppearHere = "ستظهر هنا مداخيلك ومصروفاتك."

    override val categoryDeleted = "تم حذف الفئة"
    override fun categoryDeletedReturned(amount: String) = "تم حذف الفئة وأُعيد $amount إلى المتاح مع الاحتفاظ بالسجل."
    override val categoryDeletedHistoryKept = "تم حذف الفئة مع الاحتفاظ بسجلها في التقارير."
    override val editCategory = "تعديل الفئة"
    override val deleteCategory = "حذف الفئة"
    override val categoryNotExists = "هذه الفئة لم تعد موجودة."
    override val deletedCategory = "فئة محذوفة"
    override val currentBalance = "الرصيد الحالي"
    override val totalAdded = "إجمالي المضاف"
    override val totalSpent = "إجمالي المصروف"
    override val movedOut = "المنقول منها"
    override val remaining = "المتبقي"
    override val spend = "صرف"
    override val added = "المضاف"
    override fun leftOfBudget(left: String, limit: String) = "متبقٍ $left من ميزانية $limit"
    override fun goalProgress(saved: String, target: String) = "الهدف: $saved من $target"
    override fun byDate(date: String) = " بحلول $date"
    override fun history(n: Int) = "السجل ($n)"
    override val noActivityYet = "لا يوجد نشاط بعد. استخدم «إضافة مال» للتخصيص من المتاح."
    override fun deleteCategoryTitle(name: String) = "حذف «$name»؟"
    override val categoryNoTxRemoved = "هذه الفئة ليس لها معاملات وسيتم حذفها."
    override val pastTxStay = "ستبقى معاملاتها السابقة في سجلك وتقاريرك."
    override fun remainingReturned(amount: String) = " وسيُعاد المبلغ المتبقي $amount إلى المتاح."

    override fun categoryCreated(name: String) = "تم إنشاء «$name»"
    override val budgetRemoved = "تمت إزالة الميزانية"
    override val budgetSaved = "تم حفظ الميزانية"
    override val newCategory = "فئة جديدة"
    override val newGoal = "هدف جديد"
    override val setBudget = "تحديد ميزانية"
    override val tabCategories = "الفئات"
    override val tabGoals = "الأهداف"
    override val tabBudgets = "الميزانيات"
    override val budgetForWhich = "لأي فئة تريد تحديد الميزانية؟"
    override val createCategoryFirst = "أنشئ فئة أولاً."
    override val total = "الإجمالي"
    override val allocatingHint = "تخصيص المال لا يعني صرفه — يبقى إجماليك كما هو حتى تضيف دخلاً أو تسجل مصروفاً."
    override val notAssigned = "غير مخصص لأي فئة"
    override val startWithCategories = "ابدأ ببعض الفئات"
    override val tapSuggestion = "اضغط على اقتراح لإنشائه، أو أنشئ فئتك الخاصة من «فئة جديدة»."
    override fun spentThisMonth(amount: String) = "المصروف هذا الشهر $amount"
    override fun ofMonthlyBudget(spent: String, limit: String) = "$spent من الميزانية الشهرية $limit"
    override val noGoalsYet = "لا توجد أهداف بعد"
    override val goalsHint = "أنشئ هدفاً مثل «هاتف جديد — 300 ر.ع» أو «صندوق الطوارئ» وأضف إليه المال مع الوقت."
    override fun xOfY(a: String, b: String) = "$a من $b"
    override val goalReached = "تحقق الهدف 🎉"
    override fun toGo(amount: String) = "متبقٍ $amount"
    override fun targetDate(date: String) = "التاريخ المستهدف $date"
    override fun daysLeft(n: Long) = "متبقٍ ${days(n)}"
    override fun daysOverdue(n: Long) = "متأخر ${days(n)}"
    override fun savePerMonth(amount: String) = "ادّخر $amount شهرياً"
    override fun monthlyLimitsFor(month: String) = "حدود الإنفاق الشهرية لشهر $month"
    override val noBudgets = "لا توجد ميزانيات"
    override val budgetsHint = "حدد سقفاً شهرياً لأي فئة، مثل الطعام — 100 ر.ع. ستتلقى تنبيهاً عند 80% وعند التجاوز."
    override val budgetWarning = "تنبيه الميزانية"
    override fun amountSpent(amount: String) = "تم صرف $amount"
    override fun amountOver(amount: String) = "تجاوز بمقدار $amount"
    override fun leftOf(left: String, limit: String) = "متبقٍ $left من $limit"
    override fun monthlyBudgetFor(name: String) = "الميزانية الشهرية — $name"
    override val limitPerMonth = "الحد الشهري"
    override val suggestionNames = listOf(
        "ادخار", "طعام", "تسوق", "مواصلات", "تعليم",
        "صندوق الطوارئ", "ترفيه", "فواتير", "سفر",
    )

    override val monthlyLimit = "الحد الشهري"
    override val target = "الهدف"
    override val startingAmount = "المبلغ المبدئي"
    override val enterTargetForGoal = "أدخل المبلغ المستهدف للهدف."
    override val goalNameHint = "اسم الهدف (مثل: هاتف جديد)"
    override val name = "الاسم"
    override val icon = "الأيقونة"
    override val color = "اللون"
    override val targetAmount = "المبلغ المستهدف"
    override val savingsTargetOptional = "هدف الادخار (اختياري)"
    override fun targetDateColon(date: String) = "التاريخ المستهدف: $date"
    override val addTargetDate = "إضافة تاريخ مستهدف (اختياري)"
    override val clearDate = "مسح التاريخ"
    override val monthlyLimitOptional = "حد الإنفاق الشهري (اختياري)"
    override val allocateNowOptional = "خصّص الآن من المتاح (اختياري)"
    override fun availableColon(amount: String) = "المتاح: $amount"

    override val enterCurrency = "أدخل رمز العملة"
    override val currencyUpdated = "تم تحديث العملة"
    override fun somethingWrong(msg: String) = "حدث خطأ: $msg"
    override val backupSaved = "تم حفظ النسخة الاحتياطية"
    override val backupRestored = "تمت استعادة النسخة الاحتياطية"
    override val csvExported = "تم تصدير المعاملات إلى ملف CSV"
    override val categoryRestored = "تمت استعادة الفئة"
    override val allDataDeleted = "تم حذف جميع البيانات المالية"
    override val appearance = "المظهر"
    override val themeSystem = "النظام"
    override val themeLight = "فاتح"
    override val themeDark = "داكن"
    override val language = "اللغة"
    override val languageHint = "اختر لغة التطبيق"
    override val currency = "العملة"
    override fun currencySummary(code: String, decimals: Int) = "$code · منازل عشرية: $decimals"
    override val yourData = "بياناتك"
    override fun dataSummary(tx: Int, cats: Int) =
        "${transactionsCount(tx)} · ${categoriesCount(cats)}. كل شيء محفوظ على هذا الهاتف فقط ويعمل دون إنترنت."
    override val backUp = "نسخ احتياطي"
    override val backUpSub = "احفظ جميع البيانات في ملف تختاره"
    override val restoreSub = "استبدل البيانات الحالية بملف نسخة احتياطية"
    override val exportCsv = "تصدير المعاملات (CSV)"
    override val exportCsvSub = "افتحه في Excel أو Google Sheets"
    override val monthlyPdf = "تقرير شهري PDF"
    override val monthlyPdfSub = "الإحصائيات ← شهري ← تقرير PDF"
    override val deletedCategories = "الفئات المحذوفة"
    override val deletedCategoriesSub = "محفوظة لتبقى سجلاتك وتقاريرك الشهرية دقيقة."
    override val resetAllData = "مسح جميع البيانات"
    override val resetSub = "حذف كل المعاملات والفئات"
    override fun aboutLine(version: String) = "متتبع المال $version · يعمل دون إنترنت، بلا أذونات ولا إعلانات"
    override val restoreTitle = "استعادة هذه النسخة؟"
    override val restoreMessage = "سيتم استبدال جميع المعاملات والفئات الحالية بمحتوى النسخة الاحتياطية. " +
        "يتم فحص الملف أولاً — وإن كان غير صالح فلن يتغير شيء."
    override val currencyCodeHint = "رمز العملة (مثل OMR أو USD أو €)"
    override val decimalPlaces = "المنازل العشرية"
    override val decimalsHint = "تُحفظ المبالغ بدقة جزء من ألف من الوحدة، لذلك يغيّر هذا الإعداد طريقة العرض والإدخال فقط."
    override val deleteAllTitle = "حذف جميع البيانات؟"
    override val deleteAllMessage = "سيؤدي هذا إلى حذف كل المعاملات والفئات نهائياً. خذ نسخة احتياطية أولاً إن كنت قد تحتاجها. اكتب RESET للتأكيد."
    override val deleteEverything = "حذف كل شيء"

    override val periodWeek = "أسبوع"
    override val periodMonth = "شهر"
    override val periodYear = "سنة"
    override fun reportSaved(month: String) = "تم حفظ تقرير $month"
    override fun couldntSaveReport(msg: String) = "تعذّر حفظ التقرير: $msg"
    override fun monthExported(month: String) = "تم تصدير معاملات $month"
    override fun couldntExport(msg: String) = "تعذّر التصدير: $msg"
    override val overview = "نظرة عامة"
    override val monthly = "شهري"
    override val previousPeriod = "الفترة السابقة"
    override val nextPeriod = "الفترة التالية"
    override val difference = "الفرق (الدخل − المصروفات)"
    override val incomeVsExpenses = "الدخل مقابل المصروفات"
    override val last6Hint = "آخر 6 أشهر · اضغط على شهر لعرض التفاصيل"
    override val spendingTrends = "اتجاهات الإنفاق"
    override fun granularity(g: Granularity) = when (g) {
        Granularity.DAY -> "يومي"
        Granularity.WEEK -> "أسبوعي"
        Granularity.MONTH -> "شهري"
        Granularity.YEAR -> "سنوي"
    }
    override fun averagePer(amount: String, g: Granularity) = "المتوسط $amount لكل " + when (g) {
        Granularity.DAY -> "يوم"
        Granularity.WEEK -> "أسبوع"
        Granularity.MONTH -> "شهر"
        Granularity.YEAR -> "سنة"
    } + " خلال هذه الفترة"
    override val savings = "المدخرات"
    override val totalSavedNow = "إجمالي المدخرات الآن"
    override val savedThisMonth = "المدّخر هذا الشهر"
    override val balanceEndEachMonth = "الرصيد في نهاية كل شهر"
    override val balance = "الرصيد"
    override val whereMoneyGoes = "أين يذهب مالك"
    override val tapSliceHint = "اضغط على جزء من الرسم أو على فئة لعرض التفاصيل"
    override val noExpensesPeriod = "لا توجد مصروفات في هذه الفترة."
    override val totalSpentLabel = "إجمالي المصروف"
    override fun more(n: Int) = "+ $n أخرى"
    override fun insightsFor(month: String) = "ملاحظات · $month"
    override val highestCategory = "أعلى فئة إنفاقاً"
    override val noCategorySpending = "لا يوجد إنفاق في الفئات بعد"
    override val lowestCategory = "أقل فئة إنفاقاً"
    override val needsTwoCategories = "يتطلب إنفاقاً في فئتين أو أكثر"
    override val averageDaily = "متوسط الإنفاق اليومي"
    override val savedThisMonthFull = "المدّخر هذا الشهر (الدخل − المصروفات)"
    override val comparedPrevious = "مقارنة بالشهر السابق"
    override val noSpendingEither = "لا يوجد إنفاق في أي من الشهرين"
    override val spentSame = "أنفقت المبلغ نفسه"
    override fun spentMoreLess(amount: String, more: Boolean, pct: String?) =
        "أنفقت $amount ${if (more) "أكثر" else "أقل"}" + (pct?.let { " ($it%)" } ?: "")
    override val budgetsExceeded = "الميزانيات المتجاوزة"
    override val noneWellDone = "لا شيء — أحسنت"
    override val closeToLimit = "قريبة من الحد"
    override val netSavings = "صافي الادخار"
    override fun vsMonth(month: String) = "مقارنة بشهر $month"
    override val balanceMonthEnd = "الرصيد في نهاية الشهر"
    override val unallocatedMonthEnd = "غير المخصص في نهاية الشهر"
    override val dailySpending = "الإنفاق اليومي"
    override fun moneyReceivedDays(n: Int) = "استلمت مالاً في ${days(n.toLong())} هذا الشهر · اضغط على عمود لعرض إنفاق ذلك اليوم"
    override val last6Months = "آخر 6 أشهر"
    override val categoryAcrossMonths = "إنفاق الفئة عبر الأشهر"
    override val pdfReport = "تقرير PDF"
    override val noChange = "بلا تغيير"
    override val categoriesThisMonth = "الفئات هذا الشهر"
    override val categoriesTableHint = "المضاف = ما خُصص أو استُلم خلال الشهر. الرصيد كما كان في نهاية الشهر."
    override fun budgetUsed(limit: String, pct: Int) = "الميزانية $limit · مستخدم $pct%"
    override fun amountsIn(currency: String) = "المبالغ بعملة $currency"

    override val amountGtZero = "يجب أن يكون المبلغ أكبر من صفر"
    override val invalidAmount = "أدخل مبلغاً صحيحاً"
    override val wholeOnly = "استخدم أعداداً صحيحة فقط"
    override fun maxDecimals(n: Int) = "استخدم $n منازل عشرية كحد أقصى"
    override val amountTooLarge = "المبلغ كبير جداً"

    override val futureDate = "لا يمكن أن يكون التاريخ في المستقبل."
    override val descTooLong = "الوصف طويل جداً (80 حرفاً كحد أقصى)."
    override val noteTooLong = "الملاحظة طويلة جداً (500 حرف كحد أقصى)."
    override fun categoryWasDeleted(name: String) = "تم حذف «$name». اختر فئة أخرى."
    override val enterName = "يرجى إدخال اسم."
    override val nameTooLong = "الاسم طويل جداً (40 حرفاً كحد أقصى)."
    override val availableReserved = "«المتاح» اسم محجوز للمال غير المخصص."
    override fun categoryExists(name: String) = "توجد فئة باسم «$name» بالفعل."
    override val limitGtZero = "يجب أن يكون الحد الشهري أكبر من صفر."
    override val targetGtZero = "يجب أن يكون الهدف أكبر من صفر."
    override fun returnedFrom(name: String) = "مُعاد من $name (فئة محذوفة)"
    override fun activeExists(name: String) = "توجد فئة نشطة باسم «$name». غيّر اسمها أولاً."
    override fun backupInconsistent(msg: String) = "النسخة الاحتياطية غير متسقة: $msg"
    override fun notEnoughAvailable(amount: String) =
        "المال المتاح غير كافٍ. سيصبح المتاح $amount. أضف دخلاً أولاً أو خذ المال من إحدى الفئات."
    override fun notEnoughIn(name: String, amount: String) = "المال غير كافٍ في «$name». سيصبح رصيدها $amount."

    override val notBackupFile = "هذا الملف ليس نسخة احتياطية من متتبع المال."
    override fun unsupportedVersion(v: Int) = "إصدار النسخة الاحتياطية غير مدعوم ($v)."
    override val backupDuplicateCategories = "تحتوي النسخة الاحتياطية على فئات مكررة."
    override val backupInvalidCategoryIds = "تحتوي النسخة الاحتياطية على معرّفات فئات غير صالحة."
    override val backupUnknownType = "نوع معاملة غير معروف في النسخة الاحتياطية."
    override val backupInvalidAmount = "تحتوي النسخة الاحتياطية على مبلغ غير صالح."
    override val backupMissingCategory = "تشير النسخة الاحتياطية إلى فئة غير موجودة."
    override val backupInvalidTransfer = "تحتوي النسخة الاحتياطية على تحويل غير صالح."
    override val backupDuplicateTransactions = "تحتوي النسخة الاحتياطية على معاملات مكررة أو غير صالحة."
    override val backupDamaged = "ملف النسخة الاحتياطية تالف أو غير مكتمل."
    override val cantOpenFile = "تعذّر فتح الملف المحدد."
    override val cantWriteFile = "تعذّرت الكتابة في الملف المحدد."
    override val csvHeader = listOf(
        "التاريخ", "الوقت", "النوع", "المبلغ", "المبلغ بالإشارة", "العملة", "الوصف", "من / الفئة", "إلى", "ملاحظات",
    )
    override fun pdfTitle(month: String) = "التقرير الشهري — $month"
    override fun pdfGenerated(date: String) = "أُنشئ في $date بواسطة متتبع المال"
    override val summary = "الملخص"
    override val totalIncome = "إجمالي الدخل"
    override val totalExpenses = "إجمالي المصروفات"
    override val prevIncome = "دخل الشهر السابق"
    override val prevExpenses = "مصروفات الشهر السابق"
    override val budget = "الميزانية"
    override val balanceAtEnd = "الرصيد بالنهاية"
    override val availableUnallocated = "المتاح (غير مخصص)"
    override val deletedWord = "محذوفة"
    override val spendingByCategory = "الإنفاق حسب الفئة"
    override val noExpensesThisMonth = "لا توجد مصروفات هذا الشهر."

    override val chooseLanguageTitle = "اختر لغتك"
}
