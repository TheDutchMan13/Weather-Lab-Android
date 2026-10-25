package com.weatherlab.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private var locked by mutableStateOf(false)
    private var leftAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(this)
        // With App lock on, a fresh start is locked and the app's picture is hidden in the recent apps list.
        if (prefs.lockAfter >= 0) {
            locked = true
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        StormWatch.schedule(this)
        setContent {
            var scheme by remember { mutableStateOf(Scheme.from(prefs.scheme)) }
            var textScale by remember { mutableFloatStateOf(prefs.textScale) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
            WeatherLabTheme(scheme) {
                // Status and navigation bar icons follow the scheme, not the phone's own dark mode.
                val view = LocalView.current
                val light = palette(scheme).light
                SideEffect {
                    val c = WindowCompat.getInsetsController(window, view)
                    c.isAppearanceLightStatusBars = light
                    c.isAppearanceLightNavigationBars = light
                }
                if (locked) {
                    LockScreen { locked = false }
                } else {
                    WeatherLabApp(prefs, scheme, textScale, onTextScale = {
                        textScale = it
                        prefs.textScale = it
                    }) {
                        scheme = it
                        prefs.scheme = it.key
                    }
                }
            }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground.value = true
        val after = prefs.lockAfter
        if (after >= 0 && leftAt > 0 && (SystemClock.elapsedRealtime() - leftAt) / 1000 >= after) locked = true
    }

    override fun onStop() {
        super.onStop()
        AppState.foreground.value = false
        leftAt = SystemClock.elapsedRealtime()
    }
}

private val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

private enum class Tab(val label: String, val icon: ImageVector, val selected: ImageVector) {
    LIVE("Live", Icons.Outlined.Cloud, Icons.Filled.Cloud),
    FORECAST("Forecast", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    STORM("Storm", Icons.Outlined.Bolt, Icons.Filled.Bolt),
    ALERTS("Alerts", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    MORE("More", Icons.Outlined.Apps, Icons.Filled.Apps),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherLabApp(prefs: Prefs, scheme: Scheme, textScale: Float, onTextScale: (Float) -> Unit, onScheme: (Scheme) -> Unit) {
    var address by remember { mutableStateOf(prefs.address) }
    var token by remember { mutableStateOf(prefs.token) }
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reloadCount by remember { mutableIntStateOf(0) }
    var stormOn by remember { mutableStateOf(prefs.stormNotify) }
    var pauseBg by remember { mutableStateOf(prefs.pauseInBackground) }
    var lockAfter by remember { mutableIntStateOf(prefs.lockAfter) }
    val context = LocalContext.current
    val needsSetup = prefs.address.isBlank() || prefs.token.isBlank()
    var tab by remember { mutableStateOf(if (needsSetup) Tab.MORE else Tab.LIVE) }
    var sub by remember { mutableStateOf(if (needsSetup) MoreSub.SETTINGS else MoreSub.MENU) }
    var dayKey by remember { mutableStateOf<String?>(null) }
    val inSub = tab == Tab.MORE && sub != MoreSub.MENU
    val goBack: () -> Unit = {
        if (dayKey != null) {
            dayKey = null
        } else {
            sub = MoreSub.MENU
        }
    }
    BackHandler(enabled = inSub) { goBack() }

    // Loads on entry, when the settings change, when Refresh is tapped, and then once a minute.
    LaunchedEffect(address, token, reloadCount, stormOn) {
        while (address.isNotBlank() && token.isNotBlank()) {
            // With "Pause in the background" on, wait here until the app is on screen again.
            if (pauseBg) AppState.foreground.first { it }
            loading = true
            try {
                snapshot = withContext(Dispatchers.IO) { WeatherLabApi(address, token).snapshot() }
                error = null
                StormWatch.update(context, snapshot?.storm, stormOn)
            } catch (e: Exception) {
                error = e.message ?: "Couldn't reach Weather Lab."
            }
            loading = false
            delay(60_000)
        }
    }

    // The Alerts tab shows a badge when something was logged in the last 24 hours, like the Storm tab on the phone app.
    val recentAlerts = snapshot?.alerts.orEmpty().count { System.currentTimeMillis() / 1000 - it.ts < 24 * 3600 }

    val wide = LocalConfiguration.current.screenWidthDp >= 720
    val selectTab: (Tab) -> Unit = { t ->
        if (t == Tab.MORE && tab == Tab.MORE) {
            sub = MoreSub.MENU
            dayKey = null
        }
        tab = t
        if (t == Tab.LIVE) reloadCount++
    }
    val tabIcon: @Composable (Tab) -> Unit = { t ->
        val icon = if (tab == t) t.selected else t.icon
        if (t == Tab.ALERTS && recentAlerts > 0) {
            BadgedBox(badge = { Badge(containerColor = C.bad) { Text("Alert") } }) {
                Icon(icon, contentDescription = t.label)
            }
        } else {
            Icon(icon, contentDescription = t.label)
        }
    }

    Scaffold(
        containerColor = C.bg,
        topBar = {
            TopAppBar(
                title = {
                    val title = if (tab != Tab.MORE) tab.label else if (dayKey != null) "Day" else sub.label
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.W800)
                    }
                },
                navigationIcon = {
                    if (inSub) {
                        IconButton(onClick = { goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = C.bg,
                    scrolledContainerColor = C.bg,
                    titleContentColor = C.text,
                    actionIconContentColor = C.text,
                ),
            )
        },
        bottomBar = {
            if (!wide) {
                NavigationBar(containerColor = C.card) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { selectTab(t) },
                            icon = { tabIcon(t) },
                            label = { Text(t.label) },
                            colors = navItemColors(),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
        if (wide) {
            NavigationRail(containerColor = C.card) {
                Tab.entries.forEach { t ->
                    NavigationRailItem(
                        selected = tab == t,
                        onClick = { selectTab(t) },
                        icon = { tabIcon(t) },
                        label = { Text(t.label) },
                        colors = railItemColors(),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxSize()) {
            when (tab) {
                Tab.LIVE -> Paned(wide, { ChartsPage(address, token) }) { LivePage(
                    snapshot = snapshot, error = error, loading = loading,
                    configured = address.isNotBlank() && token.isNotBlank(),
                    onRefresh = { reloadCount++ },
                    onOpenSettings = {
                        tab = Tab.MORE
                        sub = MoreSub.SETTINGS
                    },
                    address = address, token = token,
                ) }
                Tab.FORECAST -> Paned(wide, { SunPage(address, token) }) { ForecastPage(address, token) }
                Tab.STORM -> Paned(wide, { LightningPage(address, token) }) { StormPage(snapshot, stormOn) }
                Tab.ALERTS -> AlertsPage(snapshot)
                Tab.MORE -> when {
                    sub == MoreSub.MENU -> MorePage { sub = it }
                    sub == MoreSub.CHARTS -> ChartsPage(address, token)
                    sub == MoreSub.DAYS && dayKey == null -> DailyPage(address, token) { dayKey = it }
                    sub == MoreSub.DAYS -> DayDetailPage(address, token, dayKey.orEmpty())
                    sub == MoreSub.RECORDS -> RecordsPage(address, token) { sub = it }
                    sub == MoreSub.RANKINGS -> RankingsPage(address, token)
                    sub == MoreSub.WIND -> WindPage(address, token)
                    sub == MoreSub.SUN -> SunPage(address, token)
                    sub == MoreSub.HEAT -> HeatPage(address, token)
                    sub == MoreSub.RAIN_CAL -> RainCalendarPage(address, token)
                    sub == MoreSub.RAIN_EVENTS -> RainEventsPage(address, token)
                    sub == MoreSub.LIGHTNING -> LightningPage(address, token)
                    sub == MoreSub.STATION -> StationPage(address, token)
                    sub == MoreSub.WHATS_NEW -> WhatsNewPage(address, token)
                    sub == MoreSub.ON_THIS_DAY -> OnThisDayPage(address, token)
                    sub == MoreSub.COMPARE -> ComparePage(address, token)
                    sub == MoreSub.MONTH -> MonthCardPage(address, token)
                    sub == MoreSub.YEAR -> YearPage(address, token)
                    sub == MoreSub.MILESTONES -> MilestonesPage(address, token)
                    sub == MoreSub.ANOMALIES -> AnomaliesPage(address, token)
                    sub == MoreSub.ALERT_HISTORY -> AlertHistoryPage(address, token)
                    sub == MoreSub.FC_ACCURACY -> ForecastAccuracyPage(address, token)
                    sub == MoreSub.EXPORT -> ExportPage(address, token)
                    sub == MoreSub.REWIND -> RewindPage(address, token)
                    sub == MoreSub.AIRPORT -> AirportPage(address, token)
                    sub == MoreSub.TROPICS -> TropicsPage(address, token)
                    sub == MoreSub.RADAR -> RadarPage(address, token)
                    sub == MoreSub.AIR -> AirPage(address, token)
                    sub == MoreSub.PUSH -> PushPage(address, token)
                    else -> SettingsPage(
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
                    textScale = textScale,
                    onTextScale = onTextScale,
                    stormOn = stormOn,
                    onStorm = { on ->
                        stormOn = on
                        prefs.stormNotify = on
                    },
                    pauseBg = pauseBg,
                    onPauseBg = { on ->
                        pauseBg = on
                        prefs.pauseInBackground = on
                    },
                    lockAfter = lockAfter,
                    onLockAfter = { seconds ->
                        lockAfter = seconds
                        prefs.lockAfter = seconds
                    },
                    )
                }
            }
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
    address: String = "",
    token: String = "",
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
                    Dashboard(snapshot, address, token)
                }
            }
        }
    }
}

/** One tile of the Live page: what it says, a small line under the figure, and which 24-hour series it draws. */
private class LiveTile(
    val label: String, val value: String, val sub: String? = null, val series: String? = null, val history: String? = null,
    val decimals: Int = 0,
)

/** "low 15 · high 52" for a 24-hour line, "scale 0 to 11" for UV and "no rain today" for a dry rain line; null with too few points. */
private fun rangeText(t: LiveTile, line: List<Double>?): String? {
    if (line == null || line.size < 2) return null
    if (t.series == "uv") return "scale 0 to 11"
    if (t.series == "dailyrainin" || t.series == "rainratein") {
        if (line.all { it <= 0.0 }) return "no rain today"
    }
    fun f(v: Double) = if (t.decimals == 0) Math.round(v).toString() else "%.${t.decimals}f".format(v)
    return "low ${f(line.min())} \u00B7 high ${f(line.max())}"
}

private val COMPASS = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

/** The dew point in plain words, with the same limits as the Windows app. [fahrenheit] is the dew point in °F. */
private fun dewWord(fahrenheit: Double): String = when {
    fahrenheit < 55 -> "dry and comfortable"
    fahrenheit < 60 -> "comfortable"
    fahrenheit < 65 -> "a little humid"
    fahrenheit < 70 -> "muggy"
    else -> "oppressive"
}

private fun compass(deg: Double): String = COMPASS[(((deg % 360 + 360) % 360 + 11.25) / 22.5).toInt() % 16]

/** "6:47 PM" in this phone's time zone from an ISO time such as 2026-10-24T01:47:00+00:00, or null when it can't be read. */
private fun clockOf(iso: String): String? = try {
    java.time.OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
} catch (e: Exception) {
    null
}

/** The Live tiles in the order of the phone app. Peaks, trend words and the airport come from newer Weather Lab versions. */
private fun liveTiles(s: Snapshot): List<LiveTile> {
    val v = s.current.values
    val tl = s.tiles
    fun peak(key: String, decimals: Int): String? = tl?.peaks?.get(key)?.let {
        if (decimals == 0) Math.round(it).toString() else "%.${decimals}f".format(it)
    }
    val out = ArrayList<LiveTile>()
    v["humidity"]?.let { m ->
        out += LiveTile("Humidity", reading("humidity", m), v["humidityin"]?.let { "Indoor ${Math.round(it.value)}%" }, "humidity", "humidity")
    }
    v["windspeedmph"]?.let { m ->
        out += LiveTile("Wind", reading("windspeedmph", m), v["winddir"]?.let { compass(it.value) }, "windspeedmph", "windspeedmph")
    }
    v["windgustmph"]?.let { m ->
        out += LiveTile("Gust", reading("windgustmph", m), peak("windgustmph", 1)?.let { "Peak today $it" }, "windgustmph", "windgustmph")
    }
    v["baromrelin"]?.let { m ->
        val word = tl?.pressureWord
        val arrow = when {
            word == null -> ""
            word.startsWith("rising") -> "\u2191 "
            word.startsWith("falling") -> "\u2193 "
            else -> "\u2192 "
        }
        out += LiveTile("Pressure", reading("baromrelin", m), word?.let { arrow + it }, "baromrelin", "baromrelin", decimals = 2)
    }
    v["tempinf"]?.let { m -> out += LiveTile("Indoor temperature", reading("tempinf", m), null, "tempinf", "tempinf") }
    v["humidityin"]?.let { m -> out += LiveTile("Indoor humidity", reading("humidityin", m), null, "humidityin", "humidityin")
    }
    s.airport?.let { a ->
        val vis = a.visibility
        if (a.available && vis != null) {
            val name = a.name.ifBlank { "Airport" }
            val sub = listOfNotNull(clockOf(a.time)).let { if (it.isEmpty()) name else "$name, ${it[0]}" } +
                (if (a.present.isNotEmpty()) " \u00B7 ${a.present.joinToString(", ")}" else "")
            out += LiveTile("Visibility", "${"%.1f".format(vis.value)} ${vis.unit}", sub)
        }
    }
    v["uv"]?.let { m -> out += LiveTile("UV index", reading("uv", m), peak("uv", 0)?.let { "Peak today $it" }, "uv", "uv") }
    v["solarradiation"]?.let { m ->
        out += LiveTile("Solar", reading("solarradiation", m), peak("solarradiation", 0)?.let { "Peak today $it" }, "solarradiation", "solarradiation")
    }
    v["rainratein"]?.let { m -> out += LiveTile("Rain rate", reading("rainratein", m), null, "rainratein", "rainratein", decimals = 2) }
    v["dailyrainin"]?.let { m -> out += LiveTile("Rain today", reading("dailyrainin", m), null, "dailyrainin", "dailyrainin", decimals = 2) }
    return out
}

@Composable
private fun Dashboard(s: Snapshot, address: String, token: String) {
    var historyOf by remember { mutableStateOf<Pair<String, String>?>(null) }
    historyOf?.let { (key, title) -> MeasureHistorySheet(address, token, key, title) { historyOf = null } }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (temp == null) "--°" else "${one(temp.value)}°", style = T.hero, color = C.text,
                    modifier = if (temp == null) Modifier else Modifier.clickable(onClickLabel = "Show temperature history") {
                        historyOf = Pair("tempf", "Temperature")
                    },
                )
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
            Text("Feels like ${deg1(v["feelslikef"])}", style = T.body, color = C.muted)
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
        liveTiles(s).map { t ->
            val line = t.series?.let { k -> s.tiles?.series?.get(k) ?: emptyList() }
            val item: @Composable (Modifier) -> Unit = { mod ->
                Tile(
                    t.label, t.value, mod, sub = t.sub, trend = line, range = rangeText(t, line),
                    onClick = t.history?.let { k -> { historyOf = Pair(k, t.label) } },
                )
            }
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

    // Dew point at the bottom, with its last 24 hours. Tap it for its history.
    v["dewpointf"]?.let { d ->
        val line = s.tiles?.series?.get("dewpointf")
        val f = if (d.unit.contains("C")) d.value * 9 / 5 + 32 else d.value
        Spacer(Modifier.height(16.dp))
        val card = LiveTile("Dew point", reading("dewpointf", d), null, "dewpointf", "dewpointf")
        Tile(
            card.label, card.value, Modifier.fillMaxWidth(), trend = line, range = rangeText(card, line),
            onClick = { historyOf = Pair("dewpointf", "Dew point") },
        )
        Text("Dew point: ${dewWord(f)}. Tap the card for its history.", style = T.label, color = C.muted, modifier = Modifier.padding(top = 6.dp))
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
    textScale: Float,
    onTextScale: (Float) -> Unit,
    stormOn: Boolean,
    onStorm: (Boolean) -> Unit,
    pauseBg: Boolean,
    onPauseBg: (Boolean) -> Unit,
    lockAfter: Int,
    onLockAfter: (Int) -> Unit,
) {
    val context = LocalContext.current
    var lockNote by remember { mutableStateOf<String?>(null) }
    var codeText by remember { mutableStateOf("") }
    var codeNote by remember { mutableStateOf<String?>(null) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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

        Section("Notifications")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Storm countdown", style = T.body, color = C.text)
                    Text(
                        "Shows a notification while lightning is near. Checked about every 15 minutes in the background.",
                        style = T.label, color = C.muted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = stormOn,
                    onCheckedChange = { on ->
                        onStorm(on)
                        if (on && Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.cyan, checkedThumbColor = C.bg),
                )
            }
        }

        Section("Security and battery")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("App lock", style = T.body, color = C.text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLock.CHOICES.forEach { (name, seconds) ->
                        FilterChip(
                            selected = lockAfter == seconds,
                            onClick = {
                                if (seconds >= 0 && !AppLock.deviceHasLock(context)) {
                                    lockNote = "Set a screen lock (PIN, pattern, password or fingerprint) in your phone's settings first."
                                } else {
                                    lockNote = if (seconds >= 0) "Takes effect the next time the app starts." else null
                                    onLockAfter(seconds)
                                }
                            },
                            label = { Text(name, color = C.text) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = C.card,
                                selectedContainerColor = C.cyan.copy(alpha = 50 / 255f),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = lockAfter == seconds,
                                borderColor = C.line, selectedBorderColor = C.cyan,
                            ),
                        )
                    }
                }
                Text(
                    "Uses your phone's own fingerprint, face, PIN, pattern or password. It also hides the app in the recent apps list.",
                    style = T.label, color = C.muted,
                )
                lockNote?.let { Text(it, style = T.label, color = C.warn) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pause refreshing in the background", style = T.body, color = C.text)
                        Text(
                            "The Live page stops updating while the app isn't on screen and catches up when you return. " +
                                "The storm countdown above has its own switch.",
                            style = T.label, color = C.muted,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = pauseBg, onCheckedChange = onPauseBg,
                        colors = SwitchDefaults.colors(checkedTrackColor = C.cyan, checkedThumbColor = C.bg),
                    )
                }
            }
        }

        Section("Move to a new phone")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "A setup code holds your address and token. Copy it here, send it to yourself, and paste it on the new phone. " +
                        "Treat it like a password.",
                    style = T.label, color = C.muted,
                )
                Button(
                    onClick = {
                        if (a.isNotBlank() && t.isNotBlank()) {
                            copyText(context, "Weather Lab setup code", SetupCode.make(a, t))
                            codeNote = "Setup code copied."
                        } else {
                            codeNote = "Fill in the address and token first."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.card, contentColor = C.text),
                ) { Text("Copy setup code") }
                OutlinedTextField(
                    value = codeText, onValueChange = { codeText = it }, label = { Text("Paste a setup code") },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val found = SetupCode.read(codeText)
                        if (found == null) {
                            codeNote = "That isn't a Weather Lab setup code."
                        } else {
                            a = found.first
                            t = found.second
                            codeText = ""
                            codeNote = "Address and token filled in. Tap Save and connect above."
                        }
                    },
                    enabled = codeText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
                ) { Text("Use this code") }
                codeNote?.let { Text(it, style = T.label, color = C.muted) }
            }
        }

        Section("Appearance")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Text("Text size", style = T.body, color = C.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Normal" to 1.0f, "Large" to 1.15f, "Largest" to 1.3f).forEach { (name, size) ->
                    FilterChip(
                        selected = textScale == size,
                        onClick = { onTextScale(size) },
                        label = { Text(name, color = C.text) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = C.card,
                            selectedContainerColor = C.cyan.copy(alpha = 50 / 255f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = textScale == size,
                            borderColor = C.line, selectedBorderColor = C.cyan,
                        ),
                    )
                }
            }
            Text(
                "Makes all text in the app bigger. It adds to the font size set in your phone settings.",
                style = T.label, color = C.muted,
            )
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
internal fun conditionWord(kind: String): String = when (kind) {
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
