package com.weatherlab.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The one-sentence briefing at the top of Live. Tap it for the full page. It stays out of the way if Weather Lab is too old or can't be reached. */
@Composable
internal fun BriefingCard(address: String, token: String, onOpen: () -> Unit) {
    var brief by remember(address, token) { mutableStateOf<Briefing?>(null) }
    LaunchedEffect(address, token) {
        brief = withContext(Dispatchers.IO) { runCatching { WeatherLabApi(address, token).briefing() }.getOrNull() }
    }
    val b = brief ?: return
    Panel(Modifier.padding(bottom = 16.dp).clickable(onClick = onOpen)) {
        Column {
            Text("Today's briefing", style = T.label, color = C.muted)
            Spacer(Modifier.height(4.dp))
            Text(b.headline, fontSize = 17.sp, fontWeight = FontWeight.W700, color = C.text)
            Spacer(Modifier.height(6.dp))
            Text("Tap for the details", style = T.label, color = C.cyan)
        }
    }
}

/** The full briefing: the headline, the numbers, and what is worth knowing. */
@Composable
fun BriefingPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "briefing", { it.briefing() }) { b ->
            Panel {
                Text(b.headline, fontSize = 19.sp, fontWeight = FontWeight.W700, color = C.text)
            }
            if (!b.forecast) {
                Note("No forecast available, so the expected high is today's highest reading so far.", C.warn)
            }
            val shown = b.tiles.filter { it.value != null }
            if (shown.isNotEmpty()) {
                Section("Today")
                TileGrid(shown.map { t ->
                    val item: @Composable (Modifier) -> Unit = { m -> Tile(t.label, t.value.orEmpty(), m, sub = t.note) }
                    item
                })
            }
            if (b.notes.isNotEmpty()) {
                Section("Worth knowing")
                ListPanel(b.notes.map { line ->
                    val row: @Composable () -> Unit = { Text(line, style = T.body, color = C.text, modifier = Modifier.padding(vertical = 9.dp)) }
                    row
                })
            }
        }
    }
}
