package com.weatherlab.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            var scheme by remember { mutableStateOf(Scheme.from(prefs.scheme)) }
            WeatherLabTheme(scheme) {
                // Status and navigation bar icons follow the scheme, not the phone's own dark mode.
                val view = LocalView.current
                val light = palette(scheme).light
                SideEffect {
                    val c = WindowCompat.getInsetsController(window, view)
                    c.isAppearanceLightStatusBars = light
                    c.isAppearanceLightNavigationBars = light
                }
                WeatherLabApp(prefs, scheme) {
                    scheme = it
                    prefs.scheme = it.key
                }
            }
        }
    }
}

private val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

private enum class Tab(val label: String, val icon: ImageVector, val selected: ImageVector) {
    LIVE("Live", Icons.Outlined.Cloud, Icons.Filled.Cloud),
    ALERTS("Alerts", Icons.Outlined.Bolt, Icons.Filled.Bolt),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherLabApp(prefs: Prefs, scheme: Scheme, onScheme: (Scheme) -> Unit) {
    var address by remember { mutableStateOf(prefs.address) }
    var token by remember { mutableStateOf(prefs.token) }
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reloadCount by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(if (prefs.address.isBlank() || prefs.token.isBlank()) Tab.SETTINGS else Tab.LIVE) }

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

    // The Alerts tab shows a badge when something was logged in the last 24 hours, like the Storm tab on the phone app.
    val recentAlerts = snapshot?.alerts.orEmpty().count { System.currentTimeMillis() / 1000 - it.ts < 24 * 3600 }

    Scaffold(
        containerColor = C.bg,
        topBar = {
            TopAppBar(
                title = { Text(tab.label, fontSize = 22.sp, fontWeight = FontWeight.W800) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = C.bg,
                    scrolledContainerColor = C.bg,
                    titleContentColor = C.text,
                    actionIconContentColor = C.text,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = C.card) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            tab = t
                            if (t == Tab.LIVE) reloadCount++
                        },
                        icon = {
                            val icon = if (tab == t) t.selected else t.icon
                            if (t == Tab.ALERTS && recentAlerts > 0) {
                                BadgedBox(badge = { Badge(containerColor = C.bad) { Text("Alert") } }) {
                                    Icon(icon, contentDescription = t.label)
                                }
                            } else {
                                Icon(icon, contentDescription = t.label)
                            }
                        },
                        label = { Text(t.label) },
                        colors = navItemColors(),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.LIVE -> LivePage(
                    snapshot = snapshot, error = error, loading = loading,
                    configured = address.isNotBlank() && token.isNotBlank(),
                    onRefresh = { reloadCount++ },
                    onOpenSettings = { tab = Tab.SETTINGS },
                )
                Tab.ALERTS -> AlertsPage(snapshot)
                Tab.SETTINGS -> SettingsPage(
                    prefs = prefs, address = address, token = token, scheme = scheme, snapshot = snapshot,
                    onSave = { a, t ->
                        prefs.address = a
                        prefs.token = t
                        address = prefs.address
                        token = prefs.token
                        reloadCount++
                        tab = Tab.LIVE
                    },
                    onScheme = onScheme,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Live

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LivePage(
    snapshot: Snapshot?,
    error: String?,
    loading: Boolean,
    configured: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = loading && snapshot != null, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            when {
                !configured -> {
                    EmptyCard("Add your Weather Lab address and token in Settings to see your station.")
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
                    ) { Text("Open Settings") }
                }
                snapshot == null && error != null -> ErrorBox(error, onRefresh, title = "Couldn't reach Weather Lab")
                snapshot == null -> LoadingCard()
                else -> {
                    if (error != null) Note(error, C.warn)
                    Dashboard(snapshot)
                }
            }
        }
    }
}

private val TILES = listOf(
    "humidity" to "Humidity",
    "windspeedmph" to "Wind",
    "windgustmph" to "Gust",
    "baromrelin" to "Pressure",
    "rainratein" to "Rain rate",
    "dailyrainin" to "Rain today",
    "uv" to "UV index",
)

@Composable
private fun Dashboard(s: Snapshot) {
    val v = s.current.values
    val age = s.current.ageSeconds ?: s.status.ageSeconds
    val ageMin = age?.let { it / 60 }

    // Storm banner above everything else, as on the phone app.
    s.storm?.let { st ->
        if (st.trend == "approaching" || st.trend == "overhead") {
            val away = st.last?.let { "${one(it.distance.value)} ${it.distance.unit}" }
            val text = if (st.trend == "overhead") "Lightning overhead." else "Lightning approaching."
            Note(if (away == null) text else "$text Last strike $away away.", C.bad)
        }
    }

    if (age != null && age > 600) Note("Station data is ${(age / 60.0).let { Math.round(it) }} minutes old.", C.warn)

    // Hero: big temperature beside the animated sky icon, then the lines under it.
    val temp = v["tempf"]
    val gustMph = v["windgustmph"]?.let { if (it.unit.contains("km")) it.value / 1.609344 else it.value }
    val rainRate = v["rainratein"]?.value
    val kind = skyKind(rainRate, gustMph, LocalDateTime.now().hour)
    Panel {
        Column {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(if (temp == null) "--°" else "${one(temp.value)}°", style = T.hero, color = C.text)
                Spacer(Modifier.width(12.dp))
                WxIcon(kind, size = 72.dp)
            }
            if (ageMin != null) {
                Text(
                    updatedAgo(age ?: 0), fontSize = 12.sp,
                    color = if (ageMin > 10) C.warn else C.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(conditionWord(kind), fontSize = 17.sp, fontWeight = FontWeight.W700, color = C.text)
            Spacer(Modifier.height(8.dp))
            Text("Feels like ${deg1(v["feelslikef"])} · Dew point ${deg1(v["dewpointf"])}", style = T.body, color = C.muted)
            Text("Today: high ${deg(s.today.high)} · low ${deg(s.today.low)}", style = T.body, color = C.muted)
        }
    }
    Spacer(Modifier.height(10.dp))

    Section("Right now", updatedMinutes = ageMin)
    val dayAlerts = s.alerts.count { System.currentTimeMillis() / 1000 - it.ts < 24 * 3600 }
    Text(
        if (dayAlerts == 0) "No alerts in the last 24 hours" else "$dayAlerts alert${if (dayAlerts == 1) "" else "s"} in the last 24 hours",
        fontSize = 14.sp, fontWeight = FontWeight.W700, color = C.text,
    )
    Spacer(Modifier.height(10.dp))
    TileGrid(
        TILES.mapNotNull { (key, label) ->
            val m = v[key] ?: return@mapNotNull null
            val item: @Composable (Modifier) -> Unit = { mod -> Tile(label, reading(key, m), mod) }
            item
        },
    )

    val hist = s.history
    if (hist != null && hist.points.size > 1) {
        Section("Last 24 hours", updatedMinutes = ageMin)
        Panel { TrendChart(hist.points.map { it.second }, unit = "°") }
    }
    val week = s.week
    if (week != null && week.days.isNotEmpty()) {
        Section("Last 7 days")
        Panel { WeekStrip(week) }
    }

    Section("Today")
    ListPanel(
        listOf(
            { InfoRow("High", s.today.high?.let { reading("tempf", it) } ?: "--") },
            { InfoRow("Low", s.today.low?.let { reading("tempf", it) } ?: "--") },
            { InfoRow("Rain", s.today.rain?.let { reading("dailyrainin", it) } ?: "--") },
        ),
    )

    s.storm?.let { st ->
        Section("Lightning")
        Panel {
            Column {
                Text("${st.strikesToday} strike${if (st.strikesToday == 1) "" else "s"} today", fontSize = 18.sp, fontWeight = FontWeight.W800, color = C.text)
                Spacer(Modifier.height(4.dp))
                val last = st.last
                Text(
                    if (last == null) "No strikes recorded yet." else
                        "Last: ${one(last.distance.value)} ${last.distance.unit} away, ${agoText(last.ageSeconds)}",
                    style = T.body, color = C.muted,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Alerts

@Composable
private fun AlertsPage(snapshot: Snapshot?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Section("Recent alerts")
        val alerts = snapshot?.alerts.orEmpty()
        if (alerts.isEmpty()) {
            EmptyCard("No alerts yet.")
        } else {
            ListPanel(
                alerts.map { a ->
                    val row: @Composable () -> Unit = {
                        InfoRow(
                            left = a.text, right = "",
                            sub = Instant.ofEpochSecond(a.ts).atZone(ZoneId.systemDefault()).format(WHEN),
                        )
                    }
                    row
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPage(
    prefs: Prefs,
    address: String,
    token: String,
    scheme: Scheme,
    snapshot: Snapshot?,
    onSave: (String, String) -> Unit,
    onScheme: (Scheme) -> Unit,
) {
    var a by remember { mutableStateOf(address) }
    var t by remember { mutableStateOf(token) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = C.text, unfocusedTextColor = C.text,
        focusedBorderColor = C.cyan, unfocusedBorderColor = C.line,
        focusedLabelColor = C.cyan, unfocusedLabelColor = C.muted,
        cursorColor = C.cyan,
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Section("Connection")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The address is shown in Weather Lab's Settings under Android app, for example 192.168.1.50:8600. " +
                        "The token is the one you copied there.",
                    style = T.label, color = C.muted,
                )
                OutlinedTextField(
                    value = a, onValueChange = { a = it }, label = { Text("Address") },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = t, onValueChange = { t = it }, label = { Text("Token") },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSave(a, t) },
                    enabled = a.isNotBlank() && t.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
                ) { Text("Save and connect") }
            }
        }

        Section("Appearance")
        Panel {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Scheme.entries.forEach { s ->
                    FilterChip(
                        selected = scheme == s,
                        onClick = { onScheme(s) },
                        label = { Text(s.label, color = C.text) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = C.card,
                            selectedContainerColor = C.cyan.copy(alpha = 50 / 255f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = scheme == s,
                            borderColor = C.line, selectedBorderColor = C.cyan,
                        ),
                    )
                }
            }
        }

        if (snapshot != null) {
            Section("Station")
            ListPanel(
                listOf(
                    { InfoRow("Station", snapshot.status.station.ifBlank { "--" }) },
                    { InfoRow("Weather Lab version", snapshot.status.appVersion.ifBlank { "--" }) },
                    { InfoRow("Units", if (snapshot.current.metric) "Metric" else "US") },
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Formatting

private fun deg(m: Measure?): String = if (m == null) "--°" else "${Math.round(m.value)}°"

private fun agoText(seconds: Long): String = when {
    seconds < 90 -> "just now"
    seconds < 3600 -> "${seconds / 60} min ago"
    seconds < 86400 -> "${seconds / 3600} h ago"
    else -> "${seconds / 86400} d ago"
}

private fun one(v: Double): String = "%.1f".format(v)

private fun deg1(m: Measure?): String = if (m == null) "--°" else "${one(m.value)}°"

/** The short word under the temperature. The API sends no forecast text, so it follows the sky icon. */
private fun conditionWord(kind: String): String = when (kind) {
    "rain" -> "Rain"
    "wind" -> "Windy"
    "night" -> "Clear"
    else -> "Sunny"
}

/** Same wording as the phone app: "Updated 2 h 4 min ago". */
private fun updatedAgo(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    if (s < 90) return "Updated $s sec ago"
    val m = Math.round(s / 60.0).toInt()
    if (m < 60) return "Updated $m min ago"
    val h = m / 60
    val r = m % 60
    if (h < 48) return "Updated $h h${if (r == 0) "" else " $r min"} ago"
    return "Updated ${h / 24} d ago"
}

private fun ageText(minutes: Int): String = when {
    minutes < 1 -> "updated just now"
    minutes < 60 -> "updated $minutes min ago"
    else -> "updated ${"%.1f".format(minutes / 60.0)} h ago"
}

/** A reading with its unit. Rain and pressure keep their decimals; the rest are whole numbers (UV keeps one). */
private fun reading(key: String, m: Measure): String {
    val decimals = when (key) {
        "dailyrainin", "rainratein" -> if (m.unit.contains("mm")) 1 else 2
        "baromrelin" -> if (m.unit.contains("hPa") || m.unit.contains("mb")) 1 else 2
        "uv", "windspeedmph", "windgustmph" -> 1
        else -> 0
    }
    val num = if (decimals == 0) Math.round(m.value).toString() else "%.${decimals}f".format(m.value)
    val unit = if (key == "humidity") "%" else m.unit
    val sep = if (unit == "%" || unit.startsWith("°")) "" else " "
    return "$num$sep$unit".trim()
}
