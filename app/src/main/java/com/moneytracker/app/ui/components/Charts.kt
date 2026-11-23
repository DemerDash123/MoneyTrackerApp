package com.moneytracker.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneytracker.app.ui.theme.FinanceTheme
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class ChartSlice(val label: String, val value: Long, val color: Color)

/**
 * Interactive donut chart. Tap a slice to select it (tap again to clear).
 * [center] is drawn in the hole and usually shows the selected slice's details.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    thickness: Dp = 26.dp,
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value }.toFloat()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices.map { it.value }) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size).testTag("donutChart"), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(size)
                .pointerInput(slices) {
                    detectTapGestures { p ->
                        if (total <= 0f) return@detectTapGestures
                        val c = Offset(this.size.width / 2f, this.size.height / 2f)
                        val dist = hypot(p.x - c.x, p.y - c.y)
                        val outer = min(this.size.width, this.size.height) / 2f
                        val inner = outer - thickness.toPx() - 10.dp.toPx()
                        if (dist < inner || dist > outer + 4.dp.toPx()) {
                            onSelect(null)
                            return@detectTapGestures
                        }
                        var angle = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        var acc = 0f
                        slices.forEachIndexed { i, s ->
                            val sweep = s.value / total * 360f
                            if (angle >= acc && angle < acc + sweep) {
                                onSelect(if (selected == i) null else i)
                                return@detectTapGestures
                            }
                            acc += sweep
                        }
                    }
                },
        ) {
            val stroke = thickness.toPx()
            val pad = 6.dp.toPx()
            val diameter = min(this.size.width, this.size.height) - stroke - pad * 2
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total <= 0f) return@Canvas
            val gap = if (slices.size > 1) 1.6f else 0f
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = s.value / total * 360f * progress.value
                val isSel = selected == i
                val alpha = if (selected == null || isSel) 1f else 0.35f
                val w = if (isSel) stroke + pad * 1.4f else stroke
                if (sweep > gap) {
                    drawArc(
                        color = s.color.copy(alpha = alpha),
                        startAngle = start + gap / 2,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(w, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

data class BarSeries(val name: String, val color: Color)
data class BarGroup(val label: String, val values: List<Long>)

/**
 * Grouped bar chart with axis, gridlines and a tooltip for the tapped group.
 */
@Composable
fun BarChart(
    groups: List<BarGroup>,
    series: List<BarSeries>,
    formatValue: (Long) -> String,
    formatAxis: (Long) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    initiallySelected: Int? = null,
    testTag: String = "barChart",
) {
    var selected by remember(groups) { mutableStateOf(initiallySelected) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(groups) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = FinanceTheme.colors.chartGrid
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .testTag(testTag)
            .pointerInput(groups) {
                detectTapGestures { p ->
                    val left = 44.dp.toPx()
                    val plotW = this.size.width - left
                    if (groups.isEmpty() || p.x < left) {
                        selected = null
                        return@detectTapGestures
                    }
                    val idx = ((p.x - left) / (plotW / groups.size)).toInt().coerceIn(0, groups.size - 1)
                    selected = if (selected == idx) null else idx
                }
            },
    ) {
        val left = 44.dp.toPx()
        val bottom = 22.dp.toPx()
        val top = 8.dp.toPx()
        val plotW = size.width - left
        val plotH = size.height - bottom - top
        val maxV = max(1L, groups.maxOfOrNull { g -> g.values.maxOrNull() ?: 0L } ?: 0L)
        val niceMax = niceCeil(maxV)
        val axisStyle = TextStyle(color = labelColor, fontSize = 10.sp)

        // grid + axis labels
        for (i in 0..4) {
            val v = niceMax * i / 4
            val y = top + plotH - plotH * i / 4f
            drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 1f,
                pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val t = measurer.measure(formatAxis(v), axisStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), y - t.size.height / 2f))
        }
        if (groups.isEmpty()) return@Canvas
        val groupW = plotW / groups.size
        val seriesCount = max(1, series.size)
        val barW = min(groupW * 0.72f / seriesCount, 22.dp.toPx())
        val labelEvery = max(1, ceil(groups.size * 34.dp.toPx() / plotW).toInt())

        groups.forEachIndexed { gi, g ->
            val gx = left + groupW * gi
            if (selected == gi) {
                drawRoundRect(highlight, Offset(gx + 2f, top), Size(groupW - 4f, plotH), CornerRadius(8.dp.toPx()))
            }
            val totalBarsW = barW * seriesCount
            var bx = gx + (groupW - totalBarsW) / 2f
            g.values.forEachIndexed { si, v ->
                val h = plotH * (v.toFloat() / niceMax) * progress.value
                if (v > 0) {
                    val color = series.getOrNull(si)?.color ?: Color.Gray
                    val alpha = if (selected == null || selected == gi) 1f else 0.45f
                    drawRoundRect(
                        color.copy(alpha = alpha),
                        topLeft = Offset(bx, top + plotH - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(min(barW / 2f, 6.dp.toPx())),
                    )
                }
                bx += barW
            }
            if (gi % labelEvery == 0 || selected == gi) {
                val t = measurer.measure(g.label, axisStyle.copy(fontWeight = if (selected == gi) FontWeight.Bold else FontWeight.Normal))
                drawText(t, topLeft = Offset(gx + groupW / 2f - t.size.width / 2f, top + plotH + 5.dp.toPx()))
            }
        }

        selected?.let { si ->
            val g = groups.getOrNull(si) ?: return@let
            val lines = buildList {
                add(g.label)
                g.values.forEachIndexed { i, v -> add("${series.getOrNull(i)?.name ?: ""}: ${formatValue(v)}") }
            }
            drawTooltip(measurer, lines, left + groupW * si + groupW / 2f, top, tooltipBg, tooltipFg, series.map { it.color })
        }
    }
}

/** Line chart with gradient fill and tap-to-inspect points. Values may be negative. */
@Composable
fun LineChart(
    labels: List<String>,
    values: List<Long>,
    color: Color,
    formatValue: (Long) -> String,
    formatAxis: (Long) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    seriesName: String = "",
) {
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = FinanceTheme.colors.chartGrid
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface
    val surface = MaterialTheme.colorScheme.surfaceContainer

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .testTag("lineChart")
            .pointerInput(values) {
                detectTapGestures { p ->
                    val left = 44.dp.toPx()
                    val right = 12.dp.toPx()
                    if (values.isEmpty()) return@detectTapGestures
                    val stepX = if (values.size == 1) 0f else (this.size.width - left - right) / (values.size - 1)
                    val idx = if (stepX == 0f) 0 else ((p.x - left) / stepX).let { kotlin.math.round(it).toInt() }.coerceIn(0, values.size - 1)
                    selected = if (selected == idx) null else idx
                }
            },
    ) {
        val left = 44.dp.toPx()
        val right = 12.dp.toPx()
        val bottom = 22.dp.toPx()
        val top = 10.dp.toPx()
        val plotW = size.width - left - right
        val plotH = size.height - bottom - top
        val axisStyle = TextStyle(color = labelColor, fontSize = 10.sp)
        if (values.isEmpty()) return@Canvas

        val rawMin = min(0L, values.min())
        val rawMax = max(1L, values.max())
        val hi = niceCeil(rawMax)
        val lo = if (rawMin < 0) -niceCeil(-rawMin) else 0L
        val span = (hi - lo).toFloat()
        fun yOf(v: Long) = top + plotH - plotH * ((v - lo) / span)

        for (i in 0..4) {
            val v = lo + (hi - lo) * i / 4
            val y = yOf(v)
            drawLine(grid, Offset(left, y), Offset(size.width - right, y), 1f,
                pathEffect = if (v == 0L) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val t = measurer.measure(formatAxis(v), axisStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), y - t.size.height / 2f))
        }
        val stepX = if (values.size == 1) 0f else plotW / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            val target = yOf(v)
            val base = yOf(0L)
            Offset(left + stepX * i + if (values.size == 1) plotW / 2f else 0f, base + (target - base) * progress.value)
        }
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val cx = (p0.x + p1.x) / 2f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(pts.last().x, top + plotH)
            lineTo(pts.first().x, top + plotH)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)), top, top + plotH))
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

        val labelEvery = max(1, ceil(values.size * 36.dp.toPx() / max(1f, plotW)).toInt())
        pts.forEachIndexed { i, p ->
            val isSel = selected == i
            drawCircle(surface, radius = if (isSel) 7.dp.toPx() else 4.5f.dp.toPx(), center = p)
            drawCircle(color, radius = if (isSel) 5.dp.toPx() else 3.dp.toPx(), center = p)
            if (i % labelEvery == 0 || isSel) {
                val t = measurer.measure(labels.getOrElse(i) { "" }, axisStyle)
                drawText(t, topLeft = Offset(p.x - t.size.width / 2f, top + plotH + 5.dp.toPx()))
            }
        }
        selected?.let { i ->
            val p = pts[i]
            drawLine(color.copy(alpha = 0.4f), Offset(p.x, top), Offset(p.x, top + plotH), 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            val title = labels.getOrElse(i) { "" }
            val line = if (seriesName.isBlank()) formatValue(values[i]) else "$seriesName: ${formatValue(values[i])}"
            drawTooltip(measurer, listOf(title, line), p.x, top, tooltipBg, tooltipFg, listOf(color))
        }
    }
}

private fun DrawScope.drawTooltip(
    measurer: TextMeasurer,
    lines: List<String>,
    anchorX: Float,
    top: Float,
    bg: Color,
    fg: Color,
    dots: List<Color>,
) {
    val titleStyle = TextStyle(color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val bodyStyle = TextStyle(color = fg, fontSize = 11.sp)
    val layouts = lines.mapIndexed { i, s -> measurer.measure(s, if (i == 0) titleStyle else bodyStyle) }
    val padH = 10.dp.toPx()
    val padV = 7.dp.toPx()
    val dotSpace = 12.dp.toPx()
    val w = layouts.mapIndexed { i, l -> l.size.width + if (i == 0) 0f else dotSpace }.max() + padH * 2
    val h = layouts.sumOf { it.size.height } + padV * 2
    val x = (anchorX - w / 2f).coerceIn(0f, max(0f, size.width - w))
    val y = top
    drawRoundRect(bg.copy(alpha = 0.94f), Offset(x, y), Size(w, h.toFloat()), CornerRadius(10.dp.toPx()))
    var cy = y + padV
    layouts.forEachIndexed { i, l ->
        if (i == 0) {
            drawText(l, topLeft = Offset(x + padH, cy))
        } else {
            drawCircle(dots.getOrElse(i - 1) { fg }, 4.dp.toPx(), Offset(x + padH + 4.dp.toPx(), cy + l.size.height / 2f))
            drawText(l, topLeft = Offset(x + padH + dotSpace, cy))
        }
        cy += l.size.height
    }
}

/** Rounds up to a "nice" axis maximum (1, 2, 2.5, 5 x 10^n). Works in milli-units. */
fun niceCeil(v: Long): Long {
    if (v <= 0) return 1000
    val units = v / 1000.0
    if (units <= 1.0) return 1000
    val exp = 10.0.pow(kotlin.math.floor(log10(units)))
    val f = units / exp
    val nice = when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 2.5 -> 2.5
        f <= 5.0 -> 5.0
        else -> 10.0
    }
    return (nice * exp * 1000).toLong()
}
