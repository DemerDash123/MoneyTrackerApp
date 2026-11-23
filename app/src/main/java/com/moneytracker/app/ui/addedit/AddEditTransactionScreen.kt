package com.moneytracker.app.ui.addedit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.moneytracker.app.AppContainer
import com.moneytracker.app.core.AmountParse
import com.moneytracker.app.core.Dates
import com.moneytracker.app.data.FinanceSnapshot
import com.moneytracker.app.data.TxDraft
import com.moneytracker.app.data.db.MoneyTransaction
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.domain.FinanceException
import com.moneytracker.app.domain.Ledger
import com.moneytracker.app.ui.AppViewModelProvider
import com.moneytracker.app.i18n.S
import com.moneytracker.app.ui.LocalMoney
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.*
import com.moneytracker.app.ui.theme.AmountStyle
import com.moneytracker.app.ui.theme.FinanceTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

data class AddEditState(
    val loaded: Boolean = false,
    val editing: MoneyTransaction? = null,
    val type: TxType = TxType.EXPENSE,
    val amountText: String = "",
    /** EXPENSE / TRANSFER source. null = Available. */
    val sourceId: Long? = null,
    /** INCOME / TRANSFER destination. null = Available. */
    val destId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val description: String = "",
    val note: String = "",
    val error: String? = null,
    val saving: Boolean = false,
)

class AddEditViewModel(handle: SavedStateHandle, private val container: AppContainer) : ViewModel() {
    private val finance = container.finance
    private val editId: Long? = handle.get<Long>("txId")?.takeIf { it > 0 }

    var state by mutableStateOf(AddEditState())
        private set

    val snapshot: StateFlow<FinanceSnapshot?> =
        finance.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            if (editId != null) {
                val t = finance.getTransaction(editId)
                if (t == null) {
                    state = state.copy(loaded = true, error = S.txNotExists)
                    return@launch
                }
                val dt = Dates.localDateTime(t.timestamp)
                state = AddEditState(
                    loaded = true,
                    editing = t,
                    type = t.type,
                    amountText = container.settings.settings.value.formatter.input(t.amount),
                    sourceId = if (t.type == TxType.INCOME) null else t.categoryId,
                    destId = when (t.type) {
                        TxType.INCOME -> t.categoryId
                        TxType.TRANSFER -> t.toCategoryId
                        TxType.EXPENSE -> null
                    },
                    date = dt.toLocalDate(),
                    time = dt.toLocalTime().withSecond(0).withNano(0),
                    description = t.description,
                    note = t.note,
                )
            } else {
                val type = handle.get<String>("type")?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { TxType.valueOf(it) }.getOrNull() } ?: TxType.EXPENSE
                val from = handle.get<Long>("from") ?: Routes.UNSET
                val to = handle.get<Long>("to") ?: Routes.UNSET
                fun decode(v: Long): Long? = if (v > 0) v else null
                val snap = finance.currentSnapshot()
                val firstCat = snap.activeCategories.firstOrNull()?.id
                val source = if (from == Routes.UNSET) null else decode(from)
                var dest = if (to == Routes.UNSET) {
                    if (type == TxType.TRANSFER) (if (source == null) firstCat else null) else null
                } else decode(to)
                if (type == TxType.TRANSFER && dest == source) {
                    dest = snap.activeCategories.firstOrNull { it.id != source }?.id
                }
                state = AddEditState(loaded = true, type = type, sourceId = source, destId = dest)
            }
        }
    }

    fun setType(t: TxType) {
        var s = state.copy(type = t, error = null)
        if (t == TxType.TRANSFER && s.sourceId == s.destId) {
            val snap = snapshot.value
            s = s.copy(destId = snap?.activeCategories?.firstOrNull { it.id != s.sourceId }?.id)
        }
        state = s
    }

    fun setAmount(v: String) {
        if (v.length <= 18) state = state.copy(amountText = v, error = null)
    }

    fun setSource(id: Long?) { state = state.copy(sourceId = id, error = null) }
    fun setDest(id: Long?) { state = state.copy(destId = id, error = null) }
    fun setDate(d: LocalDate) { state = state.copy(date = d, error = null) }
    fun setTime(t: LocalTime) { state = state.copy(time = t, error = null) }
    fun setDescription(v: String) { if (v.length <= 80) state = state.copy(description = v) }
    fun setNote(v: String) { if (v.length <= 500) state = state.copy(note = v) }

    fun swap() { state = state.copy(sourceId = state.destId, destId = state.sourceId) }

    /** Draft from current form (amount may be invalid -> null). */
    fun draftOrNull(): TxDraft? {
        val parsed = container.settings.settings.value.formatter.parse(state.amountText)
        val amount = (parsed as? AmountParse.Ok)?.milli ?: return null
        return buildDraft(amount)
    }

    private fun buildDraft(amount: Long): TxDraft {
        val s = state
        val ts = Dates.toMillis(LocalDateTime.of(s.date, s.time))
        return when (s.type) {
            TxType.INCOME -> TxDraft(TxType.INCOME, amount, s.destId, null, s.description, s.note, ts)
            TxType.EXPENSE -> TxDraft(TxType.EXPENSE, amount, s.sourceId, null, s.description, s.note, ts)
            TxType.TRANSFER -> TxDraft(TxType.TRANSFER, amount, s.sourceId, s.destId, s.description, s.note, ts)
        }
    }

    fun save(onDone: () -> Unit) {
        if (state.saving) return
        val fmt = container.settings.settings.value.formatter
        val amount = when (val p = fmt.parse(state.amountText)) {
            is AmountParse.Ok -> p.milli
            is AmountParse.Error -> { state = state.copy(error = p.message); return }
            AmountParse.Empty -> { state = state.copy(error = S.enterAmount); return }
        }
        if (state.type == TxType.TRANSFER && state.sourceId == state.destId) {
            state = state.copy(error = S.chooseTwoPlaces)
            return
        }
        val draft = buildDraft(amount)
        state = state.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val e = state.editing
                if (e == null) finance.addTransaction(draft) else finance.updateTransaction(e.id, draft)
                state = state.copy(saving = false)
                onDone()
            } catch (ex: FinanceException) {
                state = state.copy(saving = false, error = ex.message)
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val e = state.editing ?: return
        viewModelScope.launch {
            try {
                finance.deleteTransaction(e.id)
                onDone()
            } catch (ex: FinanceException) {
                state = state.copy(error = ex.message)
            }
        }
    }
}

@Composable
fun AddEditTransactionScreen(nav: NavController, vm: AddEditViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state = vm.state
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val fc = FinanceTheme.colors
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing != null) S.editTransaction else S.newTransaction) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = S.back)
                    }
                },
                actions = {
                    if (state.editing != null) {
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("deleteTx")) {
                            Icon(Icons.Rounded.Delete, contentDescription = S.deleteTransaction)
                        }
                    }
                },
            )
        },
    ) { padding ->
        val s = snap
        if (!state.loaded || s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LaunchedEffect(Unit) {
            if (state.editing == null) runCatching { focus.requestFocus() }
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val types = listOf(TxType.INCOME to S.income, TxType.EXPENSE to S.expense, TxType.TRANSFER to S.transfer)
                types.forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = state.type == t,
                        onClick = { vm.setType(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size),
                        modifier = Modifier.testTag("type_${t.name}"),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = typeColor(t).copy(alpha = 0.16f),
                            activeContentColor = typeColor(t),
                        ),
                    ) { Text(label) }
                }
            }

            val parse = money.parse(state.amountText)
            OutlinedTextField(
                value = state.amountText,
                onValueChange = vm::setAmount,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("amountField"),
                label = { Text(S.amount) },
                textStyle = AmountStyle.copy(fontSize = 30.sp, color = typeColor(state.type)),
                suffix = { Text(money.currency, style = MaterialTheme.typography.titleMedium) },
                singleLine = true,
                isError = parse is AmountParse.Error,
                supportingText = {
                    if (parse is AmountParse.Error) Text(parse.message)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(18.dp),
            )

            when (state.type) {
                TxType.INCOME -> {
                    PlaceSelector(S.addTo, s, state.destId, state.editing, vm::setDest, "dest")
                    PreviewLine(vm, s, state.destId)
                    QuickDescriptions(S.incomeSuggestions) {
                        vm.setDescription(it)
                    }
                }
                TxType.EXPENSE -> {
                    PlaceSelector(S.payFrom, s, state.sourceId, state.editing, vm::setSource, "source")
                    PreviewLine(vm, s, state.sourceId)
                }
                TxType.TRANSFER -> {
                    PlaceSelector(S.from, s, state.sourceId, state.editing, vm::setSource, "source")
                    PreviewLine(vm, s, state.sourceId)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        FilledTonalIconButton(onClick = vm::swap) {
                            Icon(Icons.Rounded.SwapVert, contentDescription = S.swapFromTo)
                        }
                    }
                    PlaceSelector(S.to, s, state.destId, state.editing, vm::setDest, "dest")
                    PreviewLine(vm, s, state.destId)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = { showDate = true },
                    label = { Text(state.date.format(Dates.mediumDate)) },
                    leadingIcon = { Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("dateChip"),
                )
                AssistChip(
                    onClick = { showTime = true },
                    label = { Text(state.time.format(Dates.time)) },
                    leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) },
                )
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = vm::setDescription,
                modifier = Modifier.fillMaxWidth().testTag("descriptionField"),
                label = { Text(if (state.type == TxType.INCOME) S.sourceDescription else S.description) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = vm::setNote,
                modifier = Modifier.fillMaxWidth().testTag("noteField"),
                label = { Text(S.notesOptional) },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
            )

            state.error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("formError"),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Button(
                onClick = { vm.save { nav.popBackStack() } },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("saveButton"),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.editing != null) S.saveChanges else S.save, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val todayUtc = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDate = false
                }) { Text(S.ok) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(S.cancel) } },
        ) { DatePicker(pickerState) }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(state.time.hour, state.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.setTime(LocalTime.of(timeState.hour, timeState.minute))
                    showTime = false
                }) { Text(S.ok) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(S.cancel) } },
            text = { TimeInput(timeState) },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = S.deleteTxTitle,
            message = S.deleteTxMessage,
            confirmLabel = S.delete,
            onConfirm = {
                confirmDelete = false
                vm.delete { nav.popBackStack() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PlaceSelector(
    title: String,
    snapshot: FinanceSnapshot,
    selected: Long?,
    editing: MoneyTransaction?,
    onSelect: (Long?) -> Unit,
    tagPrefix: String,
) {
    val money = LocalMoney.current
    val balances = snapshot.balances
    val cats = snapshot.categories.filter { c ->
        !c.archived || c.id == selected || (editing != null && (editing.categoryId == c.id || editing.toCategoryId == c.id))
    }
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("${S.available} · ${money.format(balances.available)}") },
                leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("${tagPrefix}_available"),
            )
            cats.forEach { c ->
                FilterChip(
                    selected = selected == c.id,
                    onClick = { onSelect(c.id) },
                    label = { Text("${c.name} · ${money.format(balances.of(c.id))}") },
                    leadingIcon = {
                        Icon(CategoryIcons.get(c.icon), null, Modifier.size(18.dp), tint = Color(c.color))
                    },
                    modifier = Modifier.testTag("${tagPrefix}_${c.name}"),
                )
            }
        }
        if (cats.isEmpty()) {
            Text(
                S.tipCreateCategories,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shows how the selected pool's balance changes after saving: "Food: 85.000 → 70.000 OMR". */
@Composable
private fun PreviewLine(vm: AddEditViewModel, snapshot: FinanceSnapshot, pool: Long?) {
    val money = LocalMoney.current
    val draft = vm.draftOrNull() ?: return
    val current = snapshot.balances.of(pool)
    val old = vm.state.editing?.let { Ledger.effectOn(it, pool) } ?: 0L
    val fake = MoneyTransaction(
        type = draft.type, amount = draft.amount, categoryId = draft.categoryId,
        toCategoryId = draft.toCategoryId, timestamp = draft.timestamp,
    )
    val after = current - old + Ledger.effectOn(fake, pool)
    if (after == current) return
    val negative = after < 0
    Text(
        "${placeName(snapshot, pool)}: ${money.format(current)} ${S.arrow} ${money.format(after)}" +
            if (negative) S.notEnoughSuffix else "",
        style = MaterialTheme.typography.bodySmall,
        color = if (negative) FinanceTheme.colors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("preview_${pool ?: -1}"),
    )
}

@Composable
private fun QuickDescriptions(options: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> SuggestionChip(onClick = { onPick(o) }, label = { Text(o) }) }
    }
}
