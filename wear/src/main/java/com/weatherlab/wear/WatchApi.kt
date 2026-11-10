package com.weatherlab.wear

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** One hour of the forecast: "9 am", the temperature and the chance of rain (0 to 100), if known. */
data class WatchHour(val label: String, val temp: Int?, val rainChance: Int?)

/** Lightning near the station, when there has been a strike in the last 30 minutes. */
data class WatchStorm(val distance: String, val trend: String, val strikes30m: Int, val lastAgeSeconds: Int) {
    /** "closer", "moving away", "overhead" or "steady". */
    val trendText: String
        get() = when (trend) {
            "approaching" -> "closer"
            "receding" -> "moving away"
            "overhead" -> "overhead"
            else -> "steady"
        }
}

/** An alert Weather Lab logged on the PC: when (epoch seconds) and what. */
data class WatchAlert(val ts: Long, val text: String)

/** What the watch shows, already in the units Weather Lab uses (the same as the phone app). */
data class WatchData(
    val temp: Double?,
    val tempUnit: String,
    val feelsLike: Double?,
    val humidity: Double?,
    val wind: Double?,
    val windUnit: String,
    val high: Double?,
    val low: Double?,
    val rainChance: String?,
    val headline: String?,
    val ageSeconds: Int?,
    // 0.7.28
    val readingTs: Long? = null,
    val windDir: String? = null,
    val uv: Double? = null,
    val uvWord: String? = null,
    val pressure: Double? = null,
    val pressureUnit: String = "",
    val pressureArrow: String = "",
    val rainToday: Double? = null,
    val rainUnit: String = "",
    val spark: List<Double> = emptyList(),
    val hours: List<WatchHour> = emptyList(),
    val storm: WatchStorm? = null,
    val alerts: List<WatchAlert> = emptyList(),
    val nwsAlerts: List<String> = emptyList(),
    val records: List<String> = emptyList(),
    val sunset: String? = null,
    /** Set when the PC couldn't be reached and this is the last saved reading: when it was saved (epoch ms). */
    val offlineSince: Long? = null,
) {
    /** "72°", or "--°" when the station has not reported. */
    val tempText: String get() = temp?.let { "${it.roundToInt()}°" } ?: "--°"
    val highLowText: String
        get() = "H ${high?.roundToInt() ?: "--"}°  L ${low?.roundToInt() ?: "--"}°"
    val feelsText: String? get() = feelsLike?.let { "Feels ${it.roundToInt()}°" }
    val detailText: String
        get() = listOfNotNull(
            humidity?.let { "${it.roundToInt()}% humidity" },
            wind?.let { "wind ${it.roundToInt()} $windUnit" },
        ).joinToString(", ")

    /** "NW 8 mph · 24% · UV 3" */
    val windLine: String
        get() = listOfNotNull(
            wind?.let { listOfNotNull(windDir, "${it.roundToInt()} $windUnit".trim()).joinToString(" ") },
            humidity?.let { "${it.roundToInt()}%" },
            uv?.let { "UV ${it.roundToInt()}" },
        ).joinToString(" · ")

    /** "30.02 in ↓ · Rain 0.00 in" */
    val pressureLine: String
        get() = listOfNotNull(
            pressure?.let { "${fmt(it, if (pressureUnit == "in") 2 else 0)} $pressureUnit $pressureArrow".trim() },
            rainToday?.let { "Rain ${fmt(it, if (rainUnit == "in") 2 else 1)} $rainUnit".trim() },
        ).joinToString(" · ")

    /** The chance of rain now, 0 to 100: the first forecast hour, or the briefing's number. */
    val rainChanceNow: Int?
        get() = hours.firstOrNull()?.rainChance ?: rainChance?.filter { it.isDigit() }?.toIntOrNull()

    /** "Rain likely in about 2 h", "Rain likely now" or "Dry for the next 12 hours". Null without a forecast. */
    val rainSoon: String?
        get() {
            if (hours.isEmpty()) return null
            val wet = hours.take(12).indexOfFirst { (it.rainChance ?: 0) >= 50 }
            return when {
                wet == 0 -> "Rain likely now"
                wet > 0 -> "Rain likely in about $wet h"
                else -> "Dry for the next ${minOf(12, hours.size)} hours"
            }
        }

    /** "8:05 am": the time of the newest reading, for the offline note. */
    val readingTime: String?
        get() = readingTs?.let { clock(Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalTime()) }

    /** "Offline · last reading 8:05 am", or null while the PC answers. */
    val offlineText: String?
        get() = if (offlineSince == null) null else "Offline · last reading ${readingTime ?: "earlier"}"
}

private fun fmt(v: Double, digits: Int): String = String.format(Locale.US, "%.${digits}f", v)

/** "9 am", "12 pm", "5:31 pm". */
internal fun clock(t: LocalTime, withMinutes: Boolean = true): String {
    val pattern = if (withMinutes && t.minute != 0) "h:mm a" else "h a"
    return t.format(DateTimeFormatter.ofPattern(pattern, Locale.US)).lowercase(Locale.US)
}

class WatchApiException(message: String) : Exception(message)

/** A small copy of the phone app's client: just the requests the watch needs. */
object WatchApi {
    /** Read on every refresh. "current" and "today" are needed; the rest are extras that older Weather Labs may not have. */
    val CORE = listOf("current", "today", "briefing", "history", "forecast", "storm", "alerts")

    /** Change slowly: read at most every 30 minutes. */
    val SLOW = listOf("records", "sun", "tiles")

    /** Also accepts a pasted full address such as http://192.168.1.50:8600/api/v1/status. */
    fun normalise(address: String): String {
        val trimmed = address.trim().substringBefore("/api/").trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        return if (withScheme.substringAfter("://").contains(":")) withScheme else "$withScheme:8600"
    }

    // PCs seen accepting signed requests in this run, and older PCs that only take the token (0.7.33).
    private val signedOk = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val oldPcs = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun send(base: String, token: String, path: String, signed: Boolean): Pair<Int, String> {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            if (signed) RequestSigner.sign(conn, token, path) else conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun get(base: String, token: String, path: String): String {
        var signed = base !in oldPcs
        var (code, text) = send(base, token, path, signed)
        // Weather Lab before 0.37.59 doesn't know signed requests. Ask once with the token, unless this PC has already
        // accepted a signed request in this run.
        if (signed && code == 401 && base !in signedOk) {
            val plain = send(base, token, path, false)
            if (plain.first != 401) {
                oldPcs.add(base)
                code = plain.first
                text = plain.second
                signed = false
            }
        }
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text).optString("error") }.getOrNull()
            throw WatchApiException(msg?.takeIf { it.isNotBlank() } ?: "Weather Lab answered with status $code.")
        }
        if (signed) {
            signedOk.add(base)
            oldPcs.remove(base)
        }
        return text
    }

    /** Reads the given endpoints as raw JSON text. "current" and "today" must work; the others may fail on their own. */
    fun fetch(address: String, token: String, names: List<String>): Map<String, String> {
        val base = normalise(address)
        val out = mutableMapOf<String, String>()
        for (name in names) {
            try {
                out[name] = get(base, token, "/api/v1/$name")
            } catch (e: Exception) {
                if (name == "current" || name == "today") throw e
            }
        }
        return out
    }

    /** The first entry point: current, today and the briefing. */
    fun parse(current: JSONObject, today: JSONObject, briefing: JSONObject?): WatchData =
        parse(buildMap {
            put("current", current.toString())
            put("today", today.toString())
            briefing?.let { put("briefing", it.toString()) }
        }, System.currentTimeMillis() / 1000)

    private fun obj(raw: Map<String, String>, name: String): JSONObject? =
        raw[name]?.let { runCatching { JSONObject(it) }.getOrNull() }

    // Null values (a sensor offline, or today's high just after midnight) are skipped, not a crash (0.7.26).
    private fun num(o: JSONObject?, key: String): Double? =
        if (o == null || !o.has(key) || o.isNull(key)) null else o.optDouble(key).takeUnless { it.isNaN() }

    private fun list(a: JSONArray?): List<JSONObject> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    fun parse(raw: Map<String, String>, nowSec: Long): WatchData {
        val current = obj(raw, "current") ?: JSONObject()
        val today = obj(raw, "today") ?: JSONObject()
        val briefing = obj(raw, "briefing")
        val reading = current.optJSONObject("reading")
        val values = reading?.optJSONObject("values")
        val words = reading?.optJSONObject("words")
        fun value(key: String): Pair<Double, String>? =
            values?.optJSONObject(key)?.let { o -> num(o, "value")?.let { it to o.optString("unit") } }
        fun day(name: String): Double? = num(today.optJSONObject(name), "value")

        val temp = value("tempf")
        val wind = value("windspeedmph")
        val baro = value("baromrelin")
        val rain = briefing?.optJSONArray("tiles")?.let { tiles ->
            list(tiles).firstOrNull { it.optString("label").contains("rain", ignoreCase = true) && !it.isNull("value") }
                ?.let { tile ->
                    val unit = tile.optString("unit")
                    val v = tile.get("value")
                    val text = if (v is Number && v.toDouble() % 1.0 == 0.0) v.toLong().toString() else v.toString()
                    if (unit == "%" || unit.startsWith("°")) text + unit else if (unit.isBlank()) text else "$text $unit"
                }
        }

        // Last 24 hours of temperature as [time, value] pairs, for the small graph.
        val spark = obj(raw, "history")?.optJSONArray("points")?.let { pts ->
            (0 until pts.length()).mapNotNull { i ->
                pts.optJSONArray(i)?.let { p -> if (p.length() < 2 || p.isNull(1)) null else p.optDouble(1).takeUnless { it.isNaN() } }
            }
        }.orEmpty()

        // Forecast: the next 12 hours from now on, and any National Weather Service alerts.
        val forecast = obj(raw, "forecast")?.takeIf { it.optBoolean("available") }
        val hours = list(forecast?.optJSONArray("hours")).mapNotNull { h ->
            val start = runCatching { OffsetDateTime.parse(h.optString("start")) }.getOrNull() ?: return@mapNotNull null
            if (start.toEpochSecond() + 3600 <= nowSec) return@mapNotNull null
            WatchHour(
                clock(start.atZoneSameInstant(ZoneId.systemDefault()).toLocalTime(), withMinutes = false),
                num(h, "temp")?.roundToInt(),
                num(h, "rain_chance")?.roundToInt(),
            )
        }.take(12)
        val nwsAlerts = list(forecast?.optJSONArray("alerts")).map { it.optString("event") }.filter { it.isNotBlank() }.distinct()

        // Lightning in the last 30 minutes.
        val stormObj = obj(raw, "storm")
        val last = stormObj?.optJSONObject("last_strike")
        val storm = if (stormObj != null && last != null && stormObj.optString("trend") != "none" &&
            last.optInt("age_seconds", 99999) <= 1800
        ) {
            val d = last.optJSONObject("distance")
            val dist = num(d, "value")
            WatchStorm(
                if (dist == null) "nearby" else "${dist.roundToInt()} ${d?.optString("unit").orEmpty()}".trim(),
                stormObj.optString("trend"), stormObj.optInt("strikes_30m"), last.optInt("age_seconds"),
            )
        } else null

        // Alerts the PC logged in the last 24 hours, newest first.
        val alerts = list(obj(raw, "alerts")?.optJSONArray("alerts"))
            .map { WatchAlert(it.optDouble("ts").toLong(), it.optString("text")) }
            .filter { it.text.isNotBlank() && nowSec - it.ts < 24 * 3600 }
            .sortedByDescending { it.ts }

        // Today against the station's all-time records.
        val recs = list(obj(raw, "records")?.optJSONArray("records")).associateBy { it.optString("key") }
        val todayDate = today.optString("date")
        fun record(key: String): Pair<Double, String>? =
            recs[key]?.let { r -> num(r, "value")?.let { it to r.optString("date") } }
        val high = day("high")
        val low = day("low")
        val rainDay = day("rain")
        val records = buildList {
            record("tempf_max")?.let { (v, d) -> if (high != null && (high >= v || d == todayDate)) add("Record high today") }
            record("tempf_min")?.let { (v, d) -> if (low != null && (low <= v || d == todayDate)) add("Record low today") }
            record("rain_in")?.let { (v, d) ->
                if (rainDay != null && rainDay > 0 && (rainDay >= v || d == todayDate)) add("Wettest day on record")
            }
        }

        val pressureWord = obj(raw, "tiles")?.optJSONObject("pressure")?.optString("word").orEmpty().lowercase(Locale.US)
        val arrow = when {
            pressureWord.contains("ris") -> "↑"
            pressureWord.contains("fall") -> "↓"
            pressureWord.isNotBlank() -> "→"
            else -> ""
        }
        val sunset = obj(raw, "sun")?.let { if (it.isNull("sunset")) null else it.optString("sunset") }
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { clock(LocalTime.parse(it)) }.getOrNull() }

        return WatchData(
            temp = temp?.first,
            tempUnit = temp?.second.orEmpty(),
            feelsLike = value("feelslikef")?.first,
            humidity = value("humidity")?.first,
            wind = wind?.first,
            windUnit = wind?.second.orEmpty(),
            high = high,
            low = low,
            rainChance = rain,
            headline = briefing?.optString("headline")?.takeIf { it.isNotBlank() },
            ageSeconds = if (reading != null && reading.has("age_seconds")) reading.optInt("age_seconds") else null,
            readingTs = if (reading != null && reading.has("ts")) reading.optDouble("ts").toLong() else null,
            windDir = words?.optString("wind_direction")?.takeIf { it.isNotBlank() },
            uv = value("uv")?.first,
            uvWord = words?.optString("uv")?.takeIf { it.isNotBlank() },
            pressure = baro?.first,
            pressureUnit = baro?.second.orEmpty(),
            pressureArrow = arrow,
            rainToday = rainDay,
            rainUnit = today.optJSONObject("rain")?.optString("unit").orEmpty(),
            spark = spark,
            hours = hours,
            storm = storm,
            alerts = alerts,
            nwsAlerts = nwsAlerts,
            records = records,
            sunset = sunset,
        )
    }
}
