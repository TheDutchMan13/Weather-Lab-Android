package com.weatherlab.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A watch-face complication: the temperature, with today's high under it. Tap to open the app. */
class TempComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) build("72°", "Hi 86°") else null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return NoDataComplicationData()
        val store = WatchStore(this)
        if (!store.configured) return NoDataComplicationData()
        return try {
            val d = withContext(Dispatchers.IO) { WatchApi.load(store.address, store.token) }
            build(d.tempText, d.high?.let { "Hi ${it.roundToInt()}°" })
        } catch (e: Exception) {
            NoDataComplicationData()
        }
    }

    private fun build(temp: String, title: String?): ComplicationData {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(temp).build(),
            PlainComplicationText.Builder("Temperature $temp").build(),
        ).setTapAction(open)
        if (title != null) builder.setTitle(PlainComplicationText.Builder(title).build())
        return builder.build()
    }
}
