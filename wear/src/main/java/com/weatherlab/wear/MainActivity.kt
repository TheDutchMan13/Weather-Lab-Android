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
import kotlinx.coroutines.delay

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

private const val PAGES = 3  // Now, Next hours, Storms and alerts (0.7.28)

@Composable
private fun WatchApp(startPage: Int) {
    val context = LocalContext.current
    var ui by remember { mutableStateOf<Ui>(Ui.Loading) }
    var reload by remember { mutableIntStateOf(0) }
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
                            0 -> NowPage(shown.data) { reload++ }
                            1 -> ForecastPage(shown.data)
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

@Composable
private fun NowPage(d: WatchData, onRefresh: () -> Unit) = Page {
    item { Text(d.tempText, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
    d.feelsText?.let { item { Text(it, fontSize = 14.sp, color = Muted) } }
    item { Text(d.highLowText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Cyan) }
    if (d.spark.size >= 2) item { Spark(d.spark) }
    if (d.windLine.isNotBlank()) item { Small(d.windLine, Color.White) }
    if (d.pressureLine.isNotBlank()) item { Small(d.pressureLine, Muted) }
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
    item { Small("Swipe for the forecast and alerts", Muted) }
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

@Composable
private fun AlertsPage(d: WatchData) = Page {
    item { Small("Storms and alerts", Muted) }
    val storm = d.storm
    if (storm != null) {
        item { Text("Lightning ${storm.distance}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Amber) }
        item { Small("${storm.strikes30m} strikes in 30 min · ${storm.trendText}", Muted) }
    } else {
        item { Text("No lightning nearby", fontSize = 15.sp, color = Color.White) }
    }
    d.nwsAlerts.forEach { item { Small(it, Red) } }
    d.records.forEach { item { Small(it, Red) } }
    if (d.alerts.isEmpty()) {
        if (storm == null && d.nwsAlerts.isEmpty()) item { Small("All clear today", Cyan) }
    } else {
        d.alerts.take(5).forEach { a ->
            item { Small(a.text, Color.White) }
            item { Small(clock(java.time.Instant.ofEpochSecond(a.ts).atZone(java.time.ZoneId.systemDefault()).toLocalTime()), Muted) }
        }
    }
    d.offlineText?.let { item { Small(it, Amber) } }
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
