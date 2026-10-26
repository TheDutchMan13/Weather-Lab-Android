package com.weatherlab.android

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The last answers from Weather Lab, kept on the phone so Live still shows something when the PC can't be reached. */
internal object SnapshotCache {
    private fun file(context: Context) = File(context.filesDir, "last_snapshot.json")

    fun save(context: Context, raw: Map<String, String>) {
        val body = JSONObject()
        raw.forEach { (path, text) -> body.put(path, text) }
        file(context).writeText(JSONObject().put("saved_at", System.currentTimeMillis()).put("raw", body).toString())
    }

    /** The saved snapshot and when it was saved (milliseconds), or null when nothing usable is saved. */
    fun load(context: Context): Pair<Snapshot, Long>? = runCatching {
        val f = file(context)
        if (!f.exists()) return null
        val j = JSONObject(f.readText())
        val rawJson = j.getJSONObject("raw")
        val raw = rawJson.keys().asSequence().associateWith { rawJson.getString(it) }
        WeatherLabApi("saved", "saved", raw).snapshot() to j.getLong("saved_at")
    }.getOrNull()

    fun saveBriefing(context: Context, headline: String) {
        runCatching { File(context.filesDir, "last_briefing.txt").writeText(headline) }
    }

    /** The last briefing headline and when it was saved (milliseconds), or null. */
    fun loadBriefing(context: Context): Pair<String, Long>? = runCatching {
        val f = File(context.filesDir, "last_briefing.txt")
        if (!f.exists()) null else f.readText() to f.lastModified()
    }.getOrNull()
}

private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

internal fun clockText(millis: Long): String = clock.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** "42 min ago", "3 h ago": how long ago the reading was taken, counting the time since the phone last heard from the PC. */
internal fun readingAge(snapshot: Snapshot, fetchedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Pair<Long, String> {
    val taken = fetchedAtMillis - (snapshot.current.ageSeconds ?: 0) * 1000L
    val seconds = ((nowMillis - taken) / 1000).coerceAtLeast(0)
    val text = when {
        seconds < 90 -> "just now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86400 -> "${seconds / 3600} h ${(seconds % 3600) / 60} min ago"
        else -> "${seconds / 86400} d ago"
    }
    return taken to text
}

/** Shown at the top of Live while the PC can't be reached: the figures below are the last ones received. */
@Composable
internal fun OfflineBanner(snapshot: Snapshot, fetchedAtMillis: Long, onRetry: () -> Unit) {
    val (taken, age) = readingAge(snapshot, fetchedAtMillis)
    Panel(Modifier.padding(bottom = 16.dp), border = C.warn) {
        Column {
            Text("Offline. Showing the last reading.", fontSize = 16.sp, fontWeight = FontWeight.W700, color = C.warn)
            Spacer(Modifier.height(4.dp))
            Text(
                "Weather Lab on your PC can't be reached. Last reading $age, at ${clockText(taken)}.",
                style = T.body, color = C.text,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg)) { Text("Try again") }
        }
    }
}
