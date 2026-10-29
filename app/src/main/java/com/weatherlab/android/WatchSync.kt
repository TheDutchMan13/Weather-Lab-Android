package com.weatherlab.android

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** Hands the address and token to a paired Wear OS watch running the Weather Lab watch app. Harmless without one. */
object WatchSync {
    const val PATH = "/weatherlab/config"

    fun push(context: Context, address: String, token: String) {
        if (address.isBlank()) return
        try {
            val request = PutDataMapRequest.create(PATH).apply {
                dataMap.putString("address", address)
                dataMap.putString("token", token)
                dataMap.putLong("stamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context.applicationContext).putDataItem(request)
        } catch (_: Throwable) {
            // No Google Play services or no watch: nothing to do.
        }
    }
}
