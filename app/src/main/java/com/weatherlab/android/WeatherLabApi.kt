package com.weatherlab.android

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One number from Weather Lab, already in the units chosen on the PC, with its unit label. */
data class Measure(val value: Double, val unit: String)

data class Current(val ageSeconds: Int?, val values: Map<String, Measure>, val metric: Boolean)
data class Today(val date: String, val high: Measure?, val low: Measure?, val rain: Measure?, val readings: Int)
data class Status(val station: String, val appVersion: String, val ageSeconds: Int?)
data class Alert(val ts: Long, val text: String)
/** The last 24 hours of temperature, oldest first: (timestamp in seconds, value). */
data class History(val unit: String, val points: List<Pair<Long, Double>>)
data class WeekDay(val date: String, val high: Double?, val low: Double?, val rain: Double?)
data class Week(val days: List<WeekDay>, val tempUnit: String, val rainUnit: String)
data class Strike(val ts: Long, val ageSeconds: Long, val distance: Measure)
/** trend is one of none, overhead, approaching, receding, steady. */
data class Storm(val last: Strike?, val strikes10m: Int, val strikes30m: Int, val strikesToday: Int, val trend: String)

/**
 * history, week and storm come from newer versions of Weather Lab (0.32 and up). With an older Weather Lab they are
 * null and the app simply leaves those cards out.
 */
data class Snapshot(
    val status: Status,
    val current: Current,
    val today: Today,
    val alerts: List<Alert>,
    val history: History? = null,
    val week: Week? = null,
    val storm: Storm? = null,
)

/** One day of the 7 and 30 day charts. Missing figures are null. */
data class ChartDay(val date: String, val high: Double?, val low: Double?, val avg: Double?, val rain: Double?, val gust: Double?)
data class Charts(val days: List<ChartDay>, val tempUnit: String, val rainUnit: String, val windUnit: String)
data class DayRow(val date: String, val high: Double?, val low: Double?, val rain: Double?)
data class DayList(val days: List<DayRow>, val tempUnit: String, val rainUnit: String)
data class DayValue(val label: String, val value: Double?, val unit: String)
/** Every figure kept for one day. found is false when Weather Lab has no summary for that date. */
data class DayDetail(val date: String, val found: Boolean, val estimated: Boolean, val partial: Boolean, val values: List<DayValue>)
data class RecordItem(val label: String, val value: Double?, val unit: String, val date: String, val estimated: Boolean)

class ApiException(message: String, val code: Int = 0) : Exception(message)

/** Talks to Weather Lab's read-only API (api_version 1). Every call is a plain GET with the token in the header. */
class WeatherLabApi(address: String, private val token: String) {

    private val base: String = normalise(address)

    private fun get(path: String): JSONObject {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) {
                throw ApiException(json.optString("error", "Weather Lab answered with status $code."), code)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    fun status(): Status {
        val j = get("/api/v1/status")
        return Status(
            station = j.optString("station"),
            appVersion = j.optString("app_version"),
            ageSeconds = if (j.isNull("latest_reading_age_seconds")) null else j.getInt("latest_reading_age_seconds"),
        )
    }

    fun current(): Current {
        val j = get("/api/v1/current")
        val metric = j.optBoolean("metric")
        val reading = j.optJSONObject("reading") ?: return Current(null, emptyMap(), metric)
        val values = reading.getJSONObject("values")
        val map = LinkedHashMap<String, Measure>()
        values.keys().forEach { key ->
            val item = values.getJSONObject(key)
            map[key] = Measure(item.getDouble("value"), item.optString("unit"))
        }
        return Current(reading.optInt("age_seconds"), map, metric)
    }

    fun today(): Today {
        val j = get("/api/v1/today")
        fun measure(name: String): Measure? =
            j.optJSONObject(name)?.let { Measure(it.getDouble("value"), it.optString("unit")) }
        return Today(j.optString("date"), measure("high"), measure("low"), measure("rain"), j.optInt("readings"))
    }

    fun alerts(): List<Alert> {
        val list = get("/api/v1/alerts").optJSONArray("alerts") ?: return emptyList()
        return (0 until list.length()).map { i ->
            val item = list.getJSONObject(i)
            Alert(item.getLong("ts"), item.getString("text"))
        }
    }

    fun history(): History {
        val j = get("/api/v1/history")
        val list = j.optJSONArray("points")
        val points = if (list == null) emptyList() else (0 until list.length()).mapNotNull { i ->
            val p = list.optJSONArray(i) ?: return@mapNotNull null
            if (p.length() < 2 || p.isNull(1)) null else Pair(p.getLong(0), p.getDouble(1))
        }
        return History(j.optString("unit"), points)
    }

    fun week(): Week {
        val j = get("/api/v1/week")
        val list = j.optJSONArray("days")
        fun num(o: JSONObject, key: String): Double? = if (o.isNull(key)) null else o.optDouble(key)
        val days = if (list == null) emptyList() else (0 until list.length()).map { i ->
            val d = list.getJSONObject(i)
            WeekDay(d.optString("date"), num(d, "high"), num(d, "low"), num(d, "rain"))
        }
        return Week(days, j.optString("temp_unit"), j.optString("rain_unit"))
    }

    fun storm(): Storm {
        val j = get("/api/v1/storm")
        val last = j.optJSONObject("last_strike")?.let {
            val dist = it.getJSONObject("distance")
            Strike(it.getLong("ts"), it.optLong("age_seconds"), Measure(dist.getDouble("value"), dist.optString("unit")))
        }
        return Storm(
            last, j.optInt("strikes_10m"), j.optInt("strikes_30m"), j.optInt("strikes_today"),
            j.optString("trend", "none"),
        )
    }

    private fun JSONObject.num(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key)

    /** days is 7 or 30. Needs Weather Lab 0.35 or newer. */
    fun charts(days: Int): Charts {
        val j = get("/api/v1/charts/$days")
        val list = j.optJSONArray("days")
        val out = if (list == null) emptyList() else (0 until list.length()).map { i ->
            val d = list.getJSONObject(i)
            ChartDay(d.optString("date"), d.num("high"), d.num("low"), d.num("avg"), d.num("rain"), d.num("gust"))
        }
        return Charts(out, j.optString("temp_unit"), j.optString("rain_unit"), j.optString("wind_unit"))
    }

    fun days(): DayList {
        val j = get("/api/v1/days")
        val list = j.optJSONArray("days")
        val out = if (list == null) emptyList() else (0 until list.length()).map { i ->
            val d = list.getJSONObject(i)
            DayRow(d.optString("date"), d.num("high"), d.num("low"), d.num("rain"))
        }
        return DayList(out, j.optString("temp_unit"), j.optString("rain_unit"))
    }

    /** which is "yesterday" or a date such as 2026-10-20. */
    fun day(which: String): DayDetail {
        val j = get("/api/v1/days/$which")
        val list = j.optJSONArray("values")
        val values = if (list == null) emptyList() else (0 until list.length()).map { i ->
            val v = list.getJSONObject(i)
            DayValue(v.optString("label"), v.num("value"), v.optString("unit"))
        }
        return DayDetail(
            j.optString("date"), j.optBoolean("found"), j.optBoolean("estimated"), j.optBoolean("partial"), values,
        )
    }

    fun records(): List<RecordItem> {
        val list = get("/api/v1/records").optJSONArray("records") ?: return emptyList()
        return (0 until list.length()).map { i ->
            val r = list.getJSONObject(i)
            RecordItem(r.optString("label"), r.num("value"), r.optString("unit"), r.optString("date"), r.optBoolean("estimated"))
        }
    }

    /** The extra cards are optional: any trouble (an older Weather Lab, a hiccup) just leaves that card out. */
    private fun <T> optional(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        null
    }

    fun snapshot(): Snapshot = Snapshot(
        status(), current(), today(), alerts(),
        history = optional { history() }, week = optional { week() }, storm = optional { storm() },
    )

    companion object {
        /** Accepts "192.168.1.50:8600", "http://192.168.1.50:8600" or a bare address, and returns "http://host:port". */
        fun normalise(address: String): String {
            // Also accepts a pasted full address such as http://192.168.1.50:8600/api/v1/status.
            val trimmed = address.trim().substringBefore("/api/").trimEnd('/')
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
            return if (withScheme.substringAfter("://").contains(":")) withScheme else "$withScheme:8600"
        }
    }
}
