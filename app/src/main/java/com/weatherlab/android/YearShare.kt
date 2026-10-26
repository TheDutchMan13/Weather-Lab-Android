package com.weatherlab.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

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

/** Draws a share card as a 1080 x 1350 picture: a small heading, a big title and up to six figures. */
internal fun cardBitmap(kicker: String, title: String, titleSize: Float, facts: List<YearFact>): Bitmap {
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
    canvas.drawText(kicker, 72f, 105f, paint(34f, "#B9D2F2", true))
    canvas.drawText(title, 72f, 290f, paint(titleSize, "#FFFFFF", true))
    val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#13508F") }
    val top = 410f
    val gap = 14f
    val rowH = (h - 150f - top - gap * 5) / 6
    facts.take(6).forEachIndexed { i, f ->
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
internal fun shareBitmap(context: Context, bitmap: Bitmap, fileName: String, chooserTitle: String) {
    val folder = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(folder, fileName)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

internal fun shareYearCard(context: Context, c: YearCard) =
    shareBitmap(context, cardBitmap("MY WEATHER YEAR", c.year.toString(), 190f, yearFacts(c)), "my-weather-year-${c.year}.png", "Share ${c.year} in review")

/** The lines for one day's picture, from the figures Weather Lab keeps for that day. */
internal fun dayFacts(d: DayDetail): List<YearFact> {
    fun v(label: String, decimals: Int = 0): String? =
        d.values.firstOrNull { it.label == label && it.value != null }?.let { x ->
            val unit = x.unit
            val text = num(x.value!!, decimals)
            if (unit.startsWith("°") || unit == "%") text + unit else if (unit.isBlank()) text else "$text $unit"
        }
    return listOfNotNull(
        v("High")?.let { YearFact("High", it, "") },
        v("Low")?.let { YearFact("Low", it, "") },
        v("Heat index high")?.let { YearFact("Heat index high", it, "") },
        v("Highest gust")?.let { YearFact("Highest gust", it, "") },
        v("Rain", 2)?.let { YearFact("Rain", it, "") },
        v("UV index", 1)?.let { YearFact("UV index", it, "") },
    )
}

internal fun shareDayCard(context: Context, d: DayDetail) {
    val title = runCatching { LocalDate.parse(d.date).let { "${it.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())}, ${dayText(d.date)}" } }.getOrDefault(d.date)
    shareBitmap(context, cardBitmap("MY WEATHER DAY", title, 110f, dayFacts(d)), "my-weather-day-${d.date}.png", "Share $title")
}

/** The lines for one month's picture. Days at 100°F come from the daily highs the month card already carries. */
internal fun monthFacts(c: MonthCard): List<YearFact> {
    val out = mutableListOf<YearFact>()
    val hottest = c.hottest
    if (hottest?.value != null) out += YearFact("Hottest day", num(hottest.value, 0) + c.tempUnit, dayText(hottest.date))
    val limit = if (c.tempUnit.contains("C")) 38.0 else 100.0
    val hot = c.daily.count { (it.high ?: Double.NEGATIVE_INFINITY) >= limit }
    out += YearFact("Days at ${if (c.tempUnit.contains("C")) "38°C" else "100°F"} or more", hot.toString(), "")
    c.averageHigh?.let { out += YearFact("Average high", num(it, 0) + c.tempUnit, "") }
    val wettest = c.wettest
    if (wettest?.value != null && wettest.value > 0.0) out += YearFact("Wettest day", num(wettest.value, 2) + " " + c.rainUnit, dayText(wettest.date))
    c.rainTotal?.let { out += YearFact("Rain this month", num(it, 2) + " " + c.rainUnit, "${c.rainyDays} rainy days") }
    val gust = c.windiest
    if (gust?.value != null) out += YearFact("Highest gust", num(gust.value, 0) + " " + c.windUnit, dayText(gust.date))
    return out
}

internal fun shareMonthCard(context: Context, c: MonthCard) {
    val title = runCatching { YearMonth.parse(c.month).let { "${it.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} ${it.year}" } }.getOrDefault(c.month)
    shareBitmap(context, cardBitmap("MY WEATHER MONTH", title, 100f, monthFacts(c)), "my-weather-month-${c.month}.png", "Share $title")
}

/** A "Share ..." button that makes a picture and opens the share sheet, with an error line if that fails. */
@Composable
internal fun ShareButton(label: String, make: (Context) -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { runCatching { make(context) }.onFailure { error = it.message ?: "Couldn't share the picture." } },
        colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
    ) { Text(label) }
    error?.let {
        Spacer(Modifier.height(10.dp))
        Note(it, C.bad)
    }
}
