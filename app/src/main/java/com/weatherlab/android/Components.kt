package com.weatherlab.android

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Ported from the AZ Weather Lab phone app's widgets.dart (read only reference).
// Same shapes, padding and borders: Panel, Tile, Pill, Section, Note, EmptyCard, ErrorBox, LoadingCard, infoRow, listPanel.

private fun Color.a(alpha255: Int) = copy(alpha = alpha255 / 255f)

/**
 * The card behind most content (0.7.18): card colour, 20 dp corners, 16 dp padding, a soft shadow and a faint edge.
 * [border] draws a stronger 1.5 dp edge (warnings); [brush] lays a tint over the card (the weather on Home).
 */
@Composable
fun Panel(modifier: Modifier = Modifier, border: Color? = null, brush: Brush? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(S.lg)
    Box(
        modifier
            .fillMaxWidth()
            .softShadow(shape)
            .clip(shape)
            .background(C.card)
            .then(if (brush != null) Modifier.background(brush) else Modifier)
            .border(BorderStroke(if (border != null) 1.5.dp else 1.dp, border ?: C.line.copy(alpha = 0.6f)), shape)
            .padding(16.dp),
    ) { content() }
}

/** A light shadow under cards: stronger on the light scheme, where it shows; barely there on navy. */
@Composable
internal fun Modifier.softShadow(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val light = LocalPal.current.light
    return shadow(
        elevation = if (light) 6.dp else 3.dp, shape = shape, clip = false,
        ambientColor = Color.Black.copy(alpha = if (light) 0.06f else 0.3f),
        spotColor = Color.Black.copy(alpha = if (light) 0.10f else 0.4f),
    )
}

private const val SHOW_TILE_ACCENT = true

// Measure colours from the dashboard tiles. A tile for one of these measures gets a coloured left edge.
private fun measureAccent(label: String): Color? = when (label) {
    "Wind" -> Color(0xFF8FD3C0)
    "Gust" -> Color(0xFF10B981)
    "Humidity" -> Color(0xFF6E78AE)
    "Indoor humidity" -> Color(0xFF6E78AE)
    "Indoor temperature" -> Color(0xFFE8833A)
    "Rain today" -> Color(0xFF4F9CF7)
    "Dew point" -> Color(0xFF4F9CF7)
    "Pressure" -> Color(0xFFA78BFA)
    "Rain rate" -> Color(0xFF8A5CF0)
    "UV index" -> Color(0xFFF6D55C)
    "Solar" -> Color(0xFFF4A259)
    else -> null
}

/** One reading: small label, big figure, optional small line under it. 16 dp corners, accent edge for known measures. */
@Composable
fun Tile(
    label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, color: Color? = null,
    trend: List<Double>? = null, onClick: (() -> Unit)? = null, range: String? = null,
) {
    // Tiles for known measures get a 4 dp coloured left edge and, when given, a 24-hour line, as on the phone app.
    val accent = if (SHOW_TILE_ACCENT) measureAccent(label) else null
    val shape = RoundedCornerShape(S.md)
    val term = termForLabel(label)
    var showTerm by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp)) {
            if (term == null) {
                Text(label, style = T.label, color = C.muted)
            } else {
                // The label and its ? open the explanation together, so the tap target is wider than the ring.
                Row(
                    Modifier.clickable { showTerm = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(label, style = T.label, color = C.muted)
                    TermHelpMark(term)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = T.value, color = color ?: C.text)
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, style = T.label, color = C.muted)
            }
            if (trend != null && accent != null) {
                Spacer(Modifier.height(8.dp))
                TrendSpark(trend, accent, height = 48.dp)
                if (range != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(range, style = T.label, color = C.muted)
                }
            }
        }
    }
    Box(
        modifier
            .softShadow(shape)
            .clip(shape)
            .background(C.card)
            .border(BorderStroke(1.dp, C.line.copy(alpha = 0.6f)), shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Show history", onClick = onClick) else Modifier)
            .then(
                if (term == null) Modifier.clearAndSetSemantics { contentDescription = "$label: $value${if (sub == null) "" else ", $sub"}" }
                else Modifier.semantics(mergeDescendants = true) {},
            ),
    ) {
        if (accent == null) content()
        else Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Box(Modifier.weight(1f)) { content() }
        }
    }
    if (term != null && showTerm) TermSheet(term) { showTerm = false }
}

/** Two tiles per row, as on the phone app held upright. Rows share the height of their tallest tile. */
@Composable
fun TileGrid(items: List<@Composable (Modifier) -> Unit>, perRow: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item -> item(Modifier.weight(1f).fillMaxHeight()) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A heading above a group of cards: 22 dp above, 8 dp below, optional "updated 3 min ago" chip. */
@Composable
fun Section(text: String, updatedMinutes: Int? = null) {
    Row(
        Modifier.padding(top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = T.section, color = C.text)
        if (updatedMinutes != null) FreshChip(updatedMinutes)
    }
}

/** Green under 15 minutes, amber under an hour, red after that (same rule as the dashboard). */
@Composable
fun FreshChip(mins: Int) {
    val color = if (mins < 15) C.good else if (mins < 60) C.warn else C.bad
    val txt = when {
        mins < 1 -> "updated just now"
        mins < 60 -> "updated $mins min ago"
        else -> "updated %.1f h ago".format(mins / 60.0)
    }
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(color.a(45)).padding(horizontal = 9.dp, vertical = 2.dp)) {
        Text(txt, color = color, fontSize = 12.sp, fontWeight = FontWeight.W600)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(color.a(45)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = T.pill, color = color)
    }
}

/** The one style for every notice: offline, stale data, warnings, errors. The icon follows the colour. */
@Composable
fun Note(text: String, color: Color, icon: ImageVector? = null) {
    val ic = icon ?: when (color) {
        C.bad -> Icons.Outlined.ErrorOutline
        C.warn -> Icons.Rounded.WarningAmber
        C.good -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.Info
    }
    Row(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.a(35))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(ic, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = T.note, color = color, modifier = Modifier.weight(1f))
    }
}

/** The one "nothing here yet" card: dashed grey outline, light grey fill, 12 dp corners. */
@Composable
fun EmptyCard(text: String, icon: ImageVector = Icons.Outlined.Inbox) {
    val dash = Color(0x73808080)
    Row(
        Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14808080))
            .drawBehind {
                drawRoundRect(
                    color = dash,
                    topLeft = Offset(0.5f, 0.5f),
                    size = Size(size.width - 1f, size.height - 1f),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
                )
            }
            .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = C.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = T.note, color = C.muted, modifier = Modifier.weight(1f))
    }
}

/** The one "could not load" card, with a retry button and a red edge. */
@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, title: String = "Could not load this page") {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.card),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(C.bad))
            Column(Modifier.weight(1f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = C.bad, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(title, style = T.heading, color = C.text, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(message, style = T.label, color = C.muted)
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

/** The one loading card: pulsing bars where the content will go. */
@Composable
fun LoadingCard() {
    val pulse = rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0.35f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(S.md)).background(C.card).padding(16.dp)) {
        Column(Modifier.alpha(pulse.value), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0.5f, 1f, 0.8f).forEach { w ->
                Box(Modifier.fillMaxWidth(w).height(12.dp).clip(RoundedCornerShape(6.dp)).background(C.bar))
            }
        }
    }
}

/** A row with a label on the left and a value on the right, optional small text under the label. */
@Composable
fun InfoRow(left: String, right: String, sub: String? = null, rightColor: Color? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(left, style = T.body, color = C.text)
            if (sub != null) Text(sub, style = T.label, color = C.muted)
        }
        if (right.isNotEmpty()) Text(right, style = T.figure, color = rightColor ?: C.text)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Rows inside one Panel with thin dividers between them. */
@Composable
fun ListPanel(rows: List<@Composable () -> Unit>) {
    Panel {
        Column {
            rows.forEachIndexed { i, row ->
                row()
                if (i < rows.lastIndex) HorizontalDivider(thickness = 1.dp, color = C.line)
            }
        }
    }
}

/** A small line of the last 24 hours for a tile: 22 dp tall, 2 dp stroke, a dot on the newest reading. Blank until data arrives. */
@Composable
fun TrendSpark(values: List<Double>, color: Color, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 22.dp) {
    val box = modifier.fillMaxWidth().height(height)
    if (values.size < 2) {
        Spacer(box)
        return
    }
    Canvas(box) {
        val lo = values.min()
        val span = (values.max() - lo).let { if (it < 1e-9) 1.0 else it }
        val n = values.size
        fun x(i: Int) = i * size.width / (n - 1)
        fun y(i: Int) = ((size.height - 3.dp.toPx()) - ((values[i] - lo) / span * (size.height - 6.dp.toPx()))).toFloat()
        val path = Path().apply {
            moveTo(x(0), y(0))
            for (i in 1 until n) lineTo(x(i), y(i))
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(x(n - 1), y(n - 1)))
    }
}
