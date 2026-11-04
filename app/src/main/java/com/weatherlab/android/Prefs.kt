package com.weatherlab.android

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** The address and token, kept encrypted on the phone. */
class Prefs(context: Context) {
    private val prefs: SharedPreferences = open(context)

    private companion object {
        const val FILE = "weatherlab_prefs"

        private fun create(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
            context,
            FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        /**
         * The encrypted settings. If they can't be opened (0.7.26) - copied from an old phone without the key that
         * locks them, or a damaged key - they're cleared and made again, so the app asks for the address and token
         * once more instead of crashing on every start, along with the widget and background job.
         */
        fun open(context: Context): SharedPreferences = try {
            create(context)
        } catch (e: Exception) {
            context.deleteSharedPreferences(FILE)
            create(context)
        }
    }

    var address: String
        get() = prefs.getString("address", "").orEmpty()
        set(value) = prefs.edit().putString("address", value.trim()).apply()

    var token: String
        get() = prefs.getString("token", "").orEmpty()
        set(value) = prefs.edit().putString("token", value.trim()).apply()

    /** Colour scheme chosen in Settings: dark or light. */
    var scheme: String
        get() = prefs.getString("scheme", "dark").orEmpty()
        set(value) = prefs.edit().putString("scheme", value).apply()

    /** The storm countdown notification, on unless turned off in Settings (as on the phone app). */
    var stormNotify: Boolean
        get() = prefs.getBoolean("storm_notify", true)
        set(value) = prefs.edit().putBoolean("storm_notify", value).apply()

    /** Text size chosen in Settings: 1.0 normal, 1.15 large, 1.3 largest. Adds to the phone's own font size. */
    var textScale: Float
        get() = prefs.getFloat("text_scale", 1.0f)
        set(value) = prefs.edit().putFloat("text_scale", value).apply()

    /** App lock: -1 off, 0 every time the app is reopened, otherwise seconds away before it locks. */
    var lockAfter: Int
        get() = prefs.getInt("lock_after", -1)
        set(value) = prefs.edit().putInt("lock_after", value).apply()

    /** When on, the once-a-minute refresh waits until the app is on screen again. */
    var pauseInBackground: Boolean
        get() = prefs.getBoolean("pause_background", false)
        set(value) = prefs.edit().putBoolean("pause_background", value).apply()

    /** Keep a history of the readings on this phone (0.7.17). Off until turned on in Settings. */
    var offlineHistory: Boolean
        get() = prefs.getBoolean("offline_history", false)
        set(value) = prefs.edit().putBoolean("offline_history", value).apply()

    /** How many days of saved readings to keep: 30, 90 or 365. */
    var historyDays: Int
        get() = prefs.getInt("history_days", 90)
        set(value) = prefs.edit().putInt("history_days", value).apply()
}
