package com.moneytracker.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.moneytracker.app.AppContainer
import com.moneytracker.app.core.AmountParse
import com.moneytracker.app.core.Dates
import com.moneytracker.app.data.CategoryInput
import com.moneytracker.app.domain.FinanceException
import com.moneytracker.app.ui.AppViewModelProvider
import com.moneytracker.app.i18n.S
import com.moneytracker.app.ui.LocalMoney
import com.moneytracker.app.ui.components.CategoryIcons
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class CategoryForm(
    val loaded: Boolean = false,
    val name: String = "",
    val icon: String = "category",
    val color: Int = CategoryIcons.colors.first(),
    val limitText: String = "",
    val targetText: String = "",
    val targetDate: LocalDate? = null,
    val initialText: String = "",
    val error: String? = null,
    val saving: Boolean = false,
)

class CategoryEditViewModel(handle: SavedStateHandle, private val container: AppContainer) : ViewModel() {
    val editId: Long? = handle.get<Long>("id")?.takeIf { it > 0 }
    val goalMode: Boolean = handle.get<Boolean>("goal") ?: false

    var form by mutableStateOf(CategoryForm())
        private set

    val available = container.finance.snapshot.map { it.balances.available }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        viewModelScope.launch {
            val fmt = container.settings.settings.value.formatter
            val id = editId
            if (id == null) {
                val used = container.finance.currentSnapshot().categories.size
                form = CategoryForm(
                    loaded = true,
                    icon = if (goalMode) "star" else "category",
                    color = CategoryIcons.colors[used % CategoryIcons.colors.size],
                )
            } else {
                val c = container.finance.getCategory(id)
                form = if (c == null) CategoryForm(loaded = true, error = S.categoryNotExists)
                else CategoryForm(
                    loaded = true,
                    name = c.name,
                    icon = c.icon,
                    color = c.color,
                    limitText = c.monthlyLimit?.let { fmt.input(it) } ?: "",
                    targetText = c.targetAmount?.let { fmt.input(it) } ?: "",
                    targetDate = c.targetDate?.let { LocalDate.ofEpochDay(it) },
                )
            }
        }
    }

    fun update(f: (CategoryForm) -> CategoryForm) {
        form = f(form).copy(error = null)
    }

    fun save(onDone: () -> Unit) {
        val fmt = container.settings.settings.value.formatter
        fun optional(text: String, label: String): Result<Long?> = when (val p = fmt.parse(text)) {
            AmountParse.Empty -> Result.success(null)
            is AmountParse.Ok -> Result.success(p.milli)
            is AmountParse.Error -> Result.failure(FinanceException("$label: ${p.message}"))
        }
        try {
            val limit = optional(form.limitText, S.monthlyLimit).getOrThrow()
            val target = optional(form.targetText, S.target).getOrThrow()
            val initial = optional(form.initialText, S.startingAmount).getOrThrow() ?: 0L
            if (goalMode && target == null) throw FinanceException(S.enterTargetForGoal)
            val input = CategoryInput(form.name, form.icon, form.color, limit, target, if (target != null) form.targetDate else null)
            form = form.copy(saving = true, error = null)
            viewModelScope.launch {
                try {
                    val id = editId
                    if (id == null) container.finance.createCategory(input, initial)
                    else container.finance.updateCategory(id, input)
                    onDone()
                } catch (e: FinanceException) {
                    form = form.copy(saving = false, error = e.message)
                }
            }
        } catch (e: FinanceException) {
            form = form.copy(error = e.message)
        }
    }
}

@Composable
fun CategoryEditScreen(nav: NavController, vm: CategoryEditViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val f = vm.form
    val money = LocalMoney.current
    val available by vm.available.collectAsStateWithLifecycle()
    var showDate by remember { mutableStateOf(false) }
    val title = when {
        vm.editId != null -> S.editCategory
        vm.goalMode -> S.newGoal
        else -> S.newCategory
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = S.back)
                    }
                },
            )
        },
    ) { padding ->
        if (!f.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Color(f.color)),
                    contentAlignment = Alignment.Center,
                ) { Icon(CategoryIcons.get(f.icon), null, tint = Color.White) }
                Spacer(Modifier.width(14.dp))
                OutlinedTextField(
                    value = f.name,
                    onValueChange = { v -> if (v.length <= 40) vm.update { it.copy(name = v) } },
                    label = { Text(if (vm.goalMode) S.goalNameHint else S.name) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("categoryName"),
                    shape = RoundedCornerShape(16.dp),
                )
            }

            Text(S.icon, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryIcons.all.forEach { (key, icon) ->
                    val sel = f.icon == key
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) Color(f.color) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { vm.update { it.copy(icon = key) } }
                            .testTag("icon_$key"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = key, tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(S.color, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryIcons.colors.forEach { col ->
                    val sel = f.color == col
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(col))
                            .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { vm.update { it.copy(color = col) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            AmountInput(
                value = f.targetText,
                onChange = { v -> vm.update { it.copy(targetText = v) } },
                label = if (vm.goalMode) S.targetAmount else S.savingsTargetOptional,
                tag = "targetField",
            )
            if (f.targetText.isNotBlank()) {
                AssistChip(
                    onClick = { showDate = true },
                    label = { Text(f.targetDate?.let { S.targetDateColon(it.format(Dates.mediumDate)) } ?: S.addTargetDate) },
                    leadingIcon = { Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (f.targetDate != null) {
                            Icon(
                                Icons.Rounded.Close, S.clearDate,
                                Modifier.size(18.dp).clickable { vm.update { it.copy(targetDate = null) } },
                            )
                        }
                    },
                )
            }
            AmountInput(
                value = f.limitText,
                onChange = { v -> vm.update { it.copy(limitText = v) } },
                label = S.monthlyLimitOptional,
                tag = "limitField",
            )
            if (vm.editId == null) {
                AmountInput(
                    value = f.initialText,
                    onChange = { v -> vm.update { it.copy(initialText = v) } },
                    label = S.allocateNowOptional,
                    tag = "initialField",
                    supporting = S.availableColon(money.format(available)),
                )
            }

            f.error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("categoryError")) {
                    Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            Button(
                onClick = { vm.save { nav.popBackStack() } },
                enabled = !f.saving,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("saveCategory"),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (vm.editId != null) S.saveChanges else S.create) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (f.targetDate ?: LocalDate.now().plusMonths(3))
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        vm.update { it.copy(targetDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) }
                    }
                    showDate = false
                }) { Text(S.ok) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(S.cancel) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun AmountInput(value: String, onChange: (String) -> Unit, label: String, tag: String, supporting: String? = null) {
    val money = LocalMoney.current
    val parse = money.parse(value)
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 18) onChange(it) },
        label = { Text(label) },
        suffix = { Text(money.currency) },
        singleLine = true,
        isError = parse is AmountParse.Error,
        supportingText = {
            when {
                parse is AmountParse.Error -> Text(parse.message)
                supporting != null -> Text(supporting)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(16.dp),
    )
}
