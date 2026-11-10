package com.weatherlab.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

// Pages the PC had and the phone didn't (0.7.34): Fire weather, Ask, Compare stations and the Wind map. Each reads one
// new endpoint of Weather Lab 0.37.60; an older Weather Lab shows the usual "needs a newer Weather Lab" card.

@Composable
fun FirePage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "fire", { it.fire() }) { f ->
            if (!f.available) {
                EmptyCard(f.reason.ifBlank { "Fire weather appears once the station has sent readings for an hour." })
                return@Fetch
            }
            Section("Fire danger now")
            val first = f.parts.firstOrNull()
            TileGrid(
                tiles(
                    *(listOfNotNull(first?.let { Triple(it.label, "%.1f".format(it.value), "as of ${f.asOf}") }) +
                        f.parts.drop(1).map { Triple(it.label, "%.1f".format(it.value), it.note.ifBlank { null }) } +
                        listOfNotNull(f.daysSinceRain?.let { Triple("Days since rain", it.toString(), null) })).toTypedArray(),
                ),
            )
            Section("Forecast")
            if (f.forecast.isEmpty()) {
                EmptyCard("No forecast yet. It comes from the National Weather Service, so it covers the United States only.")
            } else {
                ListPanel(f.forecast.map { d -> @Composable { InfoRow(d.day, "%.1f".format(d.fwi), sub = d.note) } })
            }
            Text(f.notes, style = T.label, color = C.muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskPage(address: String, token: String) {
    var typed by rememberSaveable { mutableStateOf("") }
    var asked by rememberSaveable { mutableStateOf("") }
    val examples = listOf("warmest day", "coldest night", "wettest day", "wettest month", "driest month", "hottest month")
    PageColumn {
        Section("Ask about your history")
        OutlinedTextField(
            value = typed, onValueChange = { typed = it.take(200) }, singleLine = true,
            label = { Text("Your question") }, placeholder = { Text("for example: rain in March 2026") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { asked = typed.trim() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = C.text, unfocusedTextColor = C.text, focusedBorderColor = C.cyan,
                unfocusedBorderColor = C.line, focusedLabelColor = C.cyan, unfocusedLabelColor = C.muted, cursorColor = C.cyan,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { asked = typed.trim() }, enabled = typed.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
        ) { Text("Ask") }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            examples.forEach { e ->
                AssistChip(
                    onClick = { typed = e; asked = e }, label = { Text(e) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = C.text),
                )
            }
        }
        if (asked.isNotBlank()) {
            Section("Answer")
            Fetch(address, token, "ask:$asked", { it.ask(asked) }) { a ->
                Panel { Text(a.answer.ifBlank { "No answer for that." }, style = T.body, color = C.text) }
            }
        }
    }
}

@Composable
fun CompareStationsPage(address: String, token: String) {
    var a by rememberSaveable { mutableStateOf<String?>(null) }
    var b by rememberSaveable { mutableStateOf<String?>(null) }
    PageColumn {
        Fetch(address, token, "stations", { it.stations() }) { list ->
            if (list.size < 2) {
                EmptyCard("Add a second station in Weather Lab on your PC (Stations, under Station) to compare two.")
                return@Fetch
            }
            val first = a?.takeIf { id -> list.any { it.id == id } } ?: list[0].id
            val second = b?.takeIf { id -> id != first && list.any { it.id == id } } ?: list.first { it.id != first }.id
            Section("Station A")
            Chips(list.map { it.name }, list.indexOfFirst { it.id == first }) { a = list[it].id }
            Section("Station B")
            val others = list.filter { it.id != first }
            Chips(others.map { it.name }, others.indexOfFirst { it.id == second }) { b = others[it].id }
            Fetch(address, token, "compare:$first:$second", { it.compareStations(first, second) }) { c ->
                Spacer(Modifier.height(10.dp))
                Panel {
                    Column {
                        Text(c.headline, style = T.heading, color = C.text)
                        Text("${c.a.name}: last reading ${c.a.age}. ${c.b.name}: last reading ${c.b.age}.", style = T.label, color = C.muted)
                    }
                }
                Section("Side by side")
                ListPanel(c.rows.map { r -> @Composable { InfoRow(r.label, "${r.a}  ·  ${r.b}", sub = "Difference (A − B): ${r.diff}") } })
                if (c.a.temps.size >= 2 || c.b.temps.size >= 2) {
                    Section("Temperature, last 24 hours")
                    TwoLines(c, Modifier.fillMaxWidth().height(160.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Legend(C.cyan, c.a.name)
                        Legend(C.warn, c.b.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(width = 16.dp, height = 3.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(name, style = T.label, color = C.muted)
    }
}

@Composable
private fun TwoLines(c: StationCompare, modifier: Modifier) {
    val ca = C.cyan
    val cb = C.warn
    val grid = C.line
    val all = c.a.temps + c.b.temps
    val t0 = all.minOf { it.first }
    val t1 = all.maxOf { it.first }.coerceAtLeast(t0 + 1)
    val lo = all.minOf { it.second }
    val hi = all.maxOf { it.second }.let { if (it - lo < 1) lo + 1 else it }
    val said = "Temperature over the last 24 hours. ${c.a.name} in blue, ${c.b.name} in amber. Range %.0f to %.0f ${c.tempUnit}.".format(lo, hi)
    Canvas(modifier.semantics { contentDescription = said }) {
        fun x(t: Long) = ((t - t0).toFloat() / (t1 - t0)) * size.width
        fun y(v: Double) = (size.height - 8f) - ((v - lo) / (hi - lo)).toFloat() * (size.height - 16f)
        for (k in 0..3) drawLine(grid, Offset(0f, size.height * k / 3f), Offset(size.width, size.height * k / 3f), strokeWidth = 1f)
        for ((pts, col) in listOf(c.a.temps to ca, c.b.temps to cb)) {
            if (pts.size < 2) continue
            val path = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.first), y(p.second)) else lineTo(x(p.first), y(p.second)) } }
            drawPath(path, col, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

@Composable
fun WindMapPage(address: String, token: String) {
    var pick by remember { mutableIntStateOf(0) }
    val hours = listOf(0, 3, 6, 12)
    PageColumn {
        Chips(listOf("Now", "+3 h", "+6 h", "+12 h"), pick) { pick = it }
        Fetch(address, token, "windmap:${hours[pick]}", { it.windMap(hours[pick]) }) { w ->
            if (!w.available) {
                EmptyCard(w.reason.ifBlank { "The wind map isn't available right now." })
                return@Fetch
            }
            Spacer(Modifier.height(10.dp))
            WindGrid(w)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed (${w.unit})", style = T.label, color = C.muted)
                w.bands.forEach { band ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.size(width = 14.dp, height = 9.dp).clip(RoundedCornerShape(2.dp)).background(hex(band.color)))
                        Spacer(Modifier.width(3.dp))
                        Text(band.label, style = T.label, color = C.muted)
                    }
                }
            }
            Section("Your station")
            Panel {
                Column {
                    Text(
                        w.stationSpeed?.let { "%.0f %s".format(it, w.unit) + (w.stationFrom?.let { f -> " from the $f" } ?: "") }
                            ?: "No wind reading from the station yet.",
                        style = T.heading, color = C.text,
                    )
                    val extra = listOfNotNull(
                        w.stationGust?.let { "gusts %.0f %s".format(it, w.unit) },
                        w.strongest?.let { "strongest in the area %.0f %s".format(it, w.unit) },
                    )
                    if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = T.label, color = C.muted)
                }
            }
            Text(
                "Each arrow points the way the wind blows. Colour shows the speed. The middle point is your station. Data from Open-Meteo.",
                style = T.label, color = C.muted, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun hex(s: String): Color = runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrDefault(Color.Gray)

@Composable
private fun WindGrid(w: WindMap) {
    val colors = w.bands.map { hex(it.color) }
    val said = "Wind map: arrows show where the wind goes, colour shows the speed." +
        (w.strongest?.let { " Strongest in the area %.0f ${w.unit}.".format(it) } ?: "")
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(w.cols.toFloat() / w.rows)
            .semantics { contentDescription = said },
    ) {
        drawRoundRect(Color(0xFFDFE7EF), cornerRadius = CornerRadius(14.dp.toPx()))
        val cw = size.width / w.cols
        val ch = size.height / w.rows
        val r = minOf(cw, ch) * 0.34f
        w.points.forEachIndexed { i, p ->
            if (p.toward == null) return@forEachIndexed
            val cx = cw * (i % w.cols + 0.5f)
            val cy = ch * (i / w.cols + 0.5f)
            rotate(p.toward.toFloat(), pivot = Offset(cx, cy)) {
                val arrow = Path().apply {
                    moveTo(cx, cy - r); lineTo(cx + r * 0.58f, cy + r * 0.83f); lineTo(cx, cy + r * 0.5f)
                    lineTo(cx - r * 0.58f, cy + r * 0.83f); close()
                }
                drawPath(arrow, colors.getOrElse(p.band) { Color.Gray })
                drawPath(arrow, Color(0xFF1B4F7A), style = Stroke(width = 0.8.dp.toPx()))
            }
        }
        val mid = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = mid)
        drawCircle(Color(0xFF1C2330), radius = 6.dp.toPx(), center = mid)
    }
}
