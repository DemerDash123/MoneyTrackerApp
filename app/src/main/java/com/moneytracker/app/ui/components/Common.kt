package com.moneytracker.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneytracker.app.core.Dates
import com.moneytracker.app.data.FinanceSnapshot
import com.moneytracker.app.data.db.Category
import com.moneytracker.app.data.db.MoneyTransaction
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.ui.LocalMoney
import com.moneytracker.app.i18n.S
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.moneytracker.app.ui.theme.FinanceTheme
import java.time.YearMonth

val CardShape = RoundedCornerShape(22.dp)

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = CardShape
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(contentPadding),
        content = content,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (FinanceTheme.colors.isDark) 0.22f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun CategoryBadge(category: Category?, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    if (category == null) {
        IconBadge(Icons.Rounded.AccountBalanceWallet, FinanceTheme.colors.available, modifier, size)
    } else {
        IconBadge(CategoryIcons.get(category.icon), Color(category.color), modifier, size)
    }
}

fun placeName(snapshot: FinanceSnapshot, id: Long?): String {
    if (id == null) return S.available
    val c = snapshot.category(id) ?: return S.unknown
    return if (c.archived) S.deletedName(c.name) else c.name
}

fun categoryColor(snapshot: FinanceSnapshot, id: Long?, availableColor: Color): Color =
    snapshot.category(id)?.let { Color(it.color) } ?: availableColor

fun transactionTitle(t: MoneyTransaction, snapshot: FinanceSnapshot): String {
    if (t.description.isNotBlank()) return t.description
    return when (t.type) {
        TxType.INCOME -> S.typeName(TxType.INCOME)
        TxType.EXPENSE -> if (t.categoryId != null) placeName(snapshot, t.categoryId) else S.typeName(TxType.EXPENSE)
        TxType.TRANSFER -> when {
            t.categoryId == null -> S.allocatedTo(placeName(snapshot, t.toCategoryId))
            t.toCategoryId == null -> S.movedToAvailable
            else -> S.typeName(TxType.TRANSFER)
        }
    }
}

fun transactionSubtitle(t: MoneyTransaction, snapshot: FinanceSnapshot): String = when (t.type) {
    TxType.INCOME -> S.incomeTo(placeName(snapshot, t.categoryId))
    TxType.EXPENSE -> S.expenseFrom(placeName(snapshot, t.categoryId))
    TxType.TRANSFER -> "${placeName(snapshot, t.categoryId)} ${S.arrow} ${placeName(snapshot, t.toCategoryId)}"
}

@Composable
fun typeColor(type: TxType): Color = when (type) {
    TxType.INCOME -> FinanceTheme.colors.income
    TxType.EXPENSE -> FinanceTheme.colors.expense
    TxType.TRANSFER -> FinanceTheme.colors.transfer
}

fun typeIcon(type: TxType): ImageVector = when (type) {
    TxType.INCOME -> Icons.Rounded.ArrowDownward
    TxType.EXPENSE -> Icons.Rounded.ArrowUpward
    TxType.TRANSFER -> Icons.Rounded.SwapHoriz
}

@Composable
fun TransactionRow(
    t: MoneyTransaction,
    snapshot: FinanceSnapshot,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    /** When set, the amount is shown signed from this pool's point of view (category history). */
    perspectiveOf: Long? = null,
    usePerspective: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val money = LocalMoney.current
    val category = when (t.type) {
        TxType.TRANSFER -> snapshot.category(t.toCategoryId ?: t.categoryId)
        else -> snapshot.category(t.categoryId)
    }
    val dt = Dates.localDateTime(t.timestamp)
    val whenText = if (showDate) "${dt.toLocalDate().format(Dates.shortDate)} · ${dt.toLocalTime().format(Dates.time)}"
    else dt.toLocalTime().format(Dates.time)

    val (amountText, amountColor) = if (usePerspective) {
        val effect = com.moneytracker.app.domain.Ledger.effectOn(t, perspectiveOf)
        val c = when {
            t.type == TxType.EXPENSE -> FinanceTheme.colors.expense
            effect >= 0 -> FinanceTheme.colors.income
            else -> FinanceTheme.colors.transfer
        }
        money.signed(effect, effect >= 0) to c
    } else when (t.type) {
        TxType.INCOME -> money.signed(t.amount, true) to FinanceTheme.colors.income
        TxType.EXPENSE -> money.signed(t.amount, false) to FinanceTheme.colors.expense
        TxType.TRANSFER -> money.format(t.amount) to FinanceTheme.colors.transfer
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (t.type == TxType.TRANSFER) {
                IconBadge(Icons.Rounded.SwapHoriz, FinanceTheme.colors.transfer)
            } else if (category != null) {
                CategoryBadge(category)
            } else {
                IconBadge(typeIcon(t.type), typeColor(t.type))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                transactionTitle(t, snapshot),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${transactionSubtitle(t, snapshot)} · $whenText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(amountText, color = amountColor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(height))
                .background(color),
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 64.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun MonthSwitcher(
    month: YearMonth,
    onChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = S.previousMonth)
        }
        Text(
            month.format(Dates.monthYear),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)
                .padding(vertical = 8.dp),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = S.nextMonth)
        }
    }
}

/** Year + 12-month grid picker. */
@Composable
fun MonthPickerDialog(initial: YearMonth, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit) {
    var year by remember { mutableIntStateOf(initial.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, S.previousYear) }
                Text(year.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, S.nextYear) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 3) {
                            val m = row * 3 + col + 1
                            val ym = YearMonth.of(year, m)
                            val selected = ym == initial
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { onPick(ym) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    ym.format(Dates.shortMonth),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirmButton")) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
    )
}

/** A small "label / value" block used in summary cards. */
@Composable
fun StatBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueStyle: TextStyle = MaterialTheme.typography.titleMedium,
    tag: String? = null,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(
            value, style = valueStyle, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
        )
    }
}

/** Horizontal bar made of weighted coloured segments (e.g. how money is allocated). */
@Composable
fun SegmentedBar(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val total = segments.sumOf { it.first.toDouble() }.toFloat()
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (total > 0f) {
            segments.filter { it.first > 0f }.forEach { (w, c) ->
                Box(Modifier.weight(w / total).fillMaxHeight().background(c))
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier, value: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (value != null) {
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
