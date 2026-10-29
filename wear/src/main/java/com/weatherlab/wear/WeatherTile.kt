package com.weatherlab.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
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
import kotlinx.coroutines.withContext

/** The swipe-in tile: the temperature large, today's high and low under it, and the rain chance. Tap to open the app. */
class WeatherTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            val store = WatchStore(this@WeatherTileService)
            val lines: List<Triple<String, Float, Boolean>> = if (!store.configured) {
                listOf(Triple("Weather Lab", 16f, true), Triple("Open the phone app to connect", 12f, false))
            } else {
                try {
                    val d = withContext(Dispatchers.IO) { WatchApi.load(store.address, store.token) }
                    buildList {
                        add(Triple(d.tempText, 40f, true))
                        add(Triple(d.highLowText, 16f, true))
                        d.rainChance?.let { add(Triple("Rain $it", 13f, false)) }
                    }
                } catch (e: Exception) {
                    listOf(Triple("Weather Lab", 16f, true), Triple("Couldn't reach your PC", 12f, false))
                }
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setFreshnessIntervalMillis(15 * 60 * 1000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(lines)))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build())

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun layout(lines: List<Triple<String, Float, Boolean>>): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        lines.forEach { (text, size, bold) ->
            column.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(text)
                    .setMaxLines(2)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(size))
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                            .build(),
                    )
                    .build(),
            )
        }
        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column.build())
            .build()
    }

    private companion object {
        const val RES_VERSION = "1"
    }
}
