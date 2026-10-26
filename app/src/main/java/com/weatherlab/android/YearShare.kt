package com.weatherlab.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

/** One line of the year card: what it is, the figure, and a small note on the right. */
internal data class YearFact(val label: String, val value: String, val note: String)

private fun dayText(iso: String): String =
    runCatching { LocalDate.parse(iso).let { "${it.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())} ${it.dayOfMonth}" } }.getOrDefault(iso)

private fun num(v: Double, decimals: Int): String = if (decimals == 0) "%.0f".format(v) else "%.${decimals}f".format(v).trimEnd('0').trimEnd('.')

/** The lines on the share picture, in the same order as the Weather Lab PC version. Anything the year doesn't have is skipped. */
internal fun yearFacts(c: YearCard): List<YearFact> {
    val out = mutableListOf<YearFact>()
    val hottest = c.hottest
    if (hottest?.value != null) out += YearFact("Hottest day", num(hottest.value, 0) + c.tempUnit, dayText(hottest.date))
    val hot = if (c.tempUnit.contains("C")) "38°C" else "100°F"
    out += YearFact("Days at $hot or more", c.days100.toString(), "")
    val streak = c.longestHot
    if (streak != null) out += YearFact("Longest hot streak", "${streak.days} days", "${dayText(streak.from)} to ${dayText(streak.to)}")
    val wettest = c.wettest
    if (wettest?.value != null) out += YearFact("Wettest day", num(wettest.value, 2) + " " + c.rainUnit, dayText(wettest.date))
    val rain = c.rain
    if (rain != null) out += YearFact("Rain this year", num(rain, 1) + " " + c.rainUnit, "${c.rainyDays} rainy days")
    val gust = c.windiest
    if (gust?.value != null) out += YearFact("Highest gust", num(gust.value, 0) + " " + c.windUnit, dayText(gust.date))
    return out
}

/** Draws the card as a 1080 x 1350 picture. */
internal fun yearBitmap(c: YearCard): Bitmap {
    val w = 1080
    val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.parseColor("#0B3D91"))
    fun paint(size: Float, color: String, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = Color.parseColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    canvas.drawText("MY WEATHER YEAR", 72f, 105f, paint(34f, "#B9D2F2", true))
    canvas.drawText(c.year.toString(), 72f, 290f, paint(190f, "#FFFFFF", true))
    val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#13508F") }
    val top = 410f
    val gap = 14f
    val rowH = (h - 150f - top - gap * 5) / 6
    yearFacts(c).take(6).forEachIndexed { i, f ->
        val y = top + i * (rowH + gap)
        canvas.drawRoundRect(RectF(72f, y, w - 72f, y + rowH), 26f, 26f, panel)
        canvas.drawText(f.label, 108f, y + 46f, paint(30f, "#B9D2F2"))
        canvas.drawText(f.value, 108f, y + rowH - 22f, paint(54f, if (i == 0) "#FFD166" else "#FFFFFF", true))
        if (f.note.isNotBlank()) {
            val p = paint(30f, "#B9D2F2").apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(f.note, w - 108f, y + rowH - 28f, p)
        }
    }
    canvas.drawText("Made with Weather Lab", 72f, h - 62f, paint(30f, "#B9D2F2"))
    return bmp
}

/** Saves the picture in the app's cache and opens the share sheet. */
internal fun shareYearCard(context: Context, c: YearCard) {
    val folder = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(folder, "my-weather-year-${c.year}.png")
    file.outputStream().use { yearBitmap(c).compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share ${c.year} in review"))
}
