package com.weatherlab.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// The storm countdown notification and the background refresh of the home-screen widget.
// Android runs background work about every 15 minutes at best, so the notification is a little slower than the phone
// app's. While Weather Lab is open on screen it is refreshed every minute.

class StormNote(val title: String, val text: String)

object StormWatch {
    const val CHANNEL = "storm_watch"
    const val NOTE_ID = 4417
    private const val WORK = "weatherlab_watch"

    /** What to say, or null when there is no storm to track. Wording follows the phone app. */
    fun describe(storm: Storm?): StormNote? {
        if (storm == null || storm.trend == "none") return null
        val last = storm.last ?: return null
        if (last.ageSeconds > 1800) return null
        val away = "${"%.1f".format(last.distance.value)} ${last.distance.unit}"
        val lastLine = "Last strike $away away."
        return when (storm.trend) {
            "overhead" -> StormNote("Storm overhead", "$lastLine ${storm.strikes10m} strikes in the last 10 minutes.")
            "approaching" -> StormNote("Storm approaching", lastLine)
            "receding" -> StormNote("Storm moving away", "$lastLine ${storm.strikes30m} strikes in the last 30 minutes.")
            else -> StormNote("Lightning nearby", "$lastLine Holding steady. ${storm.strikes30m} strikes in the last 30 minutes.")
        }
    }

    /** Shows, updates or removes the notification. Does nothing when the person turned it off or has not allowed it. */
    fun update(context: Context, storm: Storm?, enabled: Boolean) {
        val note = if (enabled) describe(storm) else null
        if (note == null) {
            clear(context)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL, "Storm countdown", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(note.title)
            .setContentText(note.text)
            .setOngoing(true)
            // Gone by itself after 35 minutes without an update (0.7.26), so a storm warning can't stay up for good
            // when the PC goes offline mid-storm. Each update while the storm lasts restarts the clock.
            .setTimeoutAfter(35 * 60 * 1000L)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setLocalOnly(true)  // the watch gets its own copy from WatchAlerts (0.7.28), so it isn't shown twice
            .setContentIntent(open)
            .build()
        nm.notify(NOTE_ID, n)
    }

    fun clear(context: Context) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTE_ID)
        } catch (_: Exception) {
        }
    }

    /**
     * Checks whether a watch is connected and remembers it (0.7.28), so the background check only runs for the watch
     * when there is one. Blocks briefly: call it off the main thread. Reschedules when the answer changes.
     */
    fun noteWatch(context: Context) {
        val found = try {
            com.google.android.gms.tasks.Tasks.await(
                com.google.android.gms.wearable.Wearable.getNodeClient(context.applicationContext).connectedNodes,
                3, TimeUnit.SECONDS,
            ).isNotEmpty()
        } catch (_: Throwable) {
            return
        }
        val prefs = Prefs(context)
        if (found && !prefs.watchPaired) {
            prefs.watchPaired = true
            schedule(context)
        }
    }

    /** True when a Weather Lab widget is on the home screen. */
    fun hasWidget(context: Context): Boolean = try {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, WeatherWidgetReceiver::class.java)).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    /**
     * Starts the repeating background check when something needs it - the storm notification or a widget - and stops
     * it otherwise (0.7.26), so the phone doesn't wake up every 15 minutes for nothing. Safe to call any time.
     */
    fun schedule(context: Context, stormNotify: Boolean = Prefs(context).stormNotify) {
        val wm = WorkManager.getInstance(context)
        val watch = Prefs(context).watchAlerts && Prefs(context).watchPaired
        if (!stormNotify && !hasWidget(context) && !watch) {
            wm.cancelUniqueWork(WORK)
            if (!stormNotify) clear(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<WatchWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Every 15 minutes: check lightning for the notification, then refresh the widget. */
class WatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (prefs.address.isBlank() || prefs.token.isBlank()) return Result.success()
        try {
            val api = WeatherLabApi(prefs.address, prefs.token)
            val storm = withContext(Dispatchers.IO) { api.storm() }
            StormWatch.update(applicationContext, storm, prefs.stormNotify)
            if (prefs.watchAlerts) {
                val alerts = withContext(Dispatchers.IO) { runCatching { api.alerts() }.getOrNull() }
                withContext(Dispatchers.IO) { WatchAlerts.forward(applicationContext, storm, alerts) }
            }
        } catch (_: Exception) {
            // Weather Lab was not reachable (PC off, or an older Weather Lab). Leave things as they are.
        }
        try {
            WeatherWidget().updateAll(applicationContext)
        } catch (_: Exception) {
        }
        return Result.success()
    }
}
