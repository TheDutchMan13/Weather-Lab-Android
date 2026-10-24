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
fun TouchBars(values: List<Double?>, labels: List<String>, unit: String, height: Dp = 110.dp) {
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
        else "Most in a day: ${withUnit(top, unit, decimals)}",
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
private fun Chips(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
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

private fun tiles(vararg items: Triple<String, String, String?>): List<@Composable (Modifier) -> Unit> =
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

private fun dayLong(date: String): String = try {
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
    var pick by remember { mutableIntStateOf(0) }
    val periods = listOf(1, 7, 30)
    PageColumn {
        Chips(listOf("24 hours", "7 days", "30 days"), pick) { pick = it }
        Fetch(address, token, pick, { it.windRose(periods[pick]) }) { w ->
            if (w.total == 0) {
                EmptyCard("No wind readings in this period yet.")
            } else {
                Spacer(Modifier.height(8.dp))
                TileGrid(
                    tiles(
                        Triple("Most common direction", w.dominant ?: "--", "wind comes from"),
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
private fun WindRoseChart(w: WindRose) {
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
        Text("Wedges point to where the wind comes from. Darker is stronger: ${w.bins.joinToString(", ")}.", style = T.label, color = C.muted)
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
                ),
            )
            Section("Hot days in ${h.year}")
            ListPanel(
                listOf(
                    { InfoRow("100 or hotter", "${h.days100}") },
                    { InfoRow("105 or hotter", "${h.days105}") },
                    { InfoRow("110 or hotter", "${h.days110}") },
                ),
            )
            Section("Heat index levels")
            ListPanel(
                h.levels.map { l ->
                    val row: @Composable () -> Unit = {
                        InfoRow(l.level, if (l.from == null) "" else "${l.from.roundToInt()}${h.unit} and up", sub = l.advice)
                    }
                    row
                },
            )
            Text("General guidance, not medical advice.", style = T.label, color = C.muted)
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
