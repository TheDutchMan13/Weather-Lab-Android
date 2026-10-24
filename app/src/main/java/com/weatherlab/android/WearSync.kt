package com.weatherlab.android

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * Sends a short summary to a paired Wear OS watch so its tiles can show it. The watch never talks to your PC itself.
 * Without a watch (or without Google Play services) this quietly does nothing.
 */
object WearSync {
    const val PATH = "/weatherlab/summary"

    fun send(context: Context, s: Snapshot) {
        try {
            val v = s.current.values
            val gust = v["windgustmph"]?.let { if (it.unit.contains("km")) it.value / 1.609344 else it.value }
            val kind = skyKind(v["rainratein"]?.value, gust, LocalDateTime.now().hour)
            val note = StormWatch.describe(s.storm)
            val json = JSONObject()
                .put("temp", v["tempf"]?.let { "${Math.round(it.value)}°" } ?: "--°")
                .put("cond", conditionWord(kind))
                .put("hi", s.today.high?.let { "${Math.round(it.value)}°" } ?: "--")
                .put("lo", s.today.low?.let { "${Math.round(it.value)}°" } ?: "--")
                .put("hum", v["humidity"]?.let { "${Math.round(it.value)}%" } ?: "--")
                .put("wind", v["windspeedmph"]?.let { "${Math.round(it.value)} ${it.unit}" } ?: "--")
                .put("uv", v["uv"]?.let { Math.round(it.value).toString() } ?: "--")
                .put("rain", s.today.rain?.let { "${"%.2f".format(it.value)} ${it.unit}" } ?: "--")
                .put("storm_title", note?.title ?: "")
                .put("storm_line", note?.text ?: "")
                .put("sent", System.currentTimeMillis())
            val request = PutDataMapRequest.create(PATH)
            request.dataMap.putString("json", json.toString())
            request.dataMap.putLong("time", System.currentTimeMillis())
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
        } catch (_: Exception) {
            // No watch, or no Google Play services. Nothing to do.
        }
    }
}
