package com.weatherlab.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// Astronomy (0.7.31): the sun's path, daylight, golden hour and twilight, and the moon. Everything is worked out on the
// PC from the station's location (/api/v1/astronomy), so the phone and the PC always show the same times.

private val SunGold = Color(0xFFEF9F27)
private val SunDot = Color(0xFFFAC775)
private val SunEdge = Color(0xFFBA7517)
private val MoonLit = Color(0xFFB4B2A9)

@Composable
fun AstronomyPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "astronomy", { it.astronomy() }) { a ->
            val moon = a.moon
            if (!a.hasLocation || moon == null) {
                EmptyCard("Set the station's location in Weather Lab's Settings, under Station, to see sunrise, sunset and the moon.")
                return@Fetch
            }
            Section("Sun today")
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SunArc(a.sunProgress, a.sunrise ?: "--", a.sunset ?: "--")
                    Text(a.dayLength + " of daylight", style = T.value, color = C.text)
                    if (a.dayChange.isNotBlank()) Text(a.dayChange.replaceFirstChar { it.uppercase() }, style = T.label, color = C.muted)
                }
            }
            Spacer(Modifier.height(10.dp))
            TileGrid(
                tiles(
                    Triple("Solar noon", a.solarNoon ?: "--", "Sun is highest"),
                    Triple("Sun's height", "${a.noonAltitude}° up", "At solar noon"),
                    Triple("Golden hour", a.goldenHour ?: "--", "Evening light starts"),
                    Triple("Twilight ends", a.twilightEnd ?: "--", "Properly dark after"),
                ),
            )

            Section("Moon")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoonDisc(moon.illumination, moon.waxing, Modifier.size(64.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(moon.name, style = T.title, color = C.text)
                        Text("${moon.illumination.toInt()}% lit · day ${moon.ageDays.toInt()} of 29", style = T.label, color = C.muted)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TileGrid(
                tiles(
                    Triple("Moonrise", moon.rise ?: "None today", null),
                    Triple("Moonset", moon.set ?: "None today", null),
                    Triple("Next new moon", moon.nextNew, null),
                    Triple("Next full moon", moon.nextFull, null),
                ),
            )

            Section("Next 7 days")
            ListPanel(
                a.days.take(7).mapIndexed { i, d ->
                    val row: @Composable () -> Unit = {
                        InfoRow(
                            if (i == 0) "Today" else d.label, d.dayLength,
                            sub = "Rise ${d.sunrise ?: "--"} · Set ${d.sunset ?: "--"} · ${d.moon}",
                        )
                    }
                    row
                },
            )
            Text(
                "Sun times are accurate to about a minute. Moonrise and moonset are estimates, good to a few minutes. " +
                    "Moon phase dates can be off by about a day.",
                style = T.label, color = C.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** The sun's path from sunrise to sunset as a dashed arch, with the part already travelled filled in. Null at night. */
@Composable
private fun SunArc(progress: Double?, rise: String, set: String) {
    val muted = C.muted
    val say = (if (progress != null) "The sun is up. " else "The sun is down. ") + "Sunrise $rise, sunset $set."
    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .semantics { contentDescription = say },
        ) {
            val w = size.width
            val h = size.height
            val left = Offset(w * 0.06f, h * 0.86f)
            val right = Offset(w * 0.94f, h * 0.86f)
            val top = Offset(w / 2f, h * -0.30f) // the curve's control point, well above the arch
            drawLine(muted.copy(alpha = 0.3f), Offset(w * 0.02f, left.y), Offset(w * 0.98f, left.y), strokeWidth = 1.dp.toPx())
            val arch = Path().apply { moveTo(left.x, left.y); quadraticTo(top.x, top.y, right.x, right.y) }
            drawPath(arch, muted.copy(alpha = 0.45f), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
            if (progress != null) {
                val t = progress.toFloat().coerceIn(0f, 1f)
                fun point(at: Float) = Offset(
                    (1 - at) * (1 - at) * left.x + 2 * (1 - at) * at * top.x + at * at * right.x,
                    (1 - at) * (1 - at) * left.y + 2 * (1 - at) * at * top.y + at * at * right.y,
                )
                val here = point(t)
                val control = Offset((1 - t) * left.x + t * top.x, (1 - t) * left.y + t * top.y)
                val done = Path().apply { moveTo(left.x, left.y); quadraticTo(control.x, control.y, here.x, here.y) }
                drawPath(done, SunGold, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(SunDot, radius = 9.dp.toPx(), center = here)
                drawCircle(SunEdge, radius = 9.dp.toPx(), center = here, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(rise, style = T.label, color = C.muted)
            Text(set, style = T.label, color = C.muted)
        }
    }
}

/** The moon as seen from the northern hemisphere: the lit side is on the right while it grows. */
@Composable
private fun MoonDisc(illumination: Double, waxing: Boolean, modifier: Modifier = Modifier) {
    val edge = C.muted
    val dark = C.line
    val say = "Moon, ${illumination.toInt()} percent lit, " + if (waxing) "growing" else "shrinking"
    Canvas(modifier.semantics { contentDescription = say }) {
        val r = size.minDimension / 2f * 0.9f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(dark.copy(alpha = 0.5f), radius = r, center = c)
        drawLitPart(c, r, (illumination / 100.0).coerceIn(0.0, 1.0), waxing)
        drawCircle(edge.copy(alpha = 0.6f), radius = r, center = c, style = Stroke(width = 1.dp.toPx()))
    }
}

private fun DrawScope.drawLitPart(c: Offset, r: Float, lit: Double, waxing: Boolean) {
    if (lit <= 0.005) return
    if (lit >= 0.995) {
        drawCircle(MoonLit, radius = r, center = c)
        return
    }
    // The lit half circle, then the terminator (the line between light and dark) as half an ellipse back to the top.
    val rx = (abs(1.0 - 2.0 * lit) * r).toFloat()
    val crescent = lit < 0.5
    val outer = Rect(c.x - r, c.y - r, c.x + r, c.y + r)
    val inner = Rect(c.x - rx, c.y - r, c.x + rx, c.y + r)
    val path = Path().apply {
        arcTo(outer, -90f, if (waxing) 180f else -180f, true)
        // a crescent's inner curve bulges toward the lit side, a gibbous toward the dark side
        val bulgeRight = (waxing && crescent) || (!waxing && !crescent)
        arcTo(inner, 90f, if (bulgeRight) -180f else 180f, false)
        close()
    }
    drawPath(path, MoonLit)
}
