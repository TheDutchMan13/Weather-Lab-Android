package com.weatherlab.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxWidth()) {
                    WeatherLabScreen(prefs)
                }
            }
        }
    }
}

/** Labels for the readings the app shows, in the order they appear. Keys match the API's names. */
private val READING_LABELS = linkedMapOf(
    "tempf" to "Temperature",
    "feelslikef" to "Feels like",
    "dewpointf" to "Dew point",
    "humidity" to "Humidity",
    "windspeedmph" to "Wind",
    "windgustmph" to "Gust",
    "baromrelin" to "Pressure",
    "rainratein" to "Rain rate",
    "dailyrainin" to "Rain today",
    "uv" to "UV index",
)

private val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

@Composable
fun WeatherLabScreen(prefs: Prefs) {
    var address by remember { mutableStateOf(prefs.address) }
    var token by remember { mutableStateOf(prefs.token) }
    var editing by remember { mutableStateOf(prefs.address.isBlank() || prefs.token.isBlank()) }
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reloadCount by remember { mutableIntStateOf(0) }

    // Loads on entry, when the settings change, when Refresh is tapped, and then once a minute.
    LaunchedEffect(address, token, reloadCount) {
        while (address.isNotBlank() && token.isNotBlank()) {
            loading = true
            try {
                snapshot = withContext(Dispatchers.IO) { WeatherLabApi(address, token).snapshot() }
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Couldn't reach Weather Lab."
            }
            loading = false
            delay(60_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Weather Lab", style = MaterialTheme.typography.headlineMedium)

        if (editing) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connect to Weather Lab", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The address is shown in Weather Lab's Settings under Android app, for example 192.168.1.50:8600. " +
                            "The token is the one you copied there.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            prefs.address = address
                            prefs.token = token
                            editing = false
                            reloadCount++
                        },
                        enabled = address.isNotBlank() && token.isNotBlank(),
                    ) { Text("Save and connect") }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { reloadCount++ }, enabled = !loading) {
                    Text(if (loading) "Refreshing…" else "Refresh")
                }
                OutlinedButton(onClick = { editing = true }) { Text("Settings") }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        snapshot?.let { s -> Dashboard(s) }
    }
}

@Composable
private fun Dashboard(s: Snapshot) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.status.station.ifBlank { "Your station" }, style = MaterialTheme.typography.titleMedium)
            val age = s.current.ageSeconds ?: s.status.ageSeconds
            Text(if (age == null) "No readings yet." else "Last reading ${minutesAgo(age)}.")
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Now", style = MaterialTheme.typography.titleMedium)
            READING_LABELS.forEach { (key, label) ->
                val m = s.current.values[key] ?: return@forEach
                val unit = if (key == "humidity") "%" else m.unit
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label)
                    Text("${format(m.value)} $unit".trim())
                }
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("High")
                Text(s.today.high?.let { "${format(it.value)} ${it.unit}" } ?: "--")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Low")
                Text(s.today.low?.let { "${format(it.value)} ${it.unit}" } ?: "--")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rain")
                Text(s.today.rain?.let { "${format(it.value)} ${it.unit}" } ?: "--")
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent alerts", style = MaterialTheme.typography.titleMedium)
            if (s.alerts.isEmpty()) {
                Text("None yet.")
            }
            s.alerts.forEach { alert ->
                Column {
                    Text(alert.text)
                    Text(
                        Instant.ofEpochSecond(alert.ts).atZone(ZoneId.systemDefault()).format(WHEN),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun format(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else "%.1f".format(value)

private fun minutesAgo(seconds: Int): String = when {
    seconds < 90 -> "just now"
    seconds < 3600 -> "${seconds / 60} min ago"
    else -> "${seconds / 3600} h ago"
}
