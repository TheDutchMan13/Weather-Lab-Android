package com.weatherlab.wear

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.tasks.await

/** The phone app sends its address and token to the watch through the Wear OS data layer. */
object ConfigSync {
    const val PATH = "/weatherlab/config"

    /** Saves what the phone sent, then asks the tile and the complication to refresh. */
    fun apply(context: Context, map: DataMap) {
        val store = WatchStore(context)
        store.address = map.getString("address").orEmpty()
        store.token = map.getString("token").orEmpty()
        TileService.getUpdater(context).requestUpdate(WeatherTileService::class.java)
        ComplicationDataSourceUpdateRequester
            .create(context, ComponentName(context, TempComplicationService::class.java))
            .requestUpdateAll()
    }

    /** On first start the phone's message may already be waiting: read it. Does nothing if the phone is not paired. */
    suspend fun pullExisting(context: Context) {
        runCatching {
            val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(PATH).build()
            val items = Wearable.getDataClient(context).getDataItems(uri).await()
            try {
                items.firstOrNull()?.let { apply(context, DataMapItem.fromDataItem(it).dataMap) }
            } finally {
                items.release()
            }
        }
    }
}

class ConfigSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == ConfigSync.PATH }
            .forEach { ConfigSync.apply(this, DataMapItem.fromDataItem(it.dataItem).dataMap) }
    }
}
