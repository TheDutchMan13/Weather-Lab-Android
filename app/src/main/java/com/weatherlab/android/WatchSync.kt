package com.weatherlab.android

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Hands the address, token and quiet hours to a paired Wear OS watch running the Weather Lab watch app. Harmless without one. */
object WatchSync {
    const val PATH = "/weatherlab/config"
    const val ALERT_PATH = "/weatherlab/alert"

    fun push(context: Context, address: String, token: String) {
        if (address.isBlank()) return
        val prefs = Prefs(context)
        try {
            val request = PutDataMapRequest.create(PATH).apply {
                dataMap.putString("address", address)
                dataMap.putString("token", token)
                dataMap.putBoolean("quiet_on", prefs.watchQuietOn)
                dataMap.putInt("quiet_start", prefs.watchQuietStart)
                dataMap.putInt("quiet_end", prefs.watchQuietEnd)
                dataMap.putLong("stamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context.applicationContext).putDataItem(request)
        } catch (_: Throwable) {
            // No Google Play services or no watch: nothing to do.
        }
    }

    /** Sends the current settings again, after the watch settings change. */
    fun pushSettings(context: Context) {
        val prefs = Prefs(context)
        push(context, prefs.address, prefs.token)
    }

    /**
     * Sends one notification to every connected watch (0.7.28). Blocks for a moment: call it off the main thread.
     * kind is "storm", "storm_clear" or "alert".
     */
    fun notify(context: Context, kind: String, title: String, text: String) {
        try {
            val app = context.applicationContext
            val nodes = Tasks.await(Wearable.getNodeClient(app).connectedNodes, 5, TimeUnit.SECONDS)
            val body = JSONObject().put("kind", kind).put("title", title).put("text", text).toString().toByteArray()
            nodes.forEach { node -> Wearable.getMessageClient(app).sendMessage(node.id, ALERT_PATH, body) }
        } catch (_: Throwable) {
            // No watch nearby, or no Google Play services.
        }
    }
}

/**
 * Decides what to send to the watch (0.7.28): the storm notification when it starts, changes or ends, and each new
 * alert Weather Lab logged. Called from the background check and while the app is open. Off the main thread only.
 */
object WatchAlerts {
    fun forward(context: Context, storm: Storm?, alerts: List<Alert>?) {
        val prefs = Prefs(context)
        if (!prefs.watchAlerts || prefs.address.isBlank()) return
        if (storm != null) {
            val note = StormWatch.describe(storm)
            val key = note?.title.orEmpty()
            if (key != prefs.lastWatchStorm) {
                if (note != null) WatchSync.notify(context, "storm", note.title, note.text)
                else WatchSync.notify(context, "storm_clear", "", "")
                prefs.lastWatchStorm = key
            }
        }
        if (alerts != null) {
            val newest = alerts.maxOfOrNull { it.ts } ?: return
            val seen = prefs.lastWatchAlertTs
            if (seen == 0L) {
                // First check: don't send the whole day's list at once.
                prefs.lastWatchAlertTs = newest
                return
            }
            alerts.filter { it.ts > seen }.sortedBy { it.ts }.takeLast(3).forEach {
                WatchSync.notify(context, "alert", "Weather Lab alert", it.text)
            }
            if (newest > seen) prefs.lastWatchAlertTs = newest
        }
    }
}
