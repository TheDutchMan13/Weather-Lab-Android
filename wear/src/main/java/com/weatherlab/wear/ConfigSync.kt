package com.weatherlab.wear

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** The phone app sends its address, token and quiet hours to the watch through the Wear OS data layer. */
object ConfigSync {
    const val PATH = "/weatherlab/config"

    /** Saves what the phone sent, then asks the tiles and the complications to refresh. */
    fun apply(context: Context, map: DataMap) {
        val store = WatchStore(context)
        val address = map.getString("address").orEmpty()
        if (address != store.address) WatchRepo.clear(context)  // a different PC: the saved readings are not its
        store.address = address
        store.token = map.getString("token").orEmpty()
        // Quiet hours arrive from 0.7.28 phones; older ones leave the watch's own defaults (10 pm to 7 am).
        if (map.containsKey("quiet_on")) {
            store.quietOn = map.getBoolean("quiet_on")
            store.quietStart = map.getInt("quiet_start", 22)
            store.quietEnd = map.getInt("quiet_end", 7)
        }
        refreshSurfaces(context)
    }

    fun refreshSurfaces(context: Context) {
        runCatching {
            TileService.getUpdater(context).requestUpdate(WeatherTileService::class.java)
            TileService.getUpdater(context).requestUpdate(StormTileService::class.java)
        }
        WeatherComplication.refreshAll(context)
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

/**
 * Storm and alert notifications on the watch (0.7.28). The phone checks Weather Lab and sends each new one here; the
 * watch posts it with a short buzz, or silently during quiet hours. The phone's own storm notification stays on the
 * phone, so nothing shows twice.
 */
object WatchNotify {
    const val PATH = "/weatherlab/alert"
    const val CHANNEL = "weather_alerts"
    const val QUIET_CHANNEL = "weather_alerts_quiet"
    private const val STORM_ID = 7101
    private val BUZZ = longArrayOf(0, 180, 120, 180)

    fun post(context: Context, kind: String, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Weather alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = BUZZ
        })
        // Quiet hours use a second channel with no sound or buzz, so the alert is still there in the morning.
        nm.createNotificationChannel(NotificationChannel(QUIET_CHANNEL, "Weather alerts in quiet hours", NotificationManager.IMPORTANCE_LOW).apply {
            enableVibration(false)
            setSound(null, null)
        })
        if (kind == "storm_clear") {
            nm.cancel(STORM_ID)
            return
        }
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, 2),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val quiet = WatchStore(context).isQuiet()
        val n = Notification.Builder(context, if (quiet) QUIET_CHANNEL else CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(kind == "storm")
            .addAction(Notification.Action.Builder(null, "Open", open).build())
            .apply { if (kind == "storm") setTimeoutAfter(35 * 60 * 1000L) }
            .build()
        val id = if (kind == "storm") STORM_ID else (title + text).hashCode()
        nm.notify(id, n)
        ConfigSync.refreshSurfaces(context)
    }
}

class ConfigSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == ConfigSync.PATH }
            .forEach { ConfigSync.apply(this, DataMapItem.fromDataItem(it.dataItem).dataMap) }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchNotify.PATH) return
        val json = runCatching { JSONObject(String(event.data, Charsets.UTF_8)) }.getOrNull() ?: return
        WatchNotify.post(this, json.optString("kind"), json.optString("title"), json.optString("text"))
    }
}
