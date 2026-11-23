package com.moneytracker.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.moneytracker.app.AppContainer
import com.moneytracker.app.BuildConfig
import com.moneytracker.app.data.AppSettings
import com.moneytracker.app.data.ThemeMode
import com.moneytracker.app.data.db.Category
import com.moneytracker.app.domain.FinanceException
import com.moneytracker.app.ui.AppViewModelProvider
import com.moneytracker.app.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settings.settings
    val message = MutableStateFlow<String?>(null)
    val archived: StateFlow<List<Category>> = container.finance.snapshot
        .map { s -> s.categories.filter { it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val counts = container.finance.snapshot
        .map { it.transactions.size to it.activeCategories.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)

    fun setTheme(m: ThemeMode) = container.settings.setTheme(m)

    fun setCurrency(code: String, decimals: Int) {
        val clean = code.trim().uppercase()
        if (clean.isEmpty()) {
            message.value = "Enter a currency code or symbol"
            return
        }
        container.settings.setCurrency(clean, decimals)
        message.value = "Currency updated"
    }

    private fun run(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            message.value = try {
                block()
                success
            } catch (e: FinanceException) {
                e.message
            } catch (e: Exception) {
                "Something went wrong: ${e.message}"
            }
        }
    }

    fun backup(uri: Uri) = run("Backup saved") { container.backup.exportBackup(uri) }
    fun restore(uri: Uri) = run("Backup restored") { container.backup.restoreBackup(uri) }
    fun exportCsv(uri: Uri) = run("Transactions exported to CSV") { container.backup.exportCsv(uri) }
    fun restoreCategory(id: Long) = run("Category restored") { container.finance.restoreCategory(id) }
    fun resetAll() = run("All financial data was deleted") { container.finance.resetAll() }
}

@Composable
fun SettingsScreen(nav: NavController, vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var currencyDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var resetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }

    val today = LocalDate.now()
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.backup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("settingsList"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AppCard {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                        modes.forEachIndexed { i, (m, label) ->
                            SegmentedButton(
                                selected = settings.theme == m,
                                onClick = { vm.setTheme(m) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                modifier = Modifier.testTag("theme_${m.name}"),
                            ) { Text(label) }
                        }
                    }
                }
            }
            item {
                AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                    SettingRow(
                        Icons.Rounded.CurrencyExchange,
                        "Currency",
                        "${settings.currency} · ${settings.decimals} decimal places",
                        Modifier.testTag("currencySetting"),
                    ) { currencyDialog = true }
                }
            }
            item {
                AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                    Text("Your data", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    Text(
                        "${counts.first} transactions · ${counts.second} categories. Everything is stored only on this phone and works offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    SettingRow(Icons.Rounded.Backup, "Back up", "Save all data to a file you choose") {
                        backupLauncher.launch("MoneyTracker-backup-$today.json")
                    }
                    SettingRow(Icons.Rounded.Restore, "Restore", "Replace current data with a backup file") {
                        restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                    SettingRow(Icons.Rounded.TableChart, "Export transactions (CSV)", "Open in Excel or Google Sheets") {
                        csvLauncher.launch("MoneyTracker-transactions-$today.csv")
                    }
                    SettingRow(Icons.Rounded.PictureAsPdf, "Monthly PDF report", "Statistics → Monthly → PDF report") {
                        nav.navigate(com.moneytracker.app.ui.Routes.stats(1))
                    }
                }
            }
            if (archived.isNotEmpty()) {
                item {
                    AppCard {
                        Text("Deleted categories", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Kept so your history and monthly reports stay accurate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        archived.forEach { c ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge(c, size = 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(c.name, modifier = Modifier.weight(1f))
                                TextButton(onClick = { vm.restoreCategory(c.id) }) { Text("Restore") }
                            }
                        }
                    }
                }
            }
            item {
                AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                    SettingRow(
                        Icons.Rounded.DeleteForever,
                        "Reset all data",
                        "Delete every transaction and category",
                        Modifier.testTag("resetData"),
                        tint = MaterialTheme.colorScheme.error,
                    ) { resetDialog = true }
                }
            }
            item {
                Text(
                    "Money Tracker ${BuildConfig.VERSION_NAME} · offline, no permissions, no ads",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
        }
    }

    if (currencyDialog) {
        CurrencyDialog(settings, onDismiss = { currencyDialog = false }) { code, dec ->
            currencyDialog = false
            vm.setCurrency(code, dec)
        }
    }
    restoreUri?.let { uri ->
        ConfirmDialog(
            title = "Restore this backup?",
            message = "All current transactions and categories will be replaced by the backup's contents. " +
                "The file is checked first — if it's invalid nothing changes.",
            confirmLabel = "Restore",
            onConfirm = {
                restoreUri = null
                vm.restore(uri)
            },
            onDismiss = { restoreUri = null },
        )
    }
    if (resetDialog) {
        ResetDialog(onDismiss = { resetDialog = false }) {
            resetDialog = false
            vm.resetAll()
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CurrencyDialog(current: AppSettings, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var code by remember { mutableStateOf(current.currency) }
    var decimals by remember { mutableIntStateOf(current.decimals) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    label = { Text("Code or symbol (e.g. OMR, USD, €)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.testTag("currencyCode"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("OMR" to 3, "AED" to 2, "SAR" to 2, "USD" to 2, "EUR" to 2, "KWD" to 3).forEach { (c, d) ->
                        SuggestionChip(onClick = { code = c; decimals = d }, label = { Text(c) })
                    }
                }
                Text("Decimal places", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(0, 2, 3).forEachIndexed { i, d ->
                        SegmentedButton(
                            selected = decimals == d,
                            onClick = { decimals = d },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                            modifier = Modifier.testTag("decimals_$d"),
                        ) { Text(d.toString()) }
                    }
                }
                Text(
                    "Amounts are stored exactly to 1/1000 of a unit, so changing this only changes how they are shown and entered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(code, decimals) }, modifier = Modifier.testTag("currencySave")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ResetDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete all data?") },
        text = {
            Column {
                Text("This permanently deletes every transaction and category. Make a backup first if you might need them. Type RESET to confirm.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.testTag("resetConfirmField"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = text.trim() == "RESET", modifier = Modifier.testTag("resetConfirm")) {
                Text("Delete everything", color = if (text.trim() == "RESET") MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
