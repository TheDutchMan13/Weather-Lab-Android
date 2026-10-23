package com.weatherlab.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
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

    /** Starts the repeating background check once. Safe to call every time the app opens. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WatchWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Every 15 minutes: check lightning for the notification, then refresh the widget. */
class WatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (prefs.address.isBlank() || prefs.token.isBlank()) return Result.success()
        try {
            val storm = withContext(Dispatchers.IO) { WeatherLabApi(prefs.address, prefs.token).storm() }
            StormWatch.update(applicationContext, storm, prefs.stormNotify)
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
