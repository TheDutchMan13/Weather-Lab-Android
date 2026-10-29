package com.weatherlab.wear

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

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
}

class WatchApiException(message: String) : Exception(message)

/** A small copy of the phone app's client: just the three requests the watch needs. */
object WatchApi {
    /** Also accepts a pasted full address such as http://192.168.1.50:8600/api/v1/status. */
    fun normalise(address: String): String {
        val trimmed = address.trim().substringBefore("/api/").trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        return if (withScheme.substringAfter("://").contains(":")) withScheme else "$withScheme:8600"
    }

    private fun get(base: String, token: String, path: String): JSONObject {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) throw WatchApiException(json.optString("error", "Weather Lab answered with status $code."))
            return json
        } finally {
            conn.disconnect()
        }
    }

    /** Reads the current reading, today's high and low, and the briefing. The briefing is optional (needs Weather Lab 0.37.26+). */
    fun load(address: String, token: String): WatchData {
        val base = normalise(address)
        val current = get(base, token, "/api/v1/current")
        val today = get(base, token, "/api/v1/today")
        val briefing = try {
            get(base, token, "/api/v1/briefing")
        } catch (e: Exception) {
            null
        }
        return parse(current, today, briefing)
    }

    fun parse(current: JSONObject, today: JSONObject, briefing: JSONObject?): WatchData {
        val reading = current.optJSONObject("reading")
        val values = reading?.optJSONObject("values")
        fun value(key: String): Pair<Double, String>? =
            values?.optJSONObject(key)?.let { it.getDouble("value") to it.optString("unit") }
        fun day(name: String): Double? = today.optJSONObject(name)?.let { if (it.has("value")) it.getDouble("value") else null }

        val temp = value("tempf")
        val wind = value("windspeedmph")
        val rain = briefing?.optJSONArray("tiles")?.let { tiles ->
            (0 until tiles.length()).map { tiles.getJSONObject(it) }
                .firstOrNull { it.optString("label").contains("rain", ignoreCase = true) && !it.isNull("value") }
                ?.let { tile ->
                    val unit = tile.optString("unit")
                    val raw = tile.get("value")
                    val text = if (raw is Number && raw.toDouble() % 1.0 == 0.0) raw.toLong().toString() else raw.toString()
                    if (unit == "%" || unit.startsWith("°")) text + unit else if (unit.isBlank()) text else "$text $unit"
                }
        }
        return WatchData(
            temp = temp?.first,
            tempUnit = temp?.second.orEmpty(),
            feelsLike = value("feelslikef")?.first,
            humidity = value("humidity")?.first,
            wind = wind?.first,
            windUnit = wind?.second.orEmpty(),
            high = day("high"),
            low = day("low"),
            rainChance = rain,
            headline = briefing?.optString("headline")?.takeIf { it.isNotBlank() },
            ageSeconds = if (reading != null && reading.has("age_seconds")) reading.optInt("age_seconds") else null,
        )
    }
}
