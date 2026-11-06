package com.weatherlab.wear

import android.content.Context
import java.time.LocalTime

/** The address, token and quiet hours, kept on the watch. They arrive from the phone app (see ConfigSync). */
class WatchStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("weatherlab_watch", Context.MODE_PRIVATE)

    var address: String
        get() = prefs.getString("address", "").orEmpty()
        set(value) = prefs.edit().putString("address", value.trim()).apply()

    var token: String
        get() = prefs.getString("token", "").orEmpty()
        set(value) = prefs.edit().putString("token", value.trim()).apply()

    val configured: Boolean get() = address.isNotBlank() && token.isNotBlank()

    // Quiet hours (0.7.28): alerts still show, without a buzz. Set on the phone, in Settings > On your watch.
    var quietOn: Boolean
        get() = prefs.getBoolean("quiet_on", true)
        set(value) = prefs.edit().putBoolean("quiet_on", value).apply()

    var quietStart: Int
        get() = prefs.getInt("quiet_start", 22)
        set(value) = prefs.edit().putInt("quiet_start", value.coerceIn(0, 23)).apply()

    var quietEnd: Int
        get() = prefs.getInt("quiet_end", 7)
        set(value) = prefs.edit().putInt("quiet_end", value.coerceIn(0, 23)).apply()

    fun isQuiet(now: LocalTime = LocalTime.now()): Boolean = quietOn && inQuiet(now.hour, quietStart, quietEnd)

    companion object {
        /** True when the hour falls in quiet hours. 22 to 7 crosses midnight; equal start and end means never. */
        fun inQuiet(hour: Int, start: Int, end: Int): Boolean = when {
            start == end -> false
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }
}
