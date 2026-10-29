package com.weatherlab.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One reading saved on the phone. The units are the ones Weather Lab used when the reading arrived. */
internal data class StoredReading(
    val ts: Long, val temp: Double?, val humidity: Double?, val wind: Double?, val gust: Double?, val rain: Double?,
    val tempUnit: String = "°F", val rainUnit: String = "in", val windUnit: String = "mph",
)

internal data class StoredDay(val date: String, val high: Double?, val low: Double?, val rain: Double?, val gust: Double?, val readings: Int)

/** Roll the saved readings up into days, newest first. Rain today only goes up through a day, so the day's rain is its largest value. */
internal fun summarizeDays(rows: List<StoredReading>, zone: ZoneId = ZoneId.systemDefault()): List<StoredDay> =
    rows.groupBy { Instant.ofEpochSecond(it.ts).atZone(zone).toLocalDate().toString() }
        .map { (date, list) ->
            StoredDay(
                date,
                list.mapNotNull { it.temp }.maxOrNull(),
                list.mapNotNull { it.temp }.minOrNull(),
                list.mapNotNull { it.rain }.maxOrNull(),
                list.mapNotNull { it.gust }.maxOrNull(),
                list.size,
            )
        }
        .sortedByDescending { it.date }

/**
 * The phone's own history (0.7.17): when "Keep history on this phone" is on, each reading the phone receives is saved
 * (one every 5 minutes at most), so Saved on this phone still shows days and a 24-hour line without the PC.
 */
internal object PhoneHistory {
    private const val MIN_GAP_SECONDS = 270L

    private class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, "phone_history.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE readings (ts INTEGER PRIMARY KEY, temp REAL, hum REAL, wind REAL, gust REAL, rain REAL, " +
                    "tu TEXT, ru TEXT, wu TEXT)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    /** Save the reading in [snapshot] if it is new enough, and drop anything older than [keepDays]. */
    fun record(context: Context, snapshot: Snapshot, keepDays: Int) {
        runCatching {
            val v = snapshot.current.values
            val now = System.currentTimeMillis() / 1000
            val ts = now - (snapshot.current.ageSeconds ?: 0)
            val db = Db(context).writableDatabase
            val last = db.rawQuery("SELECT MAX(ts) FROM readings", null).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L }
            if (ts - last < MIN_GAP_SECONDS) return
            val values = ContentValues().apply {
                put("ts", ts)
                v["tempf"]?.let { put("temp", it.value); put("tu", it.unit) }
                v["humidity"]?.let { put("hum", it.value) }
                v["windspeedmph"]?.let { put("wind", it.value); put("wu", it.unit) }
                v["windgustmph"]?.let { put("gust", it.value) }
                v["dailyrainin"]?.let { put("rain", it.value); put("ru", it.unit) }
            }
            db.insertWithOnConflict("readings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.delete("readings", "ts < ?", arrayOf((now - keepDays * 86400L).toString()))
        }
    }

    /** Saved readings since [sinceSeconds] (Unix time), oldest first. */
    fun readings(context: Context, sinceSeconds: Long = 0): List<StoredReading> = runCatching {
        Db(context).readableDatabase.rawQuery(
            "SELECT ts, temp, hum, wind, gust, rain, tu, ru, wu FROM readings WHERE ts >= ? ORDER BY ts",
            arrayOf(sinceSeconds.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    fun d(i: Int) = if (c.isNull(i)) null else c.getDouble(i)
                    add(
                        StoredReading(
                            c.getLong(0), d(1), d(2), d(3), d(4), d(5),
                            c.getString(6) ?: "°F", c.getString(7) ?: "in", c.getString(8) ?: "mph",
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    fun clear(context: Context) {
        runCatching { Db(context).writableDatabase.delete("readings", null, null) }
    }
}

private val DAY_TEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

private fun dayText(date: String): String = runCatching { LocalDate.parse(date).format(DAY_TEXT) }.getOrDefault(date)

private fun fmt(v: Double?, unit: String, decimals: Int = 0): String =
    if (v == null) "--" else "${"%.${decimals}f".format(v)} $unit".trim()

/** More, Saved on this phone: the days and the last 24 hours the phone kept itself. Works without the PC. */
@Composable
fun PhoneHistoryPage(prefs: Prefs) {
    val context = LocalContext.current
    var readings by remember { mutableStateOf<List<StoredReading>?>(null) }
    var cleared by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(cleared) {
        readings = withContext(Dispatchers.IO) { PhoneHistory.readings(context) }
    }
    PageColumn {
        val rows = readings
        if (!prefs.offlineHistory && rows.isNullOrEmpty()) {
            EmptyCard("Turn on Keep history on this phone in Settings. The phone then saves what it receives so these pages work without your PC.")
            return@PageColumn
        }
        if (rows == null) {
            LoadingCard()
            return@PageColumn
        }
        if (!prefs.offlineHistory) Note("Saving is off. Showing what was saved earlier.", C.warn)
        if (rows.isEmpty()) {
            EmptyCard("Nothing saved yet. The first reading is saved the next time the phone hears from your PC.")
            return@PageColumn
        }
        val days = summarizeDays(rows)
        val last = rows.last()
        Section("Last 24 hours")
        val day = rows.filter { it.ts >= System.currentTimeMillis() / 1000 - 86400 }.mapNotNull { it.temp }
        if (day.size >= 2) {
            Panel { TrendChart(day, "°") }
        } else {
            EmptyCard("Not enough readings in the last 24 hours yet.")
        }
        Section("Days saved on this phone")
        ListPanel(
            days.take(60).map { d ->
                {
                    InfoRow(
                        dayText(d.date),
                        fmt(d.high, "°", 0) + " / " + fmt(d.low, "°", 0),
                        sub = "Rain ${fmt(d.rain, last.rainUnit, 2)} · gust ${fmt(d.gust, last.windUnit)} · ${d.readings} readings",
                    )
                }
            },
        )
        Section("Storage")
        Panel {
            InfoRow(
                "${rows.size} readings", "",
                sub = "From ${dayText(days.last().date)} to ${dayText(days.first().date)}. Kept for ${prefs.historyDays} days.",
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { PhoneHistory.clear(context) }
                    cleared++
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = C.card, contentColor = C.bad),
        ) { Text("Clear saved history") }
    }
}
