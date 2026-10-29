package com.weatherlab.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// The pages behind the Storm tab and the More menu: Charts, Daily (with Yesterday) and Records.
// Charts, Daily and Records need Weather Lab 0.35 or newer.

internal val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

internal fun dayName(date: String): String = try {
    LocalDate.parse(date).format(DAY_FORMAT)
} catch (_: Exception) {
    date
}

internal fun fmt(v: Double?, decimals: Int = 0): String =
    if (v == null) "--" else if (decimals == 0) Math.round(v).toString() else "%.${decimals}f".format(Locale.US, v)

internal fun withUnit(v: Double?, unit: String, decimals: Int = 0): String {
    if (v == null) return "--"
    val sep = if (unit.isEmpty() || unit == "%" || unit.startsWith("°")) "" else " "
    return "${fmt(v, decimals)}$sep$unit"
}

internal fun decimalsFor(unit: String): Int = when {
    unit == "days" -> 0
    unit == "in" || unit == "inHg" || unit == "in/hr" -> 2
    unit == "mm" || unit == "mm/h" || unit == "hPa" || unit == "mph" || unit == "km/h" || unit == "mi" || unit == "km" -> 1
    unit == "°F" || unit == "°C" || unit == "h" || unit == "%" -> 1
    else -> 1
}

internal fun ago(seconds: Long): String = when {
    seconds < 90 -> "just now"
    seconds < 3600 -> "${seconds / 60} min ago"
    seconds < 86400 -> "${seconds / 3600} h ago"
    else -> "${seconds / 86400} d ago"
}

@Composable
internal fun PageColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) { content() }
}

/** Loads one thing from Weather Lab on a worker thread, with loading and error cards. */
@Composable
internal fun <T> Fetch(address: String, token: String, key: Any, load: (WeatherLabApi) -> T, content: @Composable (T) -> Unit) {
    var state by remember(key, address, token) { mutableStateOf<Result<T>?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(key, address, token, retry) {
        state = null
        state = withContext(Dispatchers.IO) { runCatching { load(WeatherLabApi(address, token)) } }
    }
    val result = state
    when {
        result == null -> LoadingCard()
        result.isFailure -> {
            val e = result.exceptionOrNull()
            val message = if (e is ApiException && e.code == 404) "This page needs a newer Weather Lab. Update Weather Lab on your PC."
            else e?.message ?: "Couldn't reach Weather Lab."
            ErrorBox(message, onRetry = { retry++ }, title = "Couldn't load this page")
        }
        else -> content(result.getOrThrow())
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Storm tab

@Composable
fun StormPage(snapshot: Snapshot?, stormNotify: Boolean) {
    PageColumn {
        val st = snapshot?.storm
        if (st == null) {
            Section("Lightning")
            EmptyCard("Lightning needs Weather Lab 0.32 or newer, and the connection set up in More > Settings.")
            return@PageColumn
        }
        val (headline, color) = when (st.trend) {
            "overhead" -> "Lightning overhead" to C.bad
            "approaching" -> "Storm approaching" to C.bad
            "receding" -> "Storm moving away" to C.warn
            "steady" -> "Lightning nearby, holding steady" to C.warn
            else -> "No lightning nearby" to C.good
        }
        Section("Right now")
        Panel(border = if (st.trend == "none") null else color) {
            Column {
                Text(headline, fontSize = 20.sp, fontWeight = FontWeight.W800, color = color)
                Spacer(Modifier.height(4.dp))
                val last = st.last
                Text(
                    if (last == null) "No strikes recorded yet." else
                        "Last strike ${fmt(last.distance.value, 1)} ${last.distance.unit} away, ${ago(last.ageSeconds)}.",
                    style = T.body, color = C.muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        TileGrid(
            listOf<@Composable (Modifier) -> Unit>(
                { m -> Tile("Last 10 minutes", st.strikes10m.toString(), m) },
                { m -> Tile("Last 30 minutes", st.strikes30m.toString(), m) },
                { m -> Tile("Today", st.strikesToday.toString(), m) },
                { m ->
                    Tile(
                        "Last strike",
                        st.last?.let { withUnit(it.distance.value, it.distance.unit, 1) } ?: "--",
                        m, sub = st.last?.let { ago(it.ageSeconds) },
                    )
                },
            ),
        )
        Section("Countdown notification")
        Note(
            if (stormNotify) "On. A notification shows the arrival estimate while lightning is near. Checked about every 15 minutes."
            else "Off. Turn it on in More > Settings.",
            if (stormNotify) C.good else C.muted,
        )
        Text(
            "Approaching and moving away compare the average distance of strikes in the last 10 minutes with the 10 minutes before.",
            style = T.label, color = C.muted,
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// More menu

enum class MoreSub(val label: String) {
    MENU("More"), BRIEFING("Briefing"), CHARTS("Charts"), WIND("Wind"), SUN("Sun"), HEAT("Heat safety"),
    RAIN_CAL("Rain calendar"), RAIN_EVENTS("Rain events"), LIGHTNING("Lightning"),
    DAYS("Daily"), RECORDS("Records and analysis"), RANKINGS("Rankings"),
    STATION("Sensors and Health"), WHATS_NEW("What's New"), SETTINGS("Settings"),
    ON_THIS_DAY("On this day"), COMPARE("Compare"), MONTH("Monthly card"), YEAR("My year"),
    MILESTONES("Season milestones"), ANOMALIES("Unusual days"), ALERT_HISTORY("Alert history"),
    FC_ACCURACY("Forecast accuracy"), EXPORT("Export"), REWIND("Replay"),
    AIRPORT("Airport"), TROPICS("Tropics"), RADAR("Radar"), AIR("Air"), PUSH("Phone alerts"), PHONE_HISTORY("Saved on this phone"),
}

private data class MoreItem(val sub: MoreSub, val about: String)

@Composable
fun MorePage(onOpen: (MoreSub) -> Unit) {
    val groups = listOf(
        "Now" to listOf(
            MoreItem(MoreSub.BRIEFING, "Today at a glance, against yesterday and last year"),
            MoreItem(MoreSub.ALERT_HISTORY, "Alerts from the last 14 days"),
        ),
        "History" to listOf(
            MoreItem(MoreSub.CHARTS, "Touch charts for 7 and 30 days"),
        ),
        "Air, sun and heat" to listOf(
            MoreItem(MoreSub.AIR, "Air quality and smoke (needs an AirNow key)"),
            MoreItem(MoreSub.WIND, "Where the wind comes from"),
            MoreItem(MoreSub.SUN, "UV, sunshine and sunburn times"),
            MoreItem(MoreSub.HEAT, "Heat index, time in each level and dangerous days"),
            MoreItem(MoreSub.AIRPORT, "Your station next to the nearest airport"),
        ),
        "Forecast and storms" to listOf(
            MoreItem(MoreSub.FC_ACCURACY, "How close the forecast highs were"),
            MoreItem(MoreSub.LIGHTNING, "Strikes by day and the closest one"),
            MoreItem(MoreSub.TROPICS, "Tropical storms and how far away they are"),
            MoreItem(MoreSub.RADAR, "National Weather Service radar for your area"),
        ),
        "Season" to listOf(
            MoreItem(MoreSub.RAIN_CAL, "Every day of rain, month by month"),
            MoreItem(MoreSub.RAIN_EVENTS, "Each wet spell"),
            MoreItem(MoreSub.ON_THIS_DAY, "Today's date in every past year"),
            MoreItem(MoreSub.MILESTONES, "First and last freeze, 90 and 100 degree days"),
        ),
        "Tools" to listOf(
            MoreItem(MoreSub.ANOMALIES, "Unusual readings in the last 24 hours"),
            MoreItem(MoreSub.REWIND, "Replay any past day"),
            MoreItem(MoreSub.EXPORT, "Send your daily summaries as a CSV"),
        ),
        "Health" to listOf(
            MoreItem(MoreSub.STATION, "Data health, sensors, batteries"),
        ),
        "Settings" to listOf(
            MoreItem(MoreSub.PUSH, "Get PC alerts on this phone"),
            MoreItem(MoreSub.WHATS_NEW, "Everything added"),
            MoreItem(MoreSub.SETTINGS, "Connection, notifications, look"),
        ),
    )
    PageColumn {
        groups.forEach { (title, items) ->
            Section(title)
            ListPanel(
                items.map { item ->
                    val row: @Composable () -> Unit = {
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(item.sub) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) { InfoRow(item.sub.label, "", sub = item.about) }
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = C.muted)
                        }
                    }
                    row
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Charts

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartsPage(address: String, token: String) {
    var days by remember { mutableIntStateOf(7) }
    PageColumn {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf(7 to "7 days", 30 to "30 days").forEach { (n, label) ->
                FilterChip(
                    selected = days == n,
                    onClick = { days = n },
                    label = { Text(label, color = C.text) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = C.card,
                        selectedContainerColor = C.cyan.copy(alpha = 50 / 255f),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = days == n,
                        borderColor = C.line, selectedBorderColor = C.cyan,
                    ),
                )
            }
        }
        Fetch(address, token, days, { it.charts(days) }) { c ->
            val range = if (c.days.isEmpty()) "" else "${dayName(c.days.first().date)} to ${dayName(c.days.last().date)}"
            fun series(pick: (ChartDay) -> Double?): Pair<List<Double>, List<String>> {
                val kept = c.days.filter { pick(it) != null }
                return kept.map { pick(it)!! } to kept.map { dayName(it.date) }
            }
            if (c.tempPoints.size > 1) {
                Section("Temperature")
                Panel {
                    TouchLine(c.tempPoints.map { it.second }, c.tempPoints.map { pointTime(it.first) }, c.tempUnit)
                }
            }
            val high = series { it.high }
            Section("High each day")
            Panel { TouchLine(high.first, high.second, c.tempUnit) }
            val low = series { it.low }
            Section("Low each day")
            Panel { TouchLine(low.first, low.second, c.tempUnit) }
            Section("Rain")
            Panel {
                val rain = c.days.map { it.rain }
                if (rain.all { it == null || it <= 0.0 }) Text("No rain in this period.", style = T.body, color = C.muted)
                else TouchBars(rain, c.days.map { dayName(it.date) }, c.rainUnit)
            }
            val gust = series { it.gust }
            Section("Highest gust each day")
            Panel { TouchLine(gust.first, gust.second, " ${c.windUnit}") }
            Text(range, style = T.label, color = C.muted)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Daily

private val DAILY_RANGES = listOf("1 day", "7 days", "30 days", "All")

/** Daily: one day with previous and next, or 7 days, 30 days or all days together. All needs Weather Lab 0.37.22 or newer. */
@Composable
fun DailyPage(address: String, token: String, onOpen: (String) -> Unit) {
    var range by remember { mutableIntStateOf(1) }
    PageColumn {
        Spacer(Modifier.height(8.dp))
        Chips(DAILY_RANGES, range) { range = it }
        if (range == 0) OneDay(address, token) else DaySpan(address, token, listOf(0, 7, 30, null)[range], onOpen)
    }
}

/** One day at a time, newest first from the list of days kept, with Previous and Next. */
@Composable
private fun OneDay(address: String, token: String) {
    var index by remember { mutableIntStateOf(0) }
    Fetch(address, token, "days", { it.days() }) { list ->
        if (list.days.isEmpty()) {
            EmptyCard("No daily summaries yet. They appear after the first full day of readings.")
        } else {
            val at = index.coerceIn(0, list.days.size - 1)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { index = at + 1 }, enabled = at < list.days.size - 1,
                    colors = ButtonDefaults.buttonColors(containerColor = C.card, contentColor = C.text),
                ) { Text("Previous") }
                Text(dayName(list.days[at].date), style = T.body, color = C.text, modifier = Modifier.weight(1f))
                Button(
                    onClick = { index = at - 1 }, enabled = at > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = C.card, contentColor = C.text),
                ) { Text("Next") }
            }
            DayDetailBody(address, token, list.days[at].date, showTitle = false)
        }
    }
}

/** Summary tiles, charts, the wind rose, rain totals and the list of days for the last 7 or 30 days, or all (days = null). */
@Composable
private fun DaySpan(address: String, token: String, days: Int?, onOpen: (String) -> Unit) {
    Fetch(address, token, "span$days", { it.charts(days) }) { c ->
        val held = c.days.filter { it.high != null || it.low != null || it.rain != null || it.gust != null }
        if (held.isEmpty()) {
            EmptyCard("No daily summaries yet. They appear after the first full day of readings.")
            return@Fetch
        }
        Text("${held.size} days: ${dayName(held.first().date)} to ${dayName(held.last().date)}", style = T.label, color = C.muted)
        val hottest = held.filter { it.high != null }.maxByOrNull { it.high!! }
        val coolest = held.filter { it.low != null }.minByOrNull { it.low!! }
        val windiest = held.filter { it.gust != null }.maxByOrNull { it.gust!! }
        val rainDays = held.filter { (it.rain ?: 0.0) >= 0.01 }
        TileGrid(
            tiles(
                Triple("Hottest day", withUnit(hottest?.high, c.tempUnit, 0), hottest?.let { dayName(it.date) }),
                Triple("Coolest night", withUnit(coolest?.low, c.tempUnit, 0), coolest?.let { dayName(it.date) }),
                Triple("Windiest gust", withUnit(windiest?.gust, c.windUnit, 0), windiest?.let { dayName(it.date) }),
                Triple("Rain", withUnit(held.sumOf { it.rain ?: 0.0 }, c.rainUnit, decimalsFor(c.rainUnit)), "${rainDays.size} wet days"),
            ),
        )
        fun series(pick: (ChartDay) -> Double?): Pair<List<Double>, List<String>> {
            val kept = held.filter { pick(it) != null }
            return kept.map { pick(it)!! } to kept.map { dayName(it.date) }
        }
        if (c.tempPoints.size > 1) {
            Section("Temperature")
            Panel { TouchLine(c.tempPoints.map { it.second }, c.tempPoints.map { pointTime(it.first) }, c.tempUnit) }
        }
        val high = series { it.high }
        Section("High each day")
        Panel { TouchLine(high.first, high.second, c.tempUnit) }
        val avg = series { it.avg }
        if (avg.first.size > 1) {
            Section("Average each day")
            Panel { TouchLine(avg.first, avg.second, c.tempUnit) }
        }
        val low = series { it.low }
        Section("Low each day")
        Panel { TouchLine(low.first, low.second, c.tempUnit) }
        Section("Rain")
        Panel {
            if (rainDays.isEmpty()) Text("No rain in this period.", style = T.body, color = C.muted)
            else TouchBars(held.map { it.rain }, held.map { dayName(it.date) }, c.rainUnit)
        }
        val gust = series { it.gust }
        if (gust.first.size > 1) {
            Section("Highest gust each day")
            Panel { TouchLine(gust.first, gust.second, " ${c.windUnit}") }
        }
        Fetch(address, token, "spanwind$days", { it.windRose(days) }) { w ->
            if (w.total > 0) {
                Section("Wind rose for this period")
                Panel { WindRoseChart(w) }
                Text(
                    "Most often from the ${w.dominant ?: "--"}. Average wind ${withUnit(w.average, w.unit, 1)}, strongest gust ${withUnit(w.strongestGust, w.unit, 0)}.",
                    style = T.label, color = C.muted,
                )
            }
        }
        if (c.rainTotals.isNotEmpty()) {
            Section("Rain totals")
            TileGrid(
                tiles(
                    Triple("Rain today", withUnit(c.rainTotals["today"], c.rainUnit, decimalsFor(c.rainUnit)), null),
                    Triple("Last 7 days", withUnit(c.rainTotals["last_7_days"], c.rainUnit, decimalsFor(c.rainUnit)), null),
                    Triple("This month", withUnit(c.rainTotals["this_month"], c.rainUnit, decimalsFor(c.rainUnit)), null),
                    Triple("This year", withUnit(c.rainTotals["this_year"], c.rainUnit, decimalsFor(c.rainUnit)), null),
                ),
            )
        }
        Section("Days in this range")
        ListPanel(
            held.reversed().take(60).map { d ->
                val row: @Composable () -> Unit = {
                    Row(Modifier.fillMaxWidth().clickable { onOpen(d.date) }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            InfoRow(
                                left = dayName(d.date),
                                right = "${fmt(d.high)}° / ${fmt(d.low)}°",
                                sub = d.rain?.takeIf { it >= 0.01 }?.let { "Rain ${withUnit(it, c.rainUnit, decimalsFor(c.rainUnit))}" },
                            )
                        }
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = C.muted)
                    }
                }
                row
            },
        )
        Text("Tap a day to see every figure for it.", style = T.label, color = C.muted)
    }
}

@Composable
fun DayDetailPage(address: String, token: String, which: String) {
    PageColumn { DayDetailBody(address, token, which, showTitle = true) }
}

/** Every figure kept for one day: "yesterday" or a date such as 2026-10-20. */
@Composable
private fun DayDetailBody(address: String, token: String, which: String, showTitle: Boolean) {
    Fetch(address, token, which, { it.day(which) }) { d ->
        if (showTitle) Section(if (which == "yesterday") "Yesterday, ${dayName(d.date)}" else dayName(d.date))
        if (!d.found) {
            EmptyCard("Weather Lab has no summary for this day.")
        } else {
            if (d.partial) Note("Today so far. Only the high, low and rain are live until the day ends.", C.muted)
            if (d.estimated) Note("Imported from readings more than 5 minutes apart, so a high or a low can be a few degrees off.", C.warn)
            ListPanel(
                d.values.map { v ->
                    val row: @Composable () -> Unit = { InfoRow(v.label, withUnit(v.value, v.unit, decimalsFor(v.unit))) }
                    row
                },
            )
            ShareButton(if (d.partial) "Share today so far" else "Share this day") { shareDayCard(it, d) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Records and analysis

private val RECORD_GROUPS = listOf("Records", "Heat", "Rain and storms", "Climate")
private val RECORD_VIEWS = listOf("Extreme events", "Records board", "Distribution")

/** Records and analysis: summary tiles, group and view chips, event lists, the records board and a distribution. */
@Composable
fun RecordsPage(address: String, token: String, onOpen: (MoreSub) -> Unit = {}) {
    var group by remember { mutableIntStateOf(0) }
    var view by remember { mutableIntStateOf(0) }
    PageColumn {
        Fetch(address, token, "records-analysis", { it.recordsAnalysis() }) { a ->
            val metric = a.tempUnit == "°C"
            val hot100 = if (metric) "38°C" else "100°F"
            val hot110 = if (metric) "43°C" else "110°F"
            Section("At a glance")
            TileGrid(
                tiles(
                    Triple("Days $hot100 or more", a.days100.toString(), "${a.days110} of them $hot110+"),
                    Triple("Total rain", withUnit(a.rainTotal, a.rainUnit, decimalsFor(a.rainUnit)), "${a.rainDays} rainy days"),
                    Triple("Highest gust", withUnit(a.gust?.value, a.gustUnit, 0), a.gust?.let { dayName(it.date) }),
                    Triple("Hottest day", withUnit(a.hottest?.value, a.tempUnit, 1), a.hottest?.let { dayName(it.date) }),
                ),
            )
            Section("Explore")
            Chips(RECORD_GROUPS, group) { group = it; view = 0 }
            if (group == 0) Chips(RECORD_VIEWS, view) { view = it }
            Spacer(Modifier.height(10.dp))
            when {
                group == 0 && view == 0 -> {
                    TileGrid(
                        tiles(
                            Triple("Heat events", a.heatCount.toString(), a.heat.firstOrNull()?.let { "hottest ${withUnit(it.value, a.tempUnit, 1)}" } ?: "none recorded"),
                            Triple("Cold events", a.coldCount.toString(), a.cold.firstOrNull()?.let { "lowest ${withUnit(it.value, a.tempUnit, 1)}" } ?: "none recorded"),
                            Triple("Wind events", a.windCount.toString(), a.wind.firstOrNull()?.let { "highest ${withUnit(it.value, a.gustUnit, 0)}" } ?: "none recorded"),
                            Triple("Rain events", a.rainCount.toString(), a.rain.firstOrNull()?.let { "wettest ${withUnit(it.value, a.rainUnit, 2)}" } ?: "none recorded"),
                        ),
                    )
                    EventList("Heat events", a.heat, a.tempUnit, 1)
                    EventList("Cold events", a.cold, a.tempUnit, 1)
                    EventList("Wind events", a.wind, a.gustUnit, 0)
                    EventList("Rain events", a.rain, a.rainUnit, 2)
                    Text(
                        "An event is a high of ${withUnit(a.heatLimit, a.tempUnit, 0)} or more, a low of ${withUnit(a.coldLimit, a.tempUnit, 0)} or less, " +
                            "a gust of ${withUnit(a.windLimit, a.gustUnit, 0)} or more, or ${withUnit(a.rainLimit, a.rainUnit, 2)} of rain or more.",
                        style = T.label, color = C.muted, modifier = Modifier.padding(top = 8.dp),
                    )
                }
                group == 0 && view == 1 -> {
                    if (a.board.isEmpty()) {
                        EmptyCard("No records yet. They build up from the daily summaries.")
                    } else {
                        a.board.forEach { g ->
                            Section(g.title)
                            ListPanel(
                                g.rows.map { r ->
                                    val row: @Composable () -> Unit = {
                                        InfoRow(
                                            left = if (r.isNew) "${r.label}  NEW" else r.label,
                                            right = withUnit(r.value, r.unit, decimalsFor(r.unit)),
                                            sub = r.whenText,
                                        )
                                    }
                                    row
                                },
                            )
                        }
                        Text("NEW means set in the last 30 days. Records cover only the time since Weather Lab started collecting.", style = T.label, color = C.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                group == 0 -> {
                    if (a.distribution.isEmpty()) {
                        EmptyCard("No daily highs yet.")
                    } else {
                        Section("Days by daily high")
                        TouchBars(
                            a.distribution.map { it.count.toDouble() },
                            a.distribution.map { b -> "${fmt(b.from, 0)}${a.tempUnit} and up" },
                            "days", topLabel = "Most days in a range",
                        )
                    }
                }
                group == 1 -> {
                    EventList("Heat events", a.heat, a.tempUnit, 1)
                    Text("Days at ${withUnit(a.heatLimit, a.tempUnit, 0)} or more. The Heat Safety page has the heat index.", style = T.label, color = C.muted, modifier = Modifier.padding(top = 8.dp))
                }
                group == 2 -> {
                    EventList("Rain events", a.rain, a.rainUnit, 2)
                    EventList("Wind events", a.wind, a.gustUnit, 0)
                }
                else -> {
                    ListPanel(
                        listOf(MoreSub.ON_THIS_DAY to "Today's date in every past year", MoreSub.COMPARE to "This year against last year").map { (sub, about) ->
                            val row: @Composable () -> Unit = {
                                Row(
                                    Modifier.fillMaxWidth().clickable { onOpen(sub) }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) { InfoRow(sub.label, "", sub = about) }
                                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = C.muted)
                                }
                            }
                            row
                        },
                    )
                }
            }
        }
    }
}

/** A titled list of days with one figure each, or a note when there are none. */
@Composable
private fun EventList(title: String, items: List<EventDay>, unit: String, decimals: Int) {
    Section(title)
    if (items.isEmpty()) {
        EmptyCard("None recorded.")
    } else {
        ListPanel(
            items.map { e ->
                val row: @Composable () -> Unit = { InfoRow(dayName(e.date), withUnit(e.value, unit, decimals)) }
                row
            },
        )
    }
}
