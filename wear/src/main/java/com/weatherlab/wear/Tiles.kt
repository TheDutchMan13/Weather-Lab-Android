package com.weatherlab.wear

import android.net.Uri
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors

// Three tiles fed by the Weather Lab phone app: Now, Storm and Today. The phone sends a short summary each time it
// refreshes; the tiles show the newest one. Nothing here talks to your PC.

private val WHITE = argb(0xFFF2F2F2.toInt())
private val GREY = argb(0xFFA8A8A8.toInt())
private val RED = argb(0xFFF09595.toInt())
private val GREEN = argb(0xFF97C459.toInt())
private const val RESOURCES_VERSION = "1"
private val pool = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

private fun text(value: String, size: Float, color: androidx.wear.protolayout.ColorBuilders.ColorProp, bold: Boolean = false): LayoutElement =
    Text.Builder()
        .setText(value)
        .setFontStyle(
            FontStyle.Builder().setSize(sp(size)).setColor(color)
                .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                .build(),
        )
        .build()

private fun centered(vararg rows: LayoutElement): LayoutElement {
    val column = Column.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
    rows.forEach { column.addContent(it) }
    return LayoutElementBuilders.Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .addContent(column.build())
        .build()
}

/** The newest summary the phone sent, or null when none has arrived yet. */
private fun latest(service: TileService): JSONObject? {
    val client = Wearable.getDataClient(service)
    val buffer = Tasks.await(client.getDataItems(Uri.parse("wear://*/weatherlab/summary")))
    return try {
        var found: JSONObject? = null
        for (item in buffer) {
            found = JSONObject(DataMapItem.fromDataItem(item).dataMap.getString("json").orEmpty())
        }
        found
    } finally {
        buffer.release()
    }
}

abstract class SummaryTile : TileService() {
    abstract val freshnessMillis: Long
    abstract fun layout(data: JSONObject?): LayoutElement

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        pool.submit(
            Callable {
                val data = try {
                    latest(this)
                } catch (_: Exception) {
                    null
                }
                TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setFreshnessIntervalMillis(freshnessMillis)
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(data)))
                    .build()
            },
        )

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        pool.submit(Callable { ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build() })
}

private fun waiting(): LayoutElement = centered(
    text("Weather Lab", 14f, WHITE, bold = true),
    text("Open the phone app", 12f, GREY),
    text("to send data", 12f, GREY),
)

class NowTileService : SummaryTile() {
    override val freshnessMillis = 5 * 60 * 1000L
    override fun layout(data: JSONObject?): LayoutElement = if (data == null) waiting() else centered(
        text("Weather Lab", 11f, GREY),
        text(data.optString("temp"), 40f, WHITE, bold = true),
        text("H ${data.optString("hi")}  L ${data.optString("lo")}", 13f, WHITE),
        text("${data.optString("hum")} · ${data.optString("wind")}", 12f, GREY),
    )
}

class StormTileService : SummaryTile() {
    override val freshnessMillis = 5 * 60 * 1000L
    override fun layout(data: JSONObject?): LayoutElement {
        if (data == null) return waiting()
        val title = data.optString("storm_title")
        return if (title.isBlank()) {
            centered(text("Storm", 11f, GREY), text("All clear", 24f, GREEN, bold = true), text("No lightning nearby", 12f, GREY))
        } else {
            centered(text("Storm", 11f, GREY), text(title, 18f, RED, bold = true), text(data.optString("storm_line"), 11f, WHITE))
        }
    }
}

class TodayTileService : SummaryTile() {
    override val freshnessMillis = 15 * 60 * 1000L
    override fun layout(data: JSONObject?): LayoutElement = if (data == null) waiting() else centered(
        text("Today", 11f, GREY),
        text(data.optString("cond"), 26f, WHITE, bold = true),
        text("H ${data.optString("hi")}  L ${data.optString("lo")}", 13f, WHITE),
        text("Rain ${data.optString("rain")} · UV ${data.optString("uv")}", 12f, GREY),
    )
}

/** When the phone sends something new, ask the watch to redraw all three tiles. */
class SummaryListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val updater = TileService.getUpdater(this)
        updater.requestUpdate(NowTileService::class.java)
        updater.requestUpdate(StormTileService::class.java)
        updater.requestUpdate(TodayTileService::class.java)
    }
}
