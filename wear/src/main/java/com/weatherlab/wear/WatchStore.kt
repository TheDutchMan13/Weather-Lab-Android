package com.weatherlab.wear

import android.content.Context

/** The address and token, kept on the watch. They arrive from the phone app (see ConfigSync). */
class WatchStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("weatherlab_watch", Context.MODE_PRIVATE)

    var address: String
        get() = prefs.getString("address", "").orEmpty()
        set(value) = prefs.edit().putString("address", value.trim()).apply()

    var token: String
        get() = prefs.getString("token", "").orEmpty()
        set(value) = prefs.edit().putString("token", value.trim()).apply()

    val configured: Boolean get() = address.isNotBlank() && token.isNotBlank()
}
