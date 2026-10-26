package com.weatherlab.android

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// History pages built from your own station's data: On This Day, Compare, Monthly Card, My Year, Season Milestones,
// Anomalies, Alert History, Forecast Accuracy, Export Data and Season Rewind. They need Weather Lab 0.37 or newer.

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val ALERT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

internal fun clockTime(seconds: Long): String = Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).format(CLOCK)

private fun shortDay(date: String): String = try {
    LocalDate.parse(date).format(SHORT_DAY)
} catch (_: Exception) {
    date
}

private fun temp(v: Double?, unit: String) = withUnit(v, unit, 0)

private fun amount(v: Double?, unit: String) = withUnit(v, unit, decimalsFor(unit))

/** A unit as it follows a chart value: degrees and percent hug the number, others get a space. */
internal fun spaced(unit: String): String = if (unit.startsWith("°") || unit == "%" || unit.isEmpty()) unit else " $unit"

private fun row(left: String, right: String, sub: String? = null): @Composable () -> Unit = { InfoRow(left, right, sub = sub) }

private fun extremeText(e: Extreme?, unit: String, decimals: Int = 0): Pair<String, String?> =
    if (e?.value == null) "--" to null else withUnit(e.value, unit, decimals) to shortDay(e.date)

// ---------------------------------------------------------------------------------------------------------------------
// On This Day

@Composable
fun OnThisDayPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "onthisday", { it.onThisDay("today") }) { d ->
            if (d.error != null) {
                EmptyCard(d.error)
            } else {
                val title = try {
                    LocalDate.of(2000, d.month, d.day).format(DateTimeFormatter.ofPattern("MMMM d", Locale.US))
                } catch (_: Exception) {
                    "Today"
                }
                Section("$title through the years")
                if (d.years.isEmpty()) {
                    EmptyCard("No earlier years have a daily summary for this date yet.")
                } else {
                    TileGrid(
                        tiles(
                            Triple("Hottest", temp(d.recordHigh, d.tempUnit), "highest high on this date"),
                            Triple("Coldest", temp(d.recordLow, d.tempUnit), "lowest low on this date"),
                        ),
                    )
                    Section("Year by year")
                    ListPanel(
                        d.years.map { y ->
                            row(
                                y.year.toString(), "${temp(y.high, d.tempUnit)} / ${temp(y.low, d.tempUnit)}",
                                y.rain?.takeIf { it >= 0.01 }?.let { "Rain ${amount(it, d.rainUnit)}" },
                            )
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Compare

@Composable
fun ComparePage(address: String, token: String) {
    var which by remember { mutableIntStateOf(0) }
    PageColumn {
        Chips(listOf("Temperature", "Rain"), which) { which = it }
        Fetch(address, token, "compare", { it.compare() }) { c ->
            if (c.months.all { it.values.isEmpty() }) {
                EmptyCard("Compare needs daily summaries from at least one month.")
            } else {
                Section(if (which == 0) "Average temperature by month" else "Rain by month")
                ListPanel(
                    c.months.filter { it.values.isNotEmpty() }.map { m ->
                        row(
                            m.label, "",
                            c.years.mapNotNull { y ->
                                val v = m.values[y.toString()] ?: return@mapNotNull null
                                val shown = if (which == 0) temp(v.first, c.tempUnit) else amount(v.second, c.rainUnit)
                                "$y  $shown"
                            }.joinToString("   ·   "),
                        )
                    },
                )
                Section("Rain so far this year")
                ListPanel(c.rainToDate.map { (y, r) -> row(y.toString(), amount(r, c.rainUnit)) })
                Text("Totals for the same dates in each year.", style = T.label, color = C.muted)
                if (c.heat.isNotEmpty()) {
                    Section("Hot days by year")
                    ListPanel(c.heat.map { h -> row(h.year.toString(), "${h.days100} at 100°F+", "${h.days110} at 110°F+") })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Monthly Card

@Composable
fun MonthCardPage(address: String, token: String) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    PageColumn {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Earlier month", tint = C.text)
            }
            Text(
                month.format(MONTH_TITLE), style = T.heading, color = C.text, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                Icon(
                    Icons.Filled.KeyboardArrowRight, contentDescription = "Later month",
                    tint = if (month < YearMonth.now()) C.text else C.quiet,
                )
            }
        }
        Fetch(address, token, month.toString(), { it.monthCard(month.toString()) }) { c ->
            if (c.error != null || !c.found) {
                EmptyCard(c.error ?: "No daily summaries for this month.")
            } else {
                val hot = extremeText(c.hottest, c.tempUnit)
                val cold = extremeText(c.coldest, c.tempUnit)
                val wet = extremeText(c.wettest, c.rainUnit, 2)
                val wind = extremeText(c.windiest, c.windUnit)
                TileGrid(
                    tiles(
                        Triple("Average high", temp(c.averageHigh, c.tempUnit), null),
                        Triple("Average low", temp(c.averageLow, c.tempUnit), null),
                        Triple("Rain", amount(c.rainTotal, c.rainUnit), "${c.rainyDays} rainy days"),
                        Triple("Days with data", c.daysWithData.toString(), null),
                        Triple("Hottest", hot.first, hot.second),
                        Triple("Coldest", cold.first, cold.second),
                        Triple("Wettest day", wet.first, wet.second),
                        Triple("Strongest gust", wind.first, wind.second),
                    ),
                )
                val highs = c.daily.filter { it.high != null }
                if (highs.size > 1) {
                    Section("High each day")
                    Panel { TouchLine(highs.map { it.high!! }, highs.map { dayName(it.date) }, c.tempUnit) }
                }
                val lows = c.daily.filter { it.low != null }
                if (lows.size > 1) {
                    Section("Low each day")
                    Panel { TouchLine(lows.map { it.low!! }, lows.map { dayName(it.date) }, c.tempUnit) }
                }
                Section("Rain")
                Panel {
                    if (c.daily.all { (it.rain ?: 0.0) <= 0.0 }) Text("No rain this month.", style = T.body, color = C.muted)
                    else TouchBars(c.daily.map { it.rain }, c.daily.map { dayName(it.date) }, c.rainUnit)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// My Year

@Composable
fun YearPage(address: String, token: String) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    val context = LocalContext.current
    var shareError by remember { mutableStateOf<String?>(null) }
    PageColumn {
        Fetch(address, token, year, { it.yearCard(year) }) { c ->
            if (c.years.isNotEmpty()) Chips(c.years.map { it.toString() }, c.years.indexOf(year)) { year = c.years[it] }
            if (!c.found) {
                EmptyCard("No daily summaries for $year.")
            } else {
                val hot = extremeText(c.hottest, c.tempUnit)
                val cold = extremeText(c.coldest, c.tempUnit)
                val wind = extremeText(c.windiest, c.windUnit)
                Section("$year in review")
                TileGrid(
                    tiles(
                        Triple("Rain", amount(c.rain, c.rainUnit), "${c.rainyDays} rainy days"),
                        Triple("Days with data", c.daysWithData.toString(), null),
                        Triple("Hottest", hot.first, hot.second),
                        Triple("Coldest", cold.first, cold.second),
                        Triple("Strongest gust", wind.first, wind.second),
                        Triple("Freezing nights", c.freezingNights.toString(), "32°F or colder"),
                        Triple("90°F or hotter", c.days90.toString(), "days"),
                        Triple("100°F or hotter", c.days100.toString(), "days"),
                    ),
                )
                Section("Streaks")
                ListPanel(
                    listOf(
                        row("Longest hot streak", c.longestHot?.let { "${it.days} days" } ?: "--",
                            c.longestHot?.let { "${shortDay(it.from)} to ${shortDay(it.to)}" }),
                        row("Longest dry spell", c.longestDry?.let { "${it.days} days" } ?: "--",
                            c.longestDry?.let { "${shortDay(it.from)} to ${shortDay(it.to)}" }),
                        row("Longest wet spell", c.longestWet?.let { "${it.days} days" } ?: "--",
                            c.longestWet?.let { "${shortDay(it.from)} to ${shortDay(it.to)}" }),
                    ),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { runCatching { shareYearCard(context, c) }.onFailure { shareError = it.message ?: "Couldn't share the picture." } },
                    colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
                ) { Text("Share this year") }
                shareError?.let {
                    Spacer(Modifier.height(10.dp))
                    Note(it, C.bad)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Season Milestones

@Composable
fun MilestonesPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "milestones", { it.milestones() }) { list ->
            if (list.all { it.years.isEmpty() }) {
                EmptyCard("Milestones appear once there are daily summaries that reach these levels.")
            } else {
                list.filter { it.years.isNotEmpty() }.forEach { m ->
                    Section(m.label)
                    ListPanel(
                        m.years.map { y ->
                            row(y.year.toString(), "First ${shortDay(y.first)}", "Last ${shortDay(y.last)}  ·  ${y.count} days")
                        },
                    )
                }
                Text("First and last day of each year that reached the level, from your daily summaries.", style = T.label, color = C.muted)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Anomalies

@Composable
fun AnomaliesPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "anomalies", { it.anomalies() }) { a ->
            Section("Last 24 hours")
            if (a.items.isEmpty()) {
                Note("Nothing unusual. Every reading was within its normal range for the last month.", C.good)
            } else {
                a.items.forEach { x ->
                    Panel(border = C.warn) {
                        Column {
                            Text("${x.label} ${if (x.kind == "high") "unusually high" else "unusually low"}", style = T.heading, color = C.warn)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${withUnit(x.value, x.unit, 1)} at ${x.time}. The last month averaged ${withUnit(x.usual, x.unit, 1)}.",
                                style = T.body, color = C.muted,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (a.checked.isNotEmpty()) {
                Text(
                    "Checked: ${a.checked.joinToString(", ")}. A reading counts when it sits more than 3 standard deviations from the previous 30 days.",
                    style = T.label, color = C.muted,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Alert History

@Composable
fun AlertHistoryPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "alert-history", { it.alertHistory() }) { list ->
            Section("Last 14 days")
            if (list.isEmpty()) {
                EmptyCard("No alerts in the last 14 days.")
            } else {
                ListPanel(
                    list.map { a ->
                        row(a.text, "", Instant.ofEpochSecond(a.ts).atZone(ZoneId.systemDefault()).format(ALERT_TIME))
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Forecast Accuracy

@Composable
fun ForecastAccuracyPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "fc-accuracy", { it.forecastAccuracy() }) { f ->
            if (f.days == 0) {
                EmptyCard("Forecast accuracy appears after Weather Lab has saved a few forecast highs and the days have passed.")
            } else {
                val bias = f.bias
                TileGrid(
                    tiles(
                        Triple("Days compared", f.days.toString(), null),
                        Triple("Average miss", "${fmt(f.averageMiss, 1)}${spaced(f.unit)}", "ignoring direction"),
                        Triple(
                            "Tendency",
                            when {
                                bias == null -> "Balanced"
                                Math.abs(bias) < 0.5 -> "Balanced"
                                bias > 0.0 -> "Runs high"
                                else -> "Runs low"
                            },
                            bias?.let { "${if (it > 0) "+" else ""}${fmt(it, 1)}${spaced(f.unit)} on average" },
                        ),
                    ),
                )
                Section("Recent days")
                ListPanel(
                    f.rows.map { r ->
                        val miss = r.miss
                        row(
                            dayName(r.date), "${temp(r.forecast, f.unit)} → ${temp(r.actual, f.unit)}",
                            if (miss == null) null else "Forecast was ${if (miss > 0) "high" else if (miss < 0) "low" else "right"}" +
                                if (miss != 0.0) " by ${fmt(Math.abs(miss), 1)}${spaced(f.unit)}" else "",
                        )
                    },
                )
                Text("Forecast high from the National Weather Service against what your station recorded.", style = T.label, color = C.muted)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Export Data

@Composable
fun ExportPage(address: String, token: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    PageColumn {
        Section("Daily summaries")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Sends every daily summary (high, low, average, rain, gust, wind run, sunshine and humidity, in US units) " +
                        "as a CSV file you can open in Excel or Sheets, email, or save to Drive.",
                    style = T.body, color = C.muted,
                )
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { WeatherLabApi(address, token).exportDaily() } }
                            result.onSuccess { export ->
                                runCatching { shareCsv(context, export) }
                                    .onFailure { message = it.message ?: "Couldn't share the file." }
                            }.onFailure { message = it.message ?: "Couldn't reach Weather Lab." }
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
                ) { Text(if (busy) "Getting the data..." else "Share CSV file") }
            }
        }
        message?.let {
            Spacer(Modifier.height(10.dp))
            Note(it, C.bad)
        }
    }
}

private fun shareCsv(context: Context, export: CsvExport) {
    val folder = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(folder, export.filename.ifBlank { "weatherlab-daily.csv" })
    file.writeText(export.csv)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share daily summaries"))
}

// ---------------------------------------------------------------------------------------------------------------------
// Season Rewind

@Composable
fun RewindPage(address: String, token: String) {
    var day by remember { mutableStateOf(LocalDate.now().minusDays(1)) }
    var which by remember { mutableIntStateOf(0) }
    val newest = LocalDate.now()
    PageColumn {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { day = day.minusDays(1) }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Earlier day", tint = C.text)
            }
            Text(
                dayLong(day.toString()), fontSize = 16.sp, fontWeight = FontWeight.W700, color = C.text,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { day = day.plusDays(1) }, enabled = day < newest) {
                Icon(
                    Icons.Filled.KeyboardArrowRight, contentDescription = "Later day",
                    tint = if (day < newest) C.text else C.quiet,
                )
            }
        }
        Fetch(address, token, day.toString(), { it.rewind(day.toString()) }) { r ->
            if (r.error != null || !r.found) {
                EmptyCard(r.error ?: "No readings were saved for this day.")
            } else {
                val picked = which.coerceIn(0, r.series.lastIndex)
                Chips(r.series.map { it.label }, picked) { which = it }
                val s = r.series[picked]
                Section(s.label)
                Panel {
                    TouchLine(s.points.map { it.second }, s.points.map { clockTime(it.first) }, spaced(s.unit))
                }
                Text(
                    "Every 10 minutes through the day. Touch and drag along the chart to replay it.",
                    style = T.label, color = C.muted,
                )
            }
        }
    }
}
