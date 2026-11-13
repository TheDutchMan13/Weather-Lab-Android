package com.weatherlab.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import kotlin.math.sin

// First-run welcome (0.7.35), the same Daybreak idea as Weather Lab on the PC: a sky that matches the time of day.

enum class DayPeriod(val greeting: String) {
    MORNING("Good morning."), AFTERNOON("Good afternoon."), EVENING("Good evening."), NIGHT("Hello there."),
}

/** Same hours as the PC app: morning 5-11, afternoon 12-16, evening 17-20, otherwise night. */
fun dayPeriod(hour: Int): DayPeriod = when (hour) {
    in 5..11 -> DayPeriod.MORNING
    in 12..16 -> DayPeriod.AFTERNOON
    in 17..20 -> DayPeriod.EVENING
    else -> DayPeriod.NIGHT
}

private class Sky(val stops: List<Pair<Float, Long>>, val hills: List<Long>, val sun: Long, val sunY: Float, val stars: Int, val moon: Boolean)

private fun sky(p: DayPeriod) = when (p) {
    DayPeriod.MORNING -> Sky(
        listOf(0f to 0x26346F, .4f to 0x4A5CA3, .7f to 0xE9A087, .88f to 0xF6D199, 1f to 0xFBE6B4),
        listOf(0x6C5B86, 0x40386A, 0x25214A), 0xFFE08A, .66f, 0, false,
    )
    DayPeriod.AFTERNOON -> Sky(
        listOf(0f to 0x103A86, .4f to 0x1F5AB4, .8f to 0x7DB6EC, 1f to 0xCBE6F8),
        listOf(0x3F7D6E, 0x2C5F57, 0x1A3F3B), 0xFFF1A8, .58f, 0, false,
    )
    DayPeriod.EVENING -> Sky(
        listOf(0f to 0x0F1B45, .4f to 0x3B3F86, .68f to 0xC9627E, .86f to 0xF2A878, 1f to 0xF8D9A0),
        listOf(0x5A3A6B, 0x2A1F4A, 0x150F2E), 0xFFC857, .68f, 36, false,
    )
    DayPeriod.NIGHT -> Sky(
        listOf(0f to 0x050816, .4f to 0x101A44, .8f to 0x27306B, 1f to 0x3E4A8C),
        listOf(0x1C2250, 0x121737, 0x0A0D24), 0xF3F0E0, .58f, 90, true,
    )
}

private fun col(v: Long) = Color(v or 0xFF000000L)

@Composable
fun WelcomePage(onStart: () -> Unit, hour: Int = LocalTime.now().hour) {
    val period = dayPeriod(hour)
    val s = sky(period)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(*s.stops.map { it.first to col(it.second) }.toTypedArray()))
            for (i in 0 until s.stars) {
                val x = (i * 211 % 1000) / 1000f * w
                val y = ((i * 97) % 420) / 1000f * h + 12f
                drawCircle(Color.White.copy(alpha = .35f + (i % 4) * .13f), radius = 1.5f + (i % 3) * .9f, center = Offset(x, y))
            }
            val c = col(s.sun)
            val centre = Offset(w * .72f, h * s.sunY)
            val r = w * .09f
            drawCircle(c.copy(alpha = .10f), r * 2.8f, centre)
            drawCircle(c.copy(alpha = .18f), r * 1.8f, centre)
            drawCircle(c, r, centre)
            s.hills.forEachIndexed { i, hex ->
                val base = h * (.78f + i * .07f)
                val path = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, base)
                    var x = 0f
                    while (x <= w) {
                        lineTo(x, base - sin((x / w) * 6.28f + i * 1.7f) * h * .025f - i * 4f)
                        x += 12f
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(path, col(hex))
            }
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.height(72.dp))
            Text("WEATHER LAB", style = TextStyle(fontFamily = Manrope, fontSize = 12.sp, fontWeight = FontWeight.W800, letterSpacing = 3.sp), color = Color(0xFFFFE7C2))
            Spacer(Modifier.height(10.dp))
            Text(period.greeting, style = TextStyle(fontFamily = Manrope, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.W800), color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                "Your own weather station, on your phone. This app connects to Weather Lab on your PC.",
                style = TextStyle(fontFamily = Manrope, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.W600), color = Color(0xFFEDEAFB),
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1A1B3A)),
            ) { Text("Connect to my PC", style = TextStyle(fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.W800)) }
            Spacer(Modifier.height(14.dp))
            Text(
                "You'll need the address and token from Weather Lab's Settings on the PC, or a setup code. Keep the phone on the same network as the PC.",
                style = TextStyle(fontFamily = Manrope, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.W600),
                color = Color(0xFFF4EEE8),
            )
        }
    }
}
