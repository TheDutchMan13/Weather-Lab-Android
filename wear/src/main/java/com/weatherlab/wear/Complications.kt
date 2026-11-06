package com.weatherlab.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlin.math.roundToInt

// Watch-face complications (0.7.28): temperature (short, a low-to-high dial, or a long line), rain chance, wind, UV and
// sunset. They all read the same saved copy from WatchRepo, so adding several doesn't mean more requests to the PC.

private fun text(s: String) = PlainComplicationText.Builder(s).build()

/** The shared part: get the data, then let each complication say what to show. */
abstract class WeatherComplication : SuspendingComplicationDataSourceService() {
    abstract fun build(d: WatchData, type: ComplicationType): ComplicationData?

    /** What the watch face's picker shows. Built from made-up but believable numbers. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(PREVIEW, type)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? = try {
        build(WatchRepo.get(this, maxAgeMs = 10 * 60 * 1000L), request.complicationType) ?: NoDataComplicationData()
    } catch (e: Exception) {
        NoDataComplicationData()
    }

    protected fun open(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    protected fun short(value: String, title: String?, describe: String): ComplicationData =
        ShortTextComplicationData.Builder(text(value), text(describe)).setTapAction(open())
            .apply { if (title != null) setTitle(text(title)) }.build()

    protected fun ranged(value: Float, min: Float, max: Float, shown: String, title: String?, describe: String): ComplicationData =
        RangedValueComplicationData.Builder(value.coerceIn(min, max), min, max, text(describe))
            .setText(text(shown)).setTapAction(open())
            .apply { if (title != null) setTitle(text(title)) }.build()

    companion object {
        val PREVIEW = WatchData(
            temp = 72.0, tempUnit = "°F", feelsLike = 70.0, humidity = 24.0, wind = 8.0, windUnit = "mph",
            high = 86.0, low = 61.0, rainChance = "60%", headline = null, ageSeconds = 60,
            windDir = "NW", uv = 3.0, uvWord = "Moderate", sunset = "5:31 pm",
        )

        /** Asks every complication and tile to refresh, after new settings or new data. */
        fun refreshAll(context: Context) {
            listOf(
                TempComplicationService::class.java, RainComplicationService::class.java,
                WindComplicationService::class.java, UvComplicationService::class.java,
                SunsetComplicationService::class.java,
            ).forEach {
                runCatching {
                    ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, it)).requestUpdateAll()
                }
            }
        }
    }
}

/** The temperature: "72°" with today's high, a dial from today's low to high, or "72° · Hi 86° · Rain 60%". */
class TempComplicationService : WeatherComplication() {
    override fun build(d: WatchData, type: ComplicationType): ComplicationData? {
        val t = d.temp ?: return null
        return when (type) {
            ComplicationType.SHORT_TEXT -> short(d.tempText, d.high?.let { "Hi ${it.roundToInt()}°" }, "Temperature ${d.tempText}")
            ComplicationType.RANGED_VALUE -> {
                val lo = minOf(d.low ?: t, t).toFloat()
                var hi = maxOf(d.high ?: t, t).toFloat()
                if (hi - lo < 1f) hi = lo + 1f
                ranged(t.toFloat(), lo, hi, d.tempText, null, "Temperature ${d.tempText}, today ${d.highLowText}")
            }
            ComplicationType.LONG_TEXT -> {
                val line = listOfNotNull(
                    d.tempText,
                    d.high?.let { "Hi ${it.roundToInt()}°" },
                    d.rainChanceNow?.let { "Rain $it%" },
                ).joinToString(" · ")
                LongTextComplicationData.Builder(text(line), text(line)).setTapAction(open()).build()
            }
            else -> null
        }
    }
}

/** The chance of rain in the next hour, as text or a 0 to 100 dial. */
class RainComplicationService : WeatherComplication() {
    override fun build(d: WatchData, type: ComplicationType): ComplicationData? {
        val chance = d.rainChanceNow ?: return null
        return when (type) {
            ComplicationType.SHORT_TEXT -> short("$chance%", "rain", "Rain chance $chance percent")
            ComplicationType.RANGED_VALUE -> ranged(chance.toFloat(), 0f, 100f, "$chance%", "rain", "Rain chance $chance percent")
            else -> null
        }
    }
}

/** Wind speed with its direction. */
class WindComplicationService : WeatherComplication() {
    override fun build(d: WatchData, type: ComplicationType): ComplicationData? {
        val w = d.wind ?: return null
        if (type != ComplicationType.SHORT_TEXT) return null
        val title = listOfNotNull(d.windUnit.takeIf { it.isNotBlank() }, d.windDir).joinToString(" ")
        return short("${w.roundToInt()}", title.ifBlank { null }, "Wind ${w.roundToInt()} $title")
    }
}

/** The UV index now, as text or a 0 to 11 dial. */
class UvComplicationService : WeatherComplication() {
    override fun build(d: WatchData, type: ComplicationType): ComplicationData? {
        val uv = d.uv ?: return null
        val word = when {
            uv < 3 -> "low"
            uv < 6 -> "mod"
            uv < 8 -> "high"
            uv < 11 -> "v.high"
            else -> "extr"
        }
        val shown = "UV ${uv.roundToInt()}"
        return when (type) {
            ComplicationType.SHORT_TEXT -> short(shown, word, "UV index ${uv.roundToInt()}, ${d.uvWord ?: word}")
            ComplicationType.RANGED_VALUE -> ranged(uv.toFloat(), 0f, 11f, shown, word, "UV index ${uv.roundToInt()}")
            else -> null
        }
    }
}

/** When the sun sets today (the last light of the day). */
class SunsetComplicationService : WeatherComplication() {
    override fun build(d: WatchData, type: ComplicationType): ComplicationData? {
        val set = d.sunset ?: return null
        if (type != ComplicationType.SHORT_TEXT) return null
        val shown = set.removeSuffix(" pm").removeSuffix(" am")
        return short(shown, "sunset", "Sunset at $set")
    }
}
