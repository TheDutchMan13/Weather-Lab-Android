package com.weatherlab.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TitleCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var startPage by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startPage = intent?.getIntExtra(EXTRA_PAGE, 0) ?: 0
        setContent { WatchApp(startPage) }
    }

    // A tapped alert notification opens the Alerts page, also when the app is already open.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startPage = intent.getIntExtra(EXTRA_PAGE, startPage)
    }

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_ALERTS = 3
    }
}

private sealed interface Ui {
    data object Loading : Ui
    data object NotSetUp : Ui
    data class Error(val message: String) : Ui
    data class Ready(val data: WatchData) : Ui
}

private val Cyan = Color(0xFF5CC8E8)
private val Muted = Color(0xFF8A97A6)
private val Amber = Color(0xFFF2B34A)
private val Red = Color(0xFFEF6B5B)

// Glance, Details, Next hours, Storms and alerts. Glance came first in 0.7.29; the old Now page is Details.
private const val PAGES = 4

@Composable
private fun WatchApp(startPage: Int) {
    val context = LocalContext.current
    var ui by remember { mutableStateOf<Ui>(Ui.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, PAGES - 1)) { PAGES }
    LaunchedEffect(startPage) { pager.scrollToPage(startPage.coerceIn(0, PAGES - 1)) }

    // Alerts arrive from the phone as notifications; Wear OS 4 and newer ask once to allow them (0.7.28).
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Loads when opened, when Refresh is tapped, and then every 5 minutes while the app is on screen. It stops while
    // the app is off screen (0.7.26). The saved copy is shown straight away, then replaced by the fresh one.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reload) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var force = reload > 0
            while (true) {
                val store = WatchStore(context)
                if (!store.configured) ConfigSync.pullExisting(context)
                if (!store.configured) {
                    ui = Ui.NotSetUp
                } else {
                    if (ui !is Ui.Ready) ui = WatchRepo.cached(context, freshMs = 10 * 60 * 1000L)?.let { Ui.Ready(it) } ?: Ui.Loading
                    ui = try {
                        Ui.Ready(WatchRepo.get(context, maxAgeMs = 60_000L, force = force))
                    } catch (e: Exception) {
                        Ui.Error(e.message ?: "Couldn't reach Weather Lab. Is the PC on, and the watch on Wi-Fi?")
                    }
                    if ((ui as? Ui.Ready)?.data?.offlineSince == null) ConfigSync.refreshSurfaces(context)
                }
                force = false
                delay(300_000)
            }
        }
    }

    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            when (val shown = ui) {
                Ui.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Ui.NotSetUp -> Page {
                    item { Message("Open Weather Lab on your phone to connect this watch.") }
                    item { RefreshChip { reload++ } }
                }
                is Ui.Error -> Page {
                    item { Message(shown.message) }
                    item { RefreshChip { reload++ } }
                }
                is Ui.Ready -> Box(Modifier.fillMaxSize()) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        when (page) {
                            0 -> GlancePage(shown.data) { scope.launch { pager.animateScrollToPage(MainActivity.PAGE_ALERTS) } }
                            1 -> DetailsPage(shown.data) { reload++ }
                            2 -> ForecastPage(shown.data)
                            else -> AlertsPage(shown.data)
                        }
                    }
                    Dots(pager.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun Page(content: ScalingLazyListScope.() -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        autoCentering = AutoCenteringParams(itemIndex = 0),
        content = content,
    )
}

/** How many things on the alerts page need attention: lightning, weather service warnings and today's PC alerts. */
private val WatchData.alertCount: Int get() = (if (storm != null) 1 else 0) + nwsAlerts.size + alerts.size

/** First page (0.7.29): the temperature, one line about rain, and whether anything needs a look. Nothing to scroll. */
@Composable
private fun GlancePage(d: WatchData, onAlerts: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(d.tempText, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(
            d.rainSoon ?: d.highLowText, fontSize = 15.sp, color = Cyan, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        val count = d.alertCount
        if (count == 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Cyan, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("All clear", fontSize = 13.sp, color = Muted)
            }
        } else {
            val urgent = d.storm != null || d.nwsAlerts.isNotEmpty()
            val label = d.storm?.let { "Lightning ${it.distance}" }
                ?: if (count == 1) "1 alert" else "$count alerts"
            Chip(
                onClick = onAlerts,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Box(Modifier.size(10.dp).background(if (urgent) Red else Amber, CircleShape)) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        d.offlineText?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 11.sp, color = Amber, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** Second page: everything that used to be on the first page. */
@Composable
private fun DetailsPage(d: WatchData, onRefresh: () -> Unit) = Page {
    item { Small("Details", Muted) }
    d.feelsText?.let { item { Text(it, fontSize = 14.sp, color = Color.White) } }
    item { Text(d.highLowText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Cyan) }
    if (d.spark.size >= 2) item { Spark(d.spark) }
    if (d.windLine.isNotBlank()) item { Small(d.windLine, Color.White) }
    if (d.pressureLine.isNotBlank()) item { Small(d.pressureLine, Muted) }
    d.uv?.let { uv -> item { Small("UV ${"%.0f".format(uv)}" + (d.uvWord?.let { " · $it" } ?: ""), Color.White) } }
    d.rainToday?.let { r -> item { Small("Rain today ${"%.${if (d.rainUnit == "in") 2 else 1}f".format(r)} ${d.rainUnit}".trim(), Color.White) } }
    d.offlineText?.let { item { Small(it, Amber) } }
    d.headline?.let {
        item {
            Text(
                it, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    item { RefreshChip(onRefresh) }
}

@Composable
private fun ForecastPage(d: WatchData) = Page {
    item { Small("Next hours", Muted) }
    val soon = d.rainSoon
    if (soon == null) {
        item { Message("No forecast yet. Weather Lab needs the station's location (Settings > Station on the PC).") }
        return@Page
    }
    item { Text(soon, fontSize = 15.sp, color = Cyan, textAlign = TextAlign.Center) }
    d.hours.take(8).forEach { h ->
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(h.label, fontSize = 14.sp, color = Color.White, modifier = Modifier.width(52.dp))
                Text(h.temp?.let { "$it°" } ?: "--", fontSize = 14.sp, color = Color.White)
                Text(h.rainChance?.let { "$it%" } ?: "", fontSize = 14.sp, color = if ((h.rainChance ?: 0) >= 50) Cyan else Muted)
            }
        }
    }
    d.sunset?.let { item { Small("Sunset $it", Muted) } }
}

/** Storms and alerts as a scrolling list of cards (0.7.29). Tap a card to read all of it. */
@Composable
private fun AlertsPage(d: WatchData) = Page {
    item { Small("Storms and alerts", Muted) }
    val storm = d.storm
    if (storm != null) {
        item { AlertCard("Lightning ${storm.distance}", Amber, "now", "${storm.strikes30m} strikes in 30 min · ${storm.trendText}") }
    }
    d.nwsAlerts.forEach { item { AlertCard("Weather service", Red, null, it) } }
    d.alerts.forEach { a ->
        val time = clock(java.time.Instant.ofEpochSecond(a.ts).atZone(java.time.ZoneId.systemDefault()).toLocalTime())
        item { AlertCard("Weather Lab", Color.White, time, a.text) }
    }
    d.records.forEach { item { AlertCard("Record", Cyan, "today", it) } }
    if (d.alertCount == 0) item { Small(if (d.records.isEmpty()) "All clear today" else "No alerts today", Cyan) }
    d.offlineText?.let { item { Small(it, Amber) } }
}

@Composable
private fun AlertCard(title: String, titleColor: Color, time: String?, text: String) {
    var open by remember { mutableStateOf(false) }
    TitleCard(
        onClick = { open = !open },
        title = { Text(title, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        time = time?.let { t -> { Text(t, color = Muted) } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Text(
            text, fontSize = 13.sp, color = Color.White,
            maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The last 24 hours of temperature as a small line. */
@Composable
private fun Spark(points: List<Double>) {
    Canvas(Modifier.width(110.dp).height(22.dp)) {
        val lo = points.min()
        val span = (points.max() - lo).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        points.forEachIndexed { i, v ->
            val p = Offset(size.width * i / (points.size - 1), size.height - ((v - lo) / span).toFloat() * size.height)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, Cyan, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun Dots(current: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(PAGES) { i ->
            Box(Modifier.size(5.dp).background(if (i == current) Color.White else Muted.copy(alpha = 0.5f), CircleShape))
        }
    }
}

@Composable
private fun Small(text: String, color: Color) {
    Text(
        text, fontSize = 12.sp, color = color, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
    )
}

@Composable
private fun Message(text: String) {
    Text(
        text, fontSize = 13.sp, color = Color.White, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RefreshChip(onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text("Refresh") },
        colors = ChipDefaults.secondaryChipColors(),
    )
}
