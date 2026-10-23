package com.weatherlab.android

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Ported from the AZ Weather Lab phone app's weather_icon.dart (read only reference).
// Animated sky icons drawn on a 100 x 100 canvas, no images. Motion stops when the phone's "remove animations" setting is on.

/** Sky kinds: clear_day, cloudy_day, cloud, rain, storm, snow, fog, wind, night, partly_night, dawn, dusk. */
@Composable
fun WxIcon(kind: String, size: Dp = 84.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animationsOff = remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }
    val t = if (animationsOff) 0f else rememberInfiniteTransition(label = "sky").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    ).value
    Canvas(modifier.size(size)) {
        scale(this.size.width / 100f, pivot = Offset.Zero) { drawSky(kind, t) }
    }
}

private val Yellow = Color(0xFFFDE047)
private val CloudMid = Color(0xFFE2E8F0)
private val CloudGrey = Color(0xFFCBD5E1)
private val CloudDark = Color(0xFF64748B)

private fun lerp(a: Float, b: Float, p: Float) = a + (b - a) * p
private fun fade(ph: Float) = sin(PI.toFloat() * ph).coerceIn(0f, 1f)

private fun DrawScope.drawSky(kind: String, t: Float) {
    val ang = (2 * PI * t).toFloat()
    val drift = 3f * sin(ang)
    when (kind) {
        "cloudy_day" -> {
            sun(40f, 40f, 13f, t, ang)
            cloud(60f + drift, 62f, 0.8f, CloudMid)
        }
        "cloud" -> cloud(50f + drift, 50f, 1.05f, CloudMid)
        "rain" -> {
            cloud(50f + drift, 40f, 0.95f, CloudGrey)
            drops(listOf(34f, 50f, 66f), Color(0xFF7DD3FC), t)
        }
        "storm" -> {
            cloud(50f + drift, 38f, 0.95f, CloudDark)
            bolt(t)
            drops(listOf(32f, 68f), Color(0xFF93C5FD), t)
        }
        "snow" -> {
            cloud(50f + drift, 40f, 0.95f, CloudMid)
            flakes(listOf(34f, 50f, 66f), t)
        }
        "fog" -> lines(listOf(44f, 58f, 72f), sway = true, t = t, ang = ang)
        "wind" -> lines(listOf(38f, 52f, 66f), sway = false, t = t, ang = ang)
        "night" -> {
            moon(48f, 50f, 24f, 62f, 40f, 19f)
            stars(t)
        }
        "partly_night" -> {
            moon(38f, 38f, 16f, 48f, 30f, 13f)
            cloud(60f + drift, 64f, 0.8f, CloudMid)
        }
        "dawn", "dusk" -> horizon(ang)
        else -> sun(50f, 50f, 18f, t, ang) // clear_day
    }
}

private fun DrawScope.sun(cx: Float, cy: Float, r: Float, t: Float, ang: Float) {
    val pulse = 1f + 0.06f * sin(ang)
    // one slow turn every 16 s of the 4 s loop
    val turnDeg = (t * 2 * PI / 4 * 180 / PI).toFloat()
    rotate(turnDeg, pivot = Offset(cx, cy)) {
        for (i in 0 until 12) {
            val a = (i * PI / 6).toFloat()
            drawLine(
                Yellow,
                Offset(cx + cos(a) * (r + 4), cy + sin(a) * (r + 4)),
                Offset(cx + cos(a) * (r + 11), cy + sin(a) * (r + 11)),
                strokeWidth = 3.2f, cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(Yellow, radius = r * pulse, center = Offset(cx, cy))
}

private fun DrawScope.cloud(x: Float, y: Float, s: Float, col: Color) {
    translate(x, y) {
        scale(s, pivot = Offset.Zero) {
            drawOval(col, Offset(-34f, -16f), Size(68f, 32f))
            drawOval(col, Offset(-16f - 17f, -10f - 15f), Size(34f, 30f))
            drawOval(col, Offset(12f - 20f, -14f - 18f), Size(40f, 36f))
        }
    }
}

private fun DrawScope.drops(xs: List<Float>, col: Color, t: Float) {
    xs.forEachIndexed { i, x ->
        val ph = (t + i / 3f) % 1f
        val y = lerp(-4f, 20f, ph)
        drawLine(col.copy(alpha = fade(ph)), Offset(x, 70f + y), Offset(x - 3f, 80f + y), strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.flakes(xs: List<Float>, t: Float) {
    xs.forEachIndexed { i, x0 ->
        val ph = (t + i / 3f) % 1f
        val y = lerp(-4f, 20f, ph)
        val x = x0 + 2f * sin((2 * PI * ph).toFloat())
        drawCircle(Color(0xFFE0F2FE).copy(alpha = fade(ph)), radius = 2.8f, center = Offset(x, 72f + y))
    }
}

private fun DrawScope.lines(ys: List<Float>, sway: Boolean, t: Float, ang: Float) {
    ys.forEachIndexed { i, y ->
        val ph = (t + i / 3f) % 1f
        val dx: Float
        val alpha: Float
        if (sway) {
            dx = 4f * sin(ang + i * 0.6f)
            alpha = 1f
        } else {
            dx = lerp(-10f, 10f, ph)
            alpha = fade(ph)
        }
        drawLine(CloudGrey.copy(alpha = alpha), Offset(18f + dx, y), Offset(78f + dx, y), strokeWidth = 4f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.bolt(t: Float) {
    if (!(t > 0.56f && t < 0.64f)) return
    val p = Path().apply {
        moveTo(54f, 56f); lineTo(44f, 72f); lineTo(52f, 72f); lineTo(46f, 88f)
        lineTo(62f, 68f); lineTo(53f, 68f); lineTo(58f, 56f); close()
    }
    drawPath(p, Yellow)
}

private fun DrawScope.moon(cx: Float, cy: Float, r: Float, hx: Float, hy: Float, hr: Float) {
    val full = Path().apply { addOval(Rect(Offset(cx - r, cy - r), Size(r * 2, r * 2))) }
    val hole = Path().apply { addOval(Rect(Offset(hx - hr, hy - hr), Size(hr * 2, hr * 2))) }
    val crescent = Path.combine(PathOperation.Difference, full, hole)
    drawPath(crescent, Color(0xFFFDE68A))
}

private fun DrawScope.stars(t: Float) {
    val pts = listOf(Offset(18f, 22f), Offset(84f, 26f), Offset(80f, 84f))
    pts.forEachIndexed { i, p ->
        val ph = (t + i * 0.4f) % 1f
        val a = 0.2f + 0.7f * (0.5f + 0.5f * sin((2 * PI * ph).toFloat()))
        drawCircle(Color.White.copy(alpha = a), radius = 1.5f, center = p)
    }
}

private fun DrawScope.horizon(ang: Float) {
    val sink = 3f * sin(ang)
    clipRect(0f, 0f, 100f, 66f) {
        drawCircle(Color(0xFFFDBA74), radius = 24f, center = Offset(50f, 66f + sink))
    }
    val orange = Color(0xFFFB923C)
    drawLine(orange, Offset(14f, 66f), Offset(86f, 66f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(orange.copy(alpha = 0.6f), Offset(24f, 78f), Offset(76f, 78f), strokeWidth = 2.5f, cap = StrokeCap.Round)
}

/** Picks a sky kind from what the station knows (the API has no forecast text): rain, wind, then day or night by the clock. */
fun skyKind(rainRate: Double?, gust: Double?, hour: Int): String {
    if (rainRate != null && rainRate > 0.0) return "rain"
    if (gust != null && gust >= 25.0) return "wind"
    return if (hour in 6..18) "clear_day" else "night"
}
