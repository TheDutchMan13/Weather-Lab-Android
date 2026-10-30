package com.weatherlab.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val POINT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, h a", Locale.US)
private val EVENT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.US)

internal fun pointTime(seconds: Long): String =
    Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).format(POINT_FORMAT)

private fun one(v: Double?): String = if (v == null) "--" else "%.1f".format(Locale.US, v)

private fun indexAt(x: Float, width: Int, n: Int): Int =
    if (n <= 1 || width <= 0) 0 else ((x / width) * (n - 1)).roundToInt().coerceIn(0, n - 1)

// ---------------------------------------------------------------------------------------------------------------------
// Touch charts: touch or drag to read a value.

@Composable
fun TouchLine(values: List<Double>, labels: List<String>, unit: String, height: Dp = 140.dp) {
    if (values.size < 2) {
        Text("Not enough readings yet.", style = T.body, color = C.muted)
        return
    }
    val n = values.size
    var picked by remember(values) { mutableIntStateOf(-1) }
    val shown = if (picked in 0 until n) picked else n - 1
    val line = C.quiet
    val marker = C.cyan
    val dot = C.text
    Text("${labels[shown]}: ${one(values[shown])}$unit", style = T.body, color = C.text)
    Text(
        if (picked < 0) "Latest reading. Touch and drag the chart to read other values." else "Drag to read other values.",
        style = T.label, color = C.muted,
    )
    Spacer(Modifier.height(6.dp))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(values) { detectTapGestures { o -> picked = indexAt(o.x, size.width, n) } }
            .pointerInput(values) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> picked = indexAt(o.x, size.width, n) },
                    onHorizontalDrag = { change, _ -> picked = indexAt(change.position.x, size.width, n) },
                )
            },
    ) {
        val lo = values.min()
        val hi = values.max()
        val span = if (hi - lo < 1) 1.0 else hi - lo
        val pad = 10.dp.toPx()
        val h = size.height - 2 * pad
        fun xAt(i: Int) = i * size.width / (n - 1)
        fun yAt(i: Int) = (pad + h * (1 - (values[i] - lo) / span)).toFloat()
        val path = Path()
        for (i in 0 until n) if (i == 0) path.moveTo(xAt(i), yAt(i)) else path.lineTo(xAt(i), yAt(i))
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(marker, Offset(xAt(shown), 0f), Offset(xAt(shown), size.height), strokeWidth = 1.5.dp.toPx())
        drawCircle(dot, radius = 5.dp.toPx(), center = Offset(xAt(shown), yAt(shown)))
    }
}

@Composable
fun TouchBars(values: List<Double?>, labels: List<String>, unit: String, height: Dp = 110.dp, topLabel: String = "Most in a day") {
    val n = values.size
    if (n == 0) return
    var picked by remember(values) { mutableIntStateOf(-1) }
    val top = values.filterNotNull().maxOrNull() ?: 0.0
    val bar = C.cyan
    val base = C.line
    val chosen = C.text
    val shown = picked
    val decimals = decimalsFor(unit)
    Text(
        if (shown in 0 until n) "${labels[shown]}: ${withUnit(values[shown] ?: 0.0, unit, decimals)}"
        else "$topLabel: ${withUnit(top, unit, decimals)}",
        style = T.body, color = C.text,
    )
    Text("Touch a bar to read it.", style = T.label, color = C.muted)
    Spacer(Modifier.height(6.dp))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(values) { detectTapGestures { o -> picked = ((o.x / size.width) * n).toInt().coerceIn(0, n - 1) } }
            .pointerInput(values) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> picked = ((o.x / size.width) * n).toInt().coerceIn(0, n - 1) },
                    onHorizontalDrag = { change, _ -> picked = ((change.position.x / size.width) * n).toInt().coerceIn(0, n - 1) },
                )
            },
    ) {
        if (top <= 0.0) return@Canvas
        val slot = size.width / n
        val w = slot * 0.7f
        values.forEachIndexed { i, v ->
            val h = if (v == null) 0f else (v / top * (size.height - 2.dp.toPx())).toFloat()
            val x = i * slot + (slot - w) / 2f
            drawRoundRect(base, Offset(x, size.height - 2.dp.toPx()), Size(w, 2.dp.toPx()), CornerRadius(1f))
            if (h > 0f) drawRoundRect(if (i == shown) chosen else bar, Offset(x, size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Shared bits

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Chips(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(
                selected = selected == i,
                onClick = { onPick(i) },
                label = { Text(label, color = C.text) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = C.card,
                    selectedContainerColor = C.cyan.copy(alpha = 50 / 255f),
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = selected == i, borderColor = C.line, selectedBorderColor = C.cyan,
                ),
            )
        }
    }
}

internal fun tiles(vararg items: Triple<String, String, String?>): List<@Composable (Modifier) -> Unit> =
    items.map { (label, value, sub) ->
        val item: @Composable (Modifier) -> Unit = { m -> Tile(label, value, m, sub = sub) }
        item
    }

// ---------------------------------------------------------------------------------------------------------------------
// Rankings

@Composable
fun RankingsPage(address: String, token: String) {
    var which by remember { mutableIntStateOf(0) }
    PageColumn {
        Fetch(address, token, "rankings", { it.rankings() }) { list ->
            if (list.isEmpty()) {
                EmptyCard("Rankings appear once there are daily summaries.")
            } else {
                Chips(list.map { it.label }, which.coerceIn(0, list.lastIndex)) { which = it }
                val r = list[which.coerceIn(0, list.lastIndex)]
                Section("Top 10: ${r.label}")
                ListPanel(
                    r.items.mapIndexed { i, item ->
                        val row: @Composable () -> Unit = {
                            InfoRow("${i + 1}.  ${dayLong(item.date)}", withUnit(item.value, r.unit, decimalsFor(r.unit)))
                        }
                        row
                    },
                )
            }
        }
    }
}

private val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

internal fun dayLong(date: String): String = try {
    LocalDate.parse(date).format(LONG_DAY)
} catch (_: Exception) {
    date
}

// ---------------------------------------------------------------------------------------------------------------------
// Rain

@Composable
fun RainCalendarPage(address: String, token: String) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    PageColumn {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Filled.KeyboardArrowLeft, "Earlier month", tint = C.text) }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = T.heading, color = C.text,
            )
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                Icon(Icons.Filled.KeyboardArrowRight, "Later month", tint = if (month < YearMonth.now()) C.text else C.line)
            }
        }
        Fetch(address, token, month, { it.rainMonth(month.toString()) }) { m ->
            val byDay = m.days.associateBy { it.date }
            val first = month.atDay(1)
            val offset = first.dayOfWeek.value % 7 // Sunday first
            val cells: List<LocalDate?> = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            val peak = m.days.mapNotNull { it.rain }.maxOrNull() ?: 0.0
            Panel {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = T.label, color = C.muted)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            for (i in 0 until 7) {
                                val d = week.getOrNull(i)
                                val rain = d?.let { byDay[it.toString()]?.rain }
                                val wet = rain != null && rain >= 0.01
                                val shade = if (wet && peak > 0) 0.25f + 0.55f * (rain!! / peak).toFloat() else 0f
                                Box(
                                    Modifier.weight(1f).padding(2.dp).aspectRatio(0.9f).clip(RoundedCornerShape(8.dp))
                                        .background(if (wet) C.cyan.copy(alpha = shade) else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (d != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${d.dayOfMonth}", fontSize = 13.sp, color = if (rain == null) C.quiet else C.text)
                                        if (wet) Text(withUnit(rain, "", decimalsFor(m.unit)), fontSize = 9.sp, color = C.text)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TileGrid(
                tiles(
                    Triple("Rain this month", withUnit(m.total, m.unit, decimalsFor(m.unit)), null),
                    Triple("Days with rain", m.rainyDays.toString(), null),
                ),
            )
            if (m.years.isNotEmpty()) {
                Section("Every year")
                ListPanel(
                    m.years.map { y ->
                        val row: @Composable () -> Unit = {
                            InfoRow("${y.year}", withUnit(y.total, m.unit, decimalsFor(m.unit)), sub = "${y.rainyDays} days with rain")
                        }
                        row
                    },
                )
            }
        }
    }
}

@Composable
fun RainEventsPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "rainevents", { it.rainEvents() }) { e ->
            Section("Wet spells, last 60 days")
            if (e.events.isEmpty()) {
                EmptyCard("No rain in the last 60 days.")
            } else {
                ListPanel(
                    e.events.map { ev ->
                        val row: @Composable () -> Unit = {
                            InfoRow(
                                left = Instant.ofEpochSecond(ev.start).atZone(ZoneId.systemDefault()).format(EVENT_FORMAT),
                                right = withUnit(ev.rain, e.rainUnit, decimalsFor(e.rainUnit)),
                                sub = "${ev.minutes} min, peak ${withUnit(ev.peakRate, e.rateUnit, decimalsFor(e.rateUnit))}",
                            )
                        }
                        row
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Wind rose and lightning

@Composable
fun WindPage(address: String, token: String) {
    var pick by remember { mutableIntStateOf(1) }
    val periods = listOf(1, 7, 30, null)
    PageColumn {
        Chips(listOf("24 hours", "7 days", "30 days", "All"), pick) { pick = it }
        Fetch(address, token, pick, { it.windRose(periods[pick]) }) { w ->
            if (w.total == 0) {
                EmptyCard("No wind readings in this period yet.")
            } else {
                Spacer(Modifier.height(8.dp))
                TileGrid(
                    tiles(
                        Triple("Most common direction", w.dominant ?: "--", "wind comes from"),
                        Triple("Calm", w.calmPercent?.let { "${one(it)}%" } ?: "--", "under 1 mph, not drawn"),
                        Triple("Average wind", withUnit(w.average, w.unit, 1), null),
                        Triple("Strongest gust", withUnit(w.strongestGust, w.unit, 0), null),
                    ),
                )
                Section("Wind rose")
                Panel { WindRoseChart(w) }
                Section("Top directions")
                ListPanel(
                    w.directions.sortedByDescending { it.percent }.take(5).map { d ->
                        val row: @Composable () -> Unit = { InfoRow(d.name, "${one(d.percent)}%") }
                        row
                    },
                )
            }
        }
    }
}

@Composable
internal fun WindRoseChart(w: WindRose) {
    val measurer = rememberTextMeasurer()
    val ring = C.line
    val label = C.muted
    val fill = C.cyan
    val maxPct = w.directions.maxOfOrNull { it.percent }?.takeIf { it > 0 } ?: 1.0
    Column {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(cx, cy) - 18.dp.toPx()
            for (f in listOf(0.5f, 1f)) drawCircle(ring, radius * f, Offset(cx, cy), style = Stroke(1.dp.toPx()))
            val bins = w.bins.size
            w.directions.forEachIndexed { i, d ->
                val start = i * 22.5f - 90f - 9f
                var cum = d.bins.sum()
                for (k in d.bins.indices.reversed()) {
                    val r = (radius * cum / maxPct).toFloat()
                    val alpha = 0.3f + 0.7f * (k + 1) / bins
                    drawArc(
                        fill.copy(alpha = alpha),
                        startAngle = start, sweepAngle = 18f, useCenter = true,
                        topLeft = Offset(cx - r, cy - r), size = Size(2 * r, 2 * r),
                    )
                    cum -= d.bins[k]
                }
            }
            val style = TextStyle(color = label, fontSize = 12.sp, fontWeight = FontWeight.W700)
            listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (name, deg) ->
                val m = measurer.measure(name, style)
                val rad = Math.toRadians((deg - 90f).toDouble())
                val x = cx + (radius + 10.dp.toPx()) * cos(rad).toFloat() - m.size.width / 2f
                val y = cy + (radius + 10.dp.toPx()) * sin(rad).toFloat() - m.size.height / 2f
                drawText(m, topLeft = Offset(x, y))
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            w.bins.forEachIndexed { k, name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(fill.copy(alpha = 0.3f + 0.7f * (k + 1) / w.bins.size)))
                    Spacer(Modifier.width(5.dp))
                    Text(name, style = T.label, color = C.muted)
                }
            }
        }
        Text(
            "Wedges point to where the wind comes from. Darker is stronger." +
                (w.calmPercent?.let { " Calm (under 1 mph) ${one(it)}% of the time is counted but not drawn." } ?: ""),
            style = T.label, color = C.muted, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun LightningPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "lightning", { it.lightningDays() }) { l ->
            Section("Last 30 days")
            if (l.days.isEmpty()) {
                EmptyCard("No lightning in the last 30 days.")
            } else {
                val oldestFirst = l.days.reversed()
                Panel { TouchBars(oldestFirst.map { it.strikes.toDouble() }, oldestFirst.map { dayName(it.date) }, " strikes") }
                Spacer(Modifier.height(10.dp))
                ListPanel(
                    l.days.map { d ->
                        val row: @Composable () -> Unit = {
                            InfoRow(
                                dayName(d.date), "${d.strikes} strike${if (d.strikes == 1) "" else "s"}",
                                sub = "Closest ${withUnit(d.closest, l.unit, 1)}",
                            )
                        }
                        row
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Sun and UV, heat safety

@Composable
fun SunPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "sun", { it.sun() }) { s ->
            Section("UV and sunshine")
            TileGrid(
                tiles(
                    Triple("UV now", if (s.uvNow == null) "--" else one(s.uvNow), s.uvNowWord),
                    Triple("UV peak today", if (s.uvPeak == null) "--" else one(s.uvPeak), s.uvPeakWord),
                    Triple("Sunshine today", "${one(s.sunshineToday)} h", null),
                    Triple("Sunshine, 7 days", "${one(s.sunshineWeek)} h", null),
                    Triple("Sunrise", s.sunrise ?: "--", null),
                    Triple("Sunset", s.sunset ?: "--", null),
                ),
            )
            Section("Minutes in full sun until sunburn")
            ListPanel(
                s.burnNow.mapIndexed { i, b ->
                    val peak = s.burnPeak.getOrNull(i)?.minutes
                    val row: @Composable () -> Unit = {
                        InfoRow(
                            b.skin, if (b.minutes == null) "--" else "${b.minutes} min",
                            sub = if (peak == null) null else "At today's peak UV: $peak min",
                        )
                    }
                    row
                },
            )
            Text("Rough estimates from your station's UV reading and the WHO's skin types. Sunscreen and shade change them.", style = T.label, color = C.muted)
        }
    }
}

@Composable
fun HeatPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "heat", { it.heat() }) { h ->
            val color = when (h.level) {
                "Extreme danger", "Danger" -> C.bad
                "Extreme caution", "Caution" -> C.warn
                else -> C.good
            }
            Section("Heat index right now")
            Panel(border = if (color == C.good) null else color) {
                Column {
                    Text(h.level, fontSize = 20.sp, fontWeight = FontWeight.W800, color = color)
                    if (h.advice.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(h.advice, style = T.body, color = C.muted)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TileGrid(
                tiles(
                    Triple("Heat index", withUnit(h.heatIndex, h.unit, 0), null),
                    Triple("Air temperature", withUnit(h.temp, h.unit, 0), null),
                    Triple("Peak today", withUnit(h.peakToday, h.unit, 0), h.peakTime),
                    Triple("Hours at Danger or worse", "${fmt(h.dangerHours, 1)} h", "today so far"),
                ),
            )
            if (h.series.size >= 2) {
                Section("Today")
                Panel {
                    Column {
                        HeatChart(
                            temps = h.series.mapNotNull { it.temp },
                            heatIndex = h.series.mapNotNull { it.heatIndex },
                            marks = h.levels.filter { (it.level == "Extreme caution" || it.level == "Danger") && it.from != null }
                                .map { (it.from ?: 0.0) to it.level },
                            unit = h.unit,
                        )
                        Text("Red: air temperature. Blue: heat index. Dotted lines mark where each level starts.", style = T.label, color = C.muted)
                    }
                }
            }
            Section("Hot days in ${h.year}")
            ListPanel(
                listOf(
                    { InfoRow("100 or hotter", "${h.days100}") },
                    { InfoRow("105 or hotter", "${h.days105}") },
                    { InfoRow("110 or hotter", "${h.days110}") },
                ),
            )
            Section("Heat safety levels")
            ListPanel(
                h.levels.reversed().map { l ->
                    val here = l.level == h.level
                    val row: @Composable () -> Unit = {
                        InfoRow(
                            l.level,
                            if (l.from == null) "under ${h.levels.getOrNull(h.levels.indexOf(l) + 1)?.from?.roundToInt() ?: ""}${h.unit}"
                            else "${l.from.roundToInt()}${h.unit} and up",
                            sub = if (here) "You are here. ${l.advice}" else l.advice,
                            rightColor = if (here) color else null,
                        )
                    }
                    row
                },
            )
            if (h.hours.isNotEmpty()) {
                Section("Time in each level today")
                ListPanel(
                    h.hours.map { x ->
                        val row: @Composable () -> Unit = { InfoRow(x.level, "${fmt(x.hours, 1)} h") }
                        row
                    },
                )
            }
            Section("Dangerous heat days")
            if (h.dangerDays.isEmpty()) {
                EmptyCard("None recorded yet.")
            } else {
                ListPanel(
                    h.dangerDays.map { d ->
                        val row: @Composable () -> Unit = {
                            InfoRow(dayName(d.date), withUnit(d.value, h.unit, 0), sub = d.level)
                        }
                        row
                    },
                )
                Text(
                    "Days with a heat index of ${withUnit(h.dangerLimit, h.unit, 0)} or more.",
                    style = T.label, color = C.muted, modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text("General guidance, not medical advice.", style = T.label, color = C.muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Station

@Composable
fun StationPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "station", { it.station() }) { s ->
            val color = when (s.status) {
                "ok" -> C.good
                "slow" -> C.warn
                else -> C.bad
            }
            Section("Station")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    when (s.status) { "ok" -> "Reporting"; "slow" -> "Slow"; "silent" -> "Silent"; else -> "No readings" },
                    color,
                )
                Spacer(Modifier.width(10.dp))
                Text("Last reading ${s.statusText}", style = T.body, color = C.muted)
            }
            Spacer(Modifier.height(10.dp))
            s.warnings.forEach { Note(it, C.warn) }
            TileGrid(
                tiles(
                    Triple("Data, last 24 hours", "${one(s.completeness)}%", "of expected readings"),
                    Triple("Gaps over 5 min", s.gapCount.toString(), if (s.gapCount > 0) "longest ${s.longestGap} min" else null),
                ),
            )
            if (s.gaps.isNotEmpty()) {
                Section("Latest gaps")
                ListPanel(
                    s.gaps.map { g ->
                        val row: @Composable () -> Unit = { InfoRow(pointTime(g.start), "${g.minutes} min") }
                        row
                    },
                )
            }
            Section("Sensors")
            ListPanel(
                s.sensors.map { x ->
                    val row: @Composable () -> Unit = {
                        InfoRow(
                            x.label, "${Math.round(x.presentPercent)}%",
                            sub = x.ageSeconds?.let { "Last seen ${ago(it)}" } ?: "Not seen",
                        )
                    }
                    row
                },
            )
            Section("Batteries and signal")
            val radios = s.radios
            if (radios == null) {
                Text("Needs the station's IP address in Weather Lab's Setup, and the PC on the same network as the station.", style = T.label, color = C.muted)
            } else if (radios.isEmpty()) {
                EmptyCard("No wireless sensors reported.")
            } else {
                ListPanel(
                    radios.map { r ->
                        val bad = r.batteryState in listOf("low", "watch") || r.signalState in listOf("weak", "none")
                        val row: @Composable () -> Unit = {
                            InfoRow(r.name, r.battery, sub = "Signal ${r.signal}", rightColor = if (bad) C.warn else null)
                        }
                        row
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// What's New

private val APP_NEWS = listOf(
    NewsEntry(
        "0.7.22", "October 30, 2026",
        listOf(
            "The More menu has five groups, the same as the Weather Lab sidebar on your PC: Now, History, Weather, Storms and Station. Every page is still there.",
        ),
    ),
    NewsEntry(
        "0.7.21", "October 29, 2026",
        listOf(
            "A new About page under More, in the Settings group: how the app keeps your data private, its version (and the Weather Lab version on your PC), a link to this list of changes, the source code on GitHub and the licence.",
        ),
    ),
    NewsEntry(
        "0.7.20", "October 29, 2026",
        listOf(
            "The words for dew point (dry, muggy...) and wind direction now come from Weather Lab, so the app and the PC always say the same thing. This needs Weather Lab 0.37.36 or newer on your PC; with an older version the dew point line shows no word.",
        ),
    ),
    NewsEntry(
        "0.7.19", "October 29, 2026",
        listOf(
            "New Wear OS watch app, installed separately from the phone app: the temperature, today's high and low, rain chance and the morning briefing on your wrist.",
            "A tile you can swipe to, and a watch-face complication that shows the temperature with today's high.",
            "The watch gets your address and token from this app by itself, so there is nothing to type on the watch. It needs Wi-Fi that can reach your PC.",
        ),
    ),
    NewsEntry(
        "0.7.18", "October 29, 2026",
        listOf(
            "A more modern look: cards have softer, rounder corners, a light shadow and a faint edge, and the figures on tiles are larger.",
            "Live is now Home, to match Weather Lab on your PC. The card at the top takes a soft tint from the weather: warm by day, deep blue at night, blue when it rains. Home's tab shows the Alert badge when an alert came in during the last 24 hours, and Home lists them under Recent alerts.",
            "New History tab in place of Alerts: Daily, Records, Rankings, Compare, Month, Year and On this phone, picked from the chips at the top. Older alerts are under More, Alert history.",
            "Pages fade gently when you switch tabs.",
        ),
    ),
    NewsEntry(
        "0.7.17", "October 29, 2026",
        listOf(
            "Keep history on this phone (Settings, History on this phone). Off by default. When on, the phone saves the readings it receives, one every 5 minutes, for 30 days, 90 days or a year.",
            "More, Saved on this phone shows the days the phone kept (high, low, rain, gust) and a 24-hour temperature line, and works when your PC can't be reached. Clear saved history empties it.",
        ),
    ),
    NewsEntry(
        "0.7.16", "October 26, 2026",
        listOf(
            "Fixed the Live page: the current-conditions card (temperature, sky, feels like, today's high and low) is back at the top under the briefing. It had been drawn underneath the tiles since 0.7.14.",
        ),
    ),
    NewsEntry(
        "0.7.15", "October 26, 2026",
        listOf(
            "Live keeps the last reading when your PC can't be reached. A yellow banner says it is offline and how old the reading is, with a Try again button, and the figures stay on screen instead of an error. The phone remembers the last reading even after you close the app.",
            "Share a day or a month as a picture. The Daily page has Share this day (or Share today so far), and the Monthly card has Share this month. Each makes a picture like the year card and opens the share sheet.",
            "The morning briefing notification is on the PC side: turn it on in Weather Lab under Settings, Notifications (Weather Lab 0.37.28). It arrives on this phone through your phone alerts.",
        ),
    ),
    NewsEntry(
        "0.7.14", "October 26, 2026",
        listOf(
            "A briefing card at the top of Live: one sentence about today. Tap it for the full Briefing page with the expected high against yesterday and the same day last year, last night's low, heat index peak, UV, rain chance, wind and a short list of things worth knowing. It also sits first in the More menu. Needs Weather Lab 0.37.26 or newer on your PC.",
            "My year has a Share this year button. It makes a picture of the year's best numbers (hottest day, days at 100°F or more, longest hot streak, wettest day, rain and highest gust) and opens the share sheet. The streak and wettest day need Weather Lab 0.37.27 or newer.",
        ),
    ),
    NewsEntry(
        "0.7.13", "October 25, 2026",
        listOf(
            "The More menu has the same sections as the PC: Now, History, Air, sun and heat, Forecast and storms, Season, Tools, Health and Settings. Some pages are renamed to match: Unusual days, Replay, Export, Air, Sun, and Station is now Sensors and Health.",
        ),
    ),
    NewsEntry(
        "0.7.12", "October 25, 2026",
        listOf(
            "Wind Rose is now Wind. Pick 24 hours, 7 days, 30 days or All. The rose is drawn in four bands (1, 2, 3-4 and 5+ mph) with a legend, and a Calm tile shows how often the wind was under 1 mph, which is counted but not drawn.",
            "Needs Weather Lab 0.37.24 or newer on the PC.",
        ),
    ),
    NewsEntry(
        "0.7.11", "October 25, 2026",
        listOf(
            "Dew point moved from the top of the Live page to a card at the bottom. It draws the last 24 hours with the low and high, says in words how the air feels, and opens its history when tapped.",
            "Needs Weather Lab 0.37.23 or newer on the PC.",
        ),
    ),
    NewsEntry(
        "0.7.10", "October 25, 2026",
        listOf(
            "Every reading card on the Live page with history now draws its last 24 hours as a taller line with the low and high underneath. UV, Rain rate and Rain today have charts, and Indoor temperature and Indoor humidity are new cards.",
            "Needs Weather Lab 0.37.23 or newer on the PC.",
        ),
    ),
    NewsEntry(
        "0.7.9", "October 25, 2026",
        listOf(
            "Daily History is now Daily. Pick 1 day, 7 days, 30 days or All. Ranges show summary tiles (hottest day, coolest night, windiest gust, rain), charts, the wind rose for the period, rain totals for today, 7 days, this month and this year, and a list of days. 1 day has Previous and Next.",
            "Needs Weather Lab 0.37.22 or newer on the PC.",
        ),
    ),
    NewsEntry(
        "0.7.8", "October 25, 2026",
        listOf(
            "Heat Safety is now Heat safety. It shows hours at Danger or worse, a chart of today's air temperature and heat index with the level lines, the levels with \"you are here\", hours spent in each level today, and a list of dangerous heat days.",
            "Needs Weather Lab 0.37.21 or newer on the PC.",
        ),
    ),
    NewsEntry(
        "0.7.7", "October 25, 2026",
        listOf(
            "Records is now Records and analysis. It has summary tiles, chips for Records, Heat, Rain and storms and Climate, lists of heat, cold, wind and rain events, the records board grouped as on the PC, and a chart of how many days fell in each daily-high range.",
            "Needs Weather Lab 0.37.20 or newer on the PC.",
        ),
    ),
    NewsEntry(
        "0.7.6", "October 25, 2026",
        listOf(
            "Tap the big temperature at the top of Live to see its history, the same as the tiles.",
        ),
    ),
    NewsEntry(
        "0.7.5", "October 25, 2026",
        listOf(
            "Tap a Live tile to see its history: 24 hours, 7 days or 30 days, with the high, low and average.",
            "Needs Weather Lab 0.37.9 or newer on the PC.",
        ),
    ),
    NewsEntry(
        "0.7.4", "October 24, 2026",
        listOf(
            "Live tiles now look like the phone app: a coloured edge, a 24-hour line, and a small line under each figure (indoor humidity, wind direction, peak today, pressure trend).",
            "New Visibility tile from the nearest airport, and a Solar tile. The ? ring now shows only on terms that need it (Gust, Pressure).",
            "Needs Weather Lab 0.37.8 or newer on the PC for the trend lines, peaks and visibility.",
        ),
    ),
    NewsEntry(
        "0.7.3", "October 24, 2026",
        listOf(
            "A blue ? ring now sits beside weather terms like Humidity, Gust, Pressure and UV index. Tap it to read what the term means.",
        ),
    ),
    NewsEntry(
        "0.7.2", "October 24, 2026",
        listOf(
            "The Weather Lab logo shows in the top bar on every screen.",
        ),
    ),
    NewsEntry(
        "0.7.1", "October 24, 2026",
        listOf(
            "New app icon: the Weather Lab badge logo.",
        ),
    ),
    NewsEntry(
        "0.7.0", "October 23, 2026",
        listOf(
            "Tablet layout: on a wide screen the tabs move to a side rail and Live, Forecast and Storm show two panes (Live next to the charts, Forecast next to Sun and UV, Storm next to Lightning).",
        ),
    ),
    NewsEntry(
        "0.6.0", "October 23, 2026",
        listOf(
            "History pages from your own station: On This Day, Compare, Monthly Card, My Year, Season Milestones, Anomalies, Alert History, Forecast Accuracy, Export Data (CSV) and Season Rewind. They need Weather Lab 0.37 or newer.",
            "Public-source pages: Radar, Airport comparison, Tropics and Air and Smoke (with an AirNow key).",
            "Phone Alerts: shows the ntfy server and topic so this phone gets the same alerts as your PC.",
            "App lock with your phone's screen lock, and a choice of how soon it locks again.",
            "Pause refreshing while the app is in the background, and a setup code to move your connection to a new phone.",
        ),
    ),
    NewsEntry(
        "0.5.0", "October 23, 2026",
        listOf(
            "A Forecast tab with the National Weather Service forecast: the next 24 hours, then day and night periods.",
            "Touch charts: touch or drag a chart to read the value.",
            "New pages under More: Wind Rose, Sun and UV, Heat Safety, Rain Calendar, Rain Events, Lightning, Rankings, Station and What's New.",
        ),
    ),
    NewsEntry(
        "0.4.0", "October 23, 2026",
        listOf(
            "A Storm tab, a More menu, Charts for 7 and 30 days, Daily History with Yesterday, and Records.",
            "Text size (Normal, Large, Largest) in Settings.",
        ),
    ),
    NewsEntry("0.3.1", "October 23, 2026", listOf("Dark and Light are the two color schemes.")),
)

@Composable
fun WhatsNewPage(address: String, token: String) {
    PageColumn {
        Section("This app")
        APP_NEWS.forEach { Panel { NewsBlock(it) }; Spacer(Modifier.height(10.dp)) }
        Section("Weather Lab on your PC")
        Fetch(address, token, "whatsnew", { it.whatsNew() }) { list ->
            list.take(12).forEach { Panel { NewsBlock(it) }; Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun NewsBlock(e: NewsEntry) {
    Column {
        Text("${e.version}  ·  ${e.date}", style = T.heading, color = C.text)
        e.items.forEach {
            Spacer(Modifier.height(6.dp))
            Text("•  $it", style = T.body, color = C.muted)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Forecast tab

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h a", Locale.US)

private fun hourLabel(iso: String): String = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(HOUR_FORMAT)
} catch (_: Exception) {
    iso.takeLast(8)
}

@Composable
fun ForecastPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "forecast", { it.forecast() }) { f ->
            if (!f.available) {
                Spacer(Modifier.height(8.dp))
                EmptyCard(f.reason.ifBlank { "The forecast isn't available right now." })
                return@Fetch
            }
            Spacer(Modifier.height(8.dp))
            f.alerts.forEach {
                val c = when (it.severity) { "Extreme", "Severe" -> C.bad; "Moderate" -> C.warn; else -> C.muted }
                Note("${it.event}. ${it.headline}".trim(), c)
            }
            if (f.hours.isNotEmpty()) {
                Section("Next 24 hours")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    f.hours.forEach { h ->
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(C.card).padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(hourLabel(h.start), style = T.label, color = C.muted)
                            Text(if (h.temp == null) "--" else "${h.temp}°", style = T.heading, color = C.text)
                            Text(if ((h.rainChance ?: 0) > 0) "${h.rainChance}%" else " ", style = T.label, color = C.cyan)
                        }
                    }
                }
            }
            Section("Day by day")
            f.periods.forEach { p ->
                Panel {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = T.heading, color = C.text)
                                Text(p.short, style = T.body, color = C.muted)
                            }
                            if ((p.rainChance ?: 0) > 0) {
                                Pill("${p.rainChance}% rain", C.cyan)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(if (p.temp == null) "--°" else "${p.temp}°", fontSize = 26.sp, fontWeight = FontWeight.W800, color = C.text)
                        }
                        if (p.detail.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(p.detail, style = T.label, color = C.muted)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Text("From the National Weather Service for your station's location.", style = T.label, color = C.muted)
        }
    }
}
