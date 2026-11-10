package com.weatherlab.android

import android.content.Context

/**
 * Which PCs accept signed requests (0.7.33). Once a PC has accepted one, this phone never falls back to sending the
 * token in the open to it, even if something on the network answers 401. Kept on the phone (not backed up).
 */
object SignMemory {
    private var prefs: android.content.SharedPreferences? = null
    private val oldPcs = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("sign_memory", Context.MODE_PRIVATE)
    }

    fun signedOk(base: String): Boolean = prefs?.getBoolean(base, false) ?: false

    fun markSignedOk(base: String) {
        oldPcs.remove(base)
        if (!signedOk(base)) prefs?.edit()?.putBoolean(base, true)?.apply()
    }

    /** Only for this run of the app, so updating Weather Lab on the PC is picked up at the next start. */
    fun oldPc(base: String): Boolean = base in oldPcs && !signedOk(base)

    fun markOldPc(base: String) {
        oldPcs.add(base)
    }
}
