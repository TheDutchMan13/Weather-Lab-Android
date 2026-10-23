package com.weatherlab.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** The address and token, kept encrypted on the phone. */
class Prefs(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "weatherlab_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var address: String
        get() = prefs.getString("address", "").orEmpty()
        set(value) = prefs.edit().putString("address", value.trim()).apply()

    var token: String
        get() = prefs.getString("token", "").orEmpty()
        set(value) = prefs.edit().putString("token", value.trim()).apply()

    /** Colour scheme chosen in Settings: dark, light, green or slate (same options as the AZ Weather Lab phone app). */
    var scheme: String
        get() = prefs.getString("scheme", "dark").orEmpty()
        set(value) = prefs.edit().putString("scheme", value).apply()

    /** The storm countdown notification, on unless turned off in Settings (as on the phone app). */
    var stormNotify: Boolean
        get() = prefs.getBoolean("storm_notify", true)
        set(value) = prefs.edit().putBoolean("storm_notify", value).apply()
}
