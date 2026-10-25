package com.weatherlab.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Ported from the AZ Weather Lab phone app's TrendPainter (widgets.dart) and its "Last 7 days" strip (now_page.dart).

/** A single-line chart: quiet line, the high and low labelled, and a dot on the newest value. */
@Composable
fun TrendChart(values: List<Double>, unit: String = "°", height: Dp = 120.dp) {
    val measurer = rememberTextMeasurer()
    val line = C.quiet
    val labelColor = C.muted
    val dot = C.text
    val description = if (values.size < 2) "Chart with no data" else
        "Chart of ${values.size} readings. Lowest ${values.min().roundToInt()}$unit, " +
            "highest ${values.max().roundToInt()}$unit, latest ${values.last().roundToInt()}$unit."
    Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max()
        val span = if (hi - lo < 1) 1.0 else hi - lo
        val padT = 20.dp.toPx()
        val padB = 10.dp.toPx()
        val h = size.height - padT - padB
        val n = values.size
        fun xAt(i: Int) = i * size.width / (n - 1)
        fun yAt(i: Int) = (padT + h * (1 - (values[i] - lo) / span)).toFloat()

        val path = Path()
        for (i in 0 until n) if (i == 0) path.moveTo(xAt(i), yAt(i)) else path.lineTo(xAt(i), yAt(i))
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val style = TextStyle(color = labelColor, fontSize = 11.sp)
        fun label(text: String, i: Int, above: Boolean) {
            val m = measurer.measure(text, style)
            val x = max(0f, min(xAt(i) - m.size.width / 2f, size.width - m.size.width))
            val y = if (above) yAt(i) - 17.dp.toPx() else yAt(i) + 4.dp.toPx()
            drawText(m, topLeft = Offset(x, y))
        }
        val iHi = values.indexOf(hi)
        val iLo = values.indexOf(lo)
        label("${hi.roundToInt()}$unit", iHi, true)
        if (iLo != iHi) label("${lo.roundToInt()}$unit", iLo, false)
        drawCircle(dot, radius = 4.dp.toPx(), center = Offset(xAt(n - 1), yAt(n - 1)))
    }
}

/**
 * Today's air temperature and heat index on one scale, with dotted lines where the Extreme caution and Danger levels start.
 * Lines at levels outside the range of the readings are left out.
 */
@Composable
fun HeatChart(temps: List<Double>, heatIndex: List<Double>, marks: List<Pair<Double, String>>, unit: String, height: Dp = 170.dp) {
    val measurer = rememberTextMeasurer()
    val tempColor = C.bad
    val indexColor = C.cyan
    val gridColor = C.line
    val labelColor = C.muted
    val markColor = C.warn
    val description = if (heatIndex.size < 2) "Heat chart with no data" else
        "Heat index today: lowest ${heatIndex.min().roundToInt()}$unit, highest ${heatIndex.max().roundToInt()}$unit, " +
            "latest ${heatIndex.last().roundToInt()}$unit."
    Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (heatIndex.size < 2) return@Canvas
        val all = temps + heatIndex
        var lo = all.min()
        var hi = all.max()
        marks.forEach { (v, _) -> if (v in (lo - 15)..(hi + 15)) { lo = min(lo, v); hi = max(hi, v) } }
        val span = if (hi - lo < 1) 1.0 else hi - lo
        val padT = 12.dp.toPx()
        val padB = 12.dp.toPx()
        val h = size.height - padT - padB
        fun yAt(v: Double) = (padT + h * (1 - (v - lo) / span)).toFloat()
        val style = TextStyle(color = labelColor, fontSize = 11.sp)
        marks.forEach { (v, name) ->
            if (v < lo || v > hi) return@forEach
            val y = yAt(v)
            drawLine(markColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())))
            val m = measurer.measure("$name ${v.roundToInt()}$unit", style)
            drawText(m, topLeft = Offset(size.width - m.size.width, y - m.size.height - 1.dp.toPx()))
        }
        drawLine(gridColor, Offset(0f, size.height - padB), Offset(size.width, size.height - padB), strokeWidth = 1.dp.toPx())
        fun line(values: List<Double>, color: Color) {
            val n = values.size
            if (n < 2) return
            val path = Path()
            for (i in 0 until n) {
                val x = i * size.width / (n - 1)
                if (i == 0) path.moveTo(x, yAt(values[i])) else path.lineTo(x, yAt(values[i]))
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        line(temps, tempColor)
        line(heatIndex, indexColor)
    }
}

/** Seven columns: weekday, high, low and rain (rain only when it is 0.01 or more). */
@Composable
fun WeekStrip(week: Week) {
    Row(Modifier.fillMaxWidth()) {
        week.days.forEach { d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text12(weekdayName(d.date), C.muted)
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    d.high?.let { "${it.roundToInt()}°" } ?: "--",
                    fontWeight = FontWeight.W800, color = C.text, textAlign = TextAlign.Center,
                )
                androidx.compose.material3.Text(
                    d.low?.let { "${it.roundToInt()}°" } ?: "--",
                    fontSize = 13.sp, color = C.muted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                val rain = d.rain
                androidx.compose.material3.Text(
                    if (rain == null || rain < 0.01) "" else rainText(rain, week.rainUnit),
                    fontSize = 11.sp, color = C.muted, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Text12(text: String, color: androidx.compose.ui.graphics.Color) =
    androidx.compose.material3.Text(text, fontSize = 12.sp, color = color)

private fun weekdayName(date: String): String = try {
    LocalDate.parse(date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US)
} catch (_: Exception) {
    ""
}

private fun rainText(v: Double, unit: String): String = if (unit.contains("mm")) "%.1f".format(v) else "%.2f".format(v)
