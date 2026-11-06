package com.weatherlab.wear

import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * The weather tile: the temperature, today's high and low, a 24-hour graph (0.7.28) and when rain is due. When the PC
 * can't be reached it shows the last reading and its time. Tap to open the app.
 */
class WeatherTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            var version = "none"
            val lines: List<TileLine> = try {
                val d = WatchRepo.get(this@WeatherTileService, maxAgeMs = 10 * 60 * 1000L)
                if (d.spark.size >= 2) version = sparkVersion(d.spark)
                buildList {
                    add(TileLine(d.tempText, 38f, bold = true))
                    add(TileLine(d.highLowText, 15f, CYAN, bold = true))
                    if (d.spark.size >= 2) add(TileLine("", 0f, spark = true))
                    val offline = d.offlineText
                    when {
                        offline != null -> add(TileLine(offline, 12f, AMBER))
                        d.rainSoon != null -> add(TileLine(d.rainSoon!!, 13f))
                        d.rainChance != null -> add(TileLine("Rain ${d.rainChance}", 13f))
                    }
                }
            } catch (e: WatchRepo.NotSetUp) {
                listOf(TileLine("Weather Lab", 16f, bold = true), TileLine("Open the phone app to connect", 12f))
            } catch (e: Exception) {
                listOf(TileLine("Weather Lab", 16f, bold = true), TileLine("Couldn't reach your PC", 12f))
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(version)
                .setFreshnessIntervalMillis(15 * 60 * 1000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(tileLayout(this@WeatherTileService, lines)))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        scope.future {
            val spark = WatchRepo.cached(this@WeatherTileService)?.spark.orEmpty()
            tileResources(requestParams.version, spark)
        }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
