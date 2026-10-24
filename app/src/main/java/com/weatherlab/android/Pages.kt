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

// The pages behind the Storm tab and the More menu: Charts, Daily History (with Yesterday) and Records.
// Charts, Daily History and Records need Weather Lab 0.35 or newer.

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
    MENU("More"), CHARTS("Charts"), WIND("Wind Rose"), SUN("Sun and UV"), HEAT("Heat Safety"),
    RAIN_CAL("Rain Calendar"), RAIN_EVENTS("Rain Events"), LIGHTNING("Lightning"),
    DAYS("Daily History"), RECORDS("Records"), RANKINGS("Rankings"),
    STATION("Station"), WHATS_NEW("What's New"), SETTINGS("Settings"),
    ON_THIS_DAY("On This Day"), COMPARE("Compare"), MONTH("Monthly Card"), YEAR("My Year"),
    MILESTONES("Season Milestones"), ANOMALIES("Anomalies"), ALERT_HISTORY("Alert History"),
    FC_ACCURACY("Forecast Accuracy"), EXPORT("Export Data"), REWIND("Season Rewind"),
    AIRPORT("Airport"), TROPICS("Tropics"), RADAR("Radar"), AIR("Air and Smoke"), PUSH("Phone Alerts"),
}

private data class MoreItem(val sub: MoreSub, val about: String)

@Composable
fun MorePage(onOpen: (MoreSub) -> Unit) {
    val groups = listOf(
        "Weather" to listOf(
            MoreItem(MoreSub.CHARTS, "Touch charts for 7 and 30 days"),
            MoreItem(MoreSub.WIND, "Where the wind comes from"),
            MoreItem(MoreSub.SUN, "UV, sunshine and sunburn times"),
            MoreItem(MoreSub.HEAT, "Heat index, advice and hot days"),
            MoreItem(MoreSub.RADAR, "National Weather Service radar for your area"),
            MoreItem(MoreSub.AIRPORT, "Your station next to the nearest airport"),
            MoreItem(MoreSub.AIR, "Air quality and smoke (needs an AirNow key)"),
        ),
        "Rain and storms" to listOf(
            MoreItem(MoreSub.RAIN_CAL, "Every day of rain, month by month"),
            MoreItem(MoreSub.RAIN_EVENTS, "Each wet spell"),
            MoreItem(MoreSub.LIGHTNING, "Strikes by day and the closest one"),
            MoreItem(MoreSub.TROPICS, "Tropical storms and how far away they are"),
        ),
        "History" to listOf(
            MoreItem(MoreSub.DAYS, "Yesterday, and any day of the last 60"),
            MoreItem(MoreSub.RECORDS, "All-time highs and lows"),
            MoreItem(MoreSub.RANKINGS, "Hottest, coolest, wettest and more"),
            MoreItem(MoreSub.ON_THIS_DAY, "Today's date in every past year"),
            MoreItem(MoreSub.COMPARE, "This year against last year"),
            MoreItem(MoreSub.MONTH, "A card for any month"),
            MoreItem(MoreSub.YEAR, "A year in review"),
            MoreItem(MoreSub.MILESTONES, "First and last freeze, 90 and 100 degree days"),
            MoreItem(MoreSub.ANOMALIES, "Unusual readings in the last 24 hours"),
            MoreItem(MoreSub.ALERT_HISTORY, "Alerts from the last 14 days"),
            MoreItem(MoreSub.FC_ACCURACY, "How close the forecast highs were"),
            MoreItem(MoreSub.REWIND, "Replay any past day"),
            MoreItem(MoreSub.EXPORT, "Send your daily summaries as a CSV"),
        ),
        "App" to listOf(
            MoreItem(MoreSub.STATION, "Data health, sensors, batteries"),
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
// Daily History

@Composable
fun DaysPage(address: String, token: String, onOpen: (String) -> Unit) {
    PageColumn {
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onOpen("yesterday") },
            colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
        ) { Text("Yesterday") }
        Fetch(address, token, "days", { it.days() }) { list ->
            Section("Last 60 days")
            if (list.days.isEmpty()) {
                EmptyCard("No daily summaries yet. They appear after the first full day of readings.")
            } else {
                ListPanel(
                    list.days.map { d ->
                        val row: @Composable () -> Unit = {
                            Row(Modifier.fillMaxWidth().clickable { onOpen(d.date) }, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    InfoRow(
                                        left = dayName(d.date),
                                        right = "${fmt(d.high)}° / ${fmt(d.low)}°",
                                        sub = d.rain?.takeIf { it >= 0.01 }?.let { "Rain ${withUnit(it, list.rainUnit, decimalsFor(list.rainUnit))}" },
                                    )
                                }
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

@Composable
fun DayDetailPage(address: String, token: String, which: String) {
    PageColumn {
        Fetch(address, token, which, { it.day(which) }) { d ->
            Section(if (which == "yesterday") "Yesterday, ${dayName(d.date)}" else dayName(d.date))
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
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Records

@Composable
fun RecordsPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "records", { it.records() }) { items ->
            Section("All-time records")
            if (items.isEmpty()) {
                EmptyCard("No records yet. They build up from the daily summaries.")
            } else {
                ListPanel(
                    items.map { r ->
                        val row: @Composable () -> Unit = {
                            InfoRow(
                                left = r.label,
                                right = withUnit(r.value, r.unit, decimalsFor(r.unit)),
                                sub = dayName(r.date) + (if (r.estimated) ", estimated" else ""),
                            )
                        }
                        row
                    },
                )
            }
        }
    }
}
