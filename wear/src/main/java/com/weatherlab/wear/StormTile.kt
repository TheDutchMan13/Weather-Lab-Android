package com.weatherlab.wear

import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/** The storm and alerts tile (0.7.28): lightning nearby, today's alerts and records, or All clear. Tap to open the app. */
class StormTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            val lines: List<TileLine> = try {
                val d = WatchRepo.get(this@StormTileService, maxAgeMs = 5 * 60 * 1000L)
                stormLines(d)
            } catch (e: WatchRepo.NotSetUp) {
                listOf(TileLine("Storms and alerts", 15f, bold = true), TileLine("Open the phone app to connect", 12f))
            } catch (e: Exception) {
                listOf(TileLine("Storms and alerts", 15f, bold = true), TileLine("Couldn't reach your PC", 12f))
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                // Checked more often than the weather tile: lightning moves fast.
                .setFreshnessIntervalMillis(10 * 60 * 1000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(tileLayout(this@StormTileService, lines)))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion("1").build())

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        fun stormLines(d: WatchData): List<TileLine> = buildList {
            val storm = d.storm
            if (storm != null) {
                add(TileLine("Lightning ${storm.distance}", 16f, AMBER, bold = true))
                add(TileLine("${storm.trendText.replaceFirstChar { it.uppercase() }} · last strike ${ago(storm.lastAgeSeconds)}", 12f, MUTED))
            } else {
                add(TileLine("No lightning nearby", 14f, bold = true))
            }
            d.nwsAlerts.firstOrNull()?.let { add(TileLine(it, 13f, RED)) }
            when (d.alerts.size) {
                0 -> if (storm == null && d.nwsAlerts.isEmpty()) add(TileLine("All clear today", 13f, CYAN))
                1 -> add(TileLine("1 alert today", 13f))
                else -> add(TileLine("${d.alerts.size} alerts today", 13f))
            }
            d.alerts.firstOrNull()?.let { add(TileLine(it.text, 12f, MUTED)) }
            d.records.firstOrNull()?.let { add(TileLine(it, 12f, RED)) }
            d.offlineText?.let { add(TileLine(it, 11f, AMBER)) }
        }
    }
}
