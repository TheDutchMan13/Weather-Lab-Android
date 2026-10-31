package com.weatherlab.android

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

// A small home-screen widget in the app's colours: temperature, condition, today's high and low.

private class WidgetData(
    val temp: String,
    val line: String,
    val hiLo: String,
    val configured: Boolean,
    val message: String,
    val notice: String = "",  // lightning close by or old data (0.7.25); blank when there is nothing to say
)

class WeatherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = Prefs(context)
        val pal = palette(Scheme.from(prefs.scheme))
        val data = load(prefs)
        provideContent {
            val text = ColorProvider(pal.text)
            val muted = ColorProvider(pal.muted)
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(pal.card)
                    .cornerRadius(18.dp)
                    .padding(14.dp)
                    .clickable(actionStartActivity(android.content.Intent(context, MainActivity::class.java))),
            ) {
                if (!data.configured || data.message.isNotEmpty()) {
                    Text("Weather Lab", style = TextStyle(color = text, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    Text(data.message, style = TextStyle(color = muted, fontSize = 12.sp))
                } else {
                    Text(data.temp, style = TextStyle(color = text, fontSize = 34.sp, fontWeight = FontWeight.Bold))
                    Text(data.line, style = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                    Text(data.hiLo, style = TextStyle(color = muted, fontSize = 13.sp))
                    if (data.notice.isNotEmpty()) {
                        Text(data.notice, style = TextStyle(color = ColorProvider(pal.warn), fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }

    private suspend fun load(prefs: Prefs): WidgetData {
        if (prefs.address.isBlank() || prefs.token.isBlank()) {
            return WidgetData("", "", "", false, "Open the app and add your address and token.")
        }
        return try {
            val api = WeatherLabApi(prefs.address, prefs.token)
            val (current, today) = withContext(Dispatchers.IO) { Pair(api.current(), api.today()) }
            // Older Weather Lab versions have no storm data; the widget just leaves the lightning line out.
            val storm = withContext(Dispatchers.IO) { try { api.storm() } catch (e: Exception) { null } }
            val v = current.values
            val temp = v["tempf"]?.let { "${"%.1f".format(it.value)}°" } ?: "--°"
            val gust = v["windgustmph"]?.let { if (it.unit.contains("km")) it.value / 1.609344 else it.value }
            val kind = skyKind(v["rainratein"]?.value, gust, LocalDateTime.now().hour)
            val hi = today.high?.let { "${Math.round(it.value)}°" } ?: "--"
            val lo = today.low?.let { "${Math.round(it.value)}°" } ?: "--"
            val rain = today.rain?.let { r -> " · Rain ${"%.2f".format(r.value)} ${r.unit}" } ?: ""
            WidgetData(temp, conditionWord(kind), "High $hi · Low $lo$rain", true, "", widgetNotice(storm, current.ageSeconds))
        } catch (e: Exception) {
            WidgetData("", "", "", true, "Couldn't reach Weather Lab. Is the PC on?")
        }
    }
}

/** One short line for the widget (0.7.25): lightning close by first, then station data over 10 minutes old. */
internal fun widgetNotice(storm: Storm?, ageSeconds: Int?): String {
    if (storm != null && (storm.trend == "approaching" || storm.trend == "overhead")) {
        val where = storm.last?.let { " · ${"%.1f".format(it.distance.value)} ${it.distance.unit}" } ?: ""
        return (if (storm.trend == "overhead") "Lightning overhead" else "Lightning approaching") + where
    }
    if (ageSeconds != null && ageSeconds > 600) return "Data ${Math.round(ageSeconds / 60.0)} min old"
    return ""
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}
