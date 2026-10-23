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
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                if (!data.configured || data.message.isNotEmpty()) {
                    Text("Weather Lab", style = TextStyle(color = text, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    Text(data.message, style = TextStyle(color = muted, fontSize = 12.sp))
                } else {
                    Text(data.temp, style = TextStyle(color = text, fontSize = 34.sp, fontWeight = FontWeight.Bold))
                    Text(data.line, style = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                    Text(data.hiLo, style = TextStyle(color = muted, fontSize = 13.sp))
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
            val v = current.values
            val temp = v["tempf"]?.let { "${"%.1f".format(it.value)}°" } ?: "--°"
            val gust = v["windgustmph"]?.let { if (it.unit.contains("km")) it.value / 1.609344 else it.value }
            val kind = skyKind(v["rainratein"]?.value, gust, LocalDateTime.now().hour)
            val hi = today.high?.let { "${Math.round(it.value)}°" } ?: "--"
            val lo = today.low?.let { "${Math.round(it.value)}°" } ?: "--"
            WidgetData(temp, conditionWord(kind), "High $hi · Low $lo", true, "")
        } catch (e: Exception) {
            WidgetData("", "", "", true, "Couldn't reach Weather Lab. Is the PC on?")
        }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}
