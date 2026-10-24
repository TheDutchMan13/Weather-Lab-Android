package com.weatherlab.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Pages built from public sources and the PC's settings: Airport, Tropics, Radar, Air and Smoke, Phone Alerts.
// They need Weather Lab 0.37 or newer.

private val OBSERVED: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

private fun observed(iso: String): String = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(OBSERVED)
} catch (_: Exception) {
    iso
}

private fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

internal fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun Unavailable(reason: String) {
    EmptyCard(reason.ifBlank { "This page isn't available for your station." })
}

// ---------------------------------------------------------------------------------------------------------------------
// Airport

@Composable
fun AirportPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "airport", { it.airport() }) { a ->
            if (!a.available) {
                Unavailable(a.reason)
            } else {
                val kind = if (a.isAirport) "airport" else "weather station"
                Section("Nearest $kind")
                Panel {
                    Column {
                        Text(a.name, style = T.heading, color = C.text)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${withUnit(a.distance, a.distanceUnit, 1)} away" +
                                if (a.time.isNotBlank()) ", observed at ${observed(a.time)}" else "",
                            style = T.body, color = C.muted,
                        )
                        if (a.text.isNotBlank()) Text(a.text, style = T.body, color = C.muted)
                    }
                }
                Section("Your station and the $kind")
                ListPanel(
                    a.rows.map { r ->
                        val diff = if (r.station != null && r.airport != null) r.station - r.airport else null
                        val line: @Composable () -> Unit = {
                            InfoRow(
                                r.label, "${withUnit(r.station, r.unit)}  /  ${withUnit(r.airport, r.unit)}",
                                sub = diff?.let {
                                    when {
                                        Math.abs(it) < 0.5 -> "About the same"
                                        it > 0 -> "Yours is ${fmt(it, 0)}${spacedUnit(r.unit)} higher"
                                        else -> "Yours is ${fmt(-it, 0)}${spacedUnit(r.unit)} lower"
                                    }
                                },
                            )
                        }
                        line
                    },
                )
                Text("Left is your station, right is the $kind. Both come from the National Weather Service.", style = T.label, color = C.muted)
            }
        }
    }
}

private fun spacedUnit(unit: String): String = spaced(unit)

// ---------------------------------------------------------------------------------------------------------------------
// Tropics

@Composable
fun TropicsPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "tropics", { it.tropics() }) { t ->
            if (!t.available) {
                Unavailable(t.reason)
            } else {
                if (!t.ok) Note("Couldn't reach the National Hurricane Center just now. This is the last list Weather Lab saved.", C.warn)
                Section("Active storms")
                if (t.storms.isEmpty()) {
                    Note("No active tropical storms or hurricanes.", C.good)
                } else {
                    t.storms.forEach { s ->
                        Panel {
                            Column {
                                Text("${s.name}  ·  ${s.cls}", style = T.heading, color = C.text)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${withUnit(s.distance, t.distanceUnit)} ${s.direction} of home, winds ${withUnit(s.wind, t.windUnit)}. Moving ${s.move}.",
                                    style = T.body, color = C.muted,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (t.checked.isNotBlank()) Text("Checked ${t.checked}. Source: National Hurricane Center.", style = T.label, color = C.muted)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Radar

@Composable
private fun RadarImage(url: String) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 8000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("User-Agent", "WeatherLabAndroid")
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }
        if (loaded == null) failed = true else bitmap = loaded
    }
    val b = bitmap
    when {
        b != null -> Image(
            b.asImageBitmap(), contentDescription = "Radar picture",
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.FillWidth,
        )
        failed -> Note("Couldn't load the radar picture. Use the button below to open it in your browser.", C.warn)
        else -> LoadingCard()
    }
}

@Composable
fun RadarPage(address: String, token: String) {
    var which by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    PageColumn {
        Fetch(address, token, "radar", { it.radar() }) { r ->
            if (!r.available) {
                Unavailable(r.reason)
            } else {
                val local = r.localImage != null
                if (local) Chips(listOf("Your area", "United States"), which) { which = it }
                val showLocal = local && which == 0
                val image = if (showLocal) r.localImage.orEmpty() else r.usImage
                val page = if (showLocal) r.localPage ?: r.usPage else r.usPage
                Section(if (showLocal) "Radar${r.station?.let { " ($it)" } ?: ""}" else "Radar, United States")
                RadarImage(image)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { openLink(context, page) },
                    colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
                ) { Text("Open moving radar in browser") }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Latest picture from the National Weather Service. United States only. Pull back to the menu and return to refresh.",
                    style = T.label, color = C.muted,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Air and Smoke

@Composable
fun AirPage(address: String, token: String) {
    PageColumn {
        Fetch(address, token, "air", { it.air() }) { a ->
            if (!a.available) {
                Unavailable(a.reason)
            } else {
                val color = when {
                    a.aqi <= 50 -> C.good
                    a.aqi <= 100 -> C.warn
                    else -> C.bad
                }
                Section(if (a.area.isBlank()) "Air quality" else "Air quality, ${a.area}")
                Panel(border = color) {
                    Column {
                        Text("${a.aqi}", fontSize = 40.sp, fontWeight = FontWeight.W800, color = color)
                        Text(a.category, style = T.heading, color = color)
                    }
                }
                if (a.pollutants.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    TileGrid(tiles(*a.pollutants.map { Triple(it.label, it.aqi.toString(), null as String?) }.toTypedArray()))
                }
                Text("Air Quality Index from AirNow. Smoke shows up as PM2.5.", style = T.label, color = C.muted)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Phone Alerts

@Composable
fun PushPage(address: String, token: String) {
    val context = LocalContext.current
    PageColumn {
        Fetch(address, token, "push", { it.pushInfo() }) { p ->
            if (p.topic.isBlank()) {
                EmptyCard("Phone alerts aren't set up yet. Turn them on in Weather Lab on your PC, then come back here.")
            } else {
                Section("Alerts to this phone")
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Weather Lab on your PC sends its alerts through ntfy. Install the free ntfy app, " +
                                "then subscribe to this topic on this server to get the same alerts on this phone, even when this app is closed.",
                            style = T.body, color = C.muted,
                        )
                        InfoRow("Server", p.server)
                        InfoRow("Topic", p.topic)
                        Button(
                            onClick = { openNtfy(context, p.server, p.topic) },
                            colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
                        ) { Text("Open in ntfy") }
                        Button(
                            onClick = { copyText(context, "ntfy topic", p.topic) },
                            colors = ButtonDefaults.buttonColors(containerColor = C.card, contentColor = C.text),
                        ) { Text("Copy topic") }
                    }
                }
                Text("Treat the topic like a password: anyone who knows it can read your alerts.", style = T.label, color = C.muted)
            }
        }
    }
}

private fun openNtfy(context: Context, server: String, topic: String) {
    val host = server.substringAfter("://").trimEnd('/')
    val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("ntfy://$host/$topic"))
    try {
        context.startActivity(deepLink)
    } catch (_: Exception) {
        openLink(context, "${server.trimEnd('/')}/$topic")
    }
}
