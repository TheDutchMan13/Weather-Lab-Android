package com.weatherlab.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import java.io.ByteArrayOutputStream

// Shared by both tiles (0.7.28): text lines, an optional small graph, and tap to open the app.

const val WHITE = 0xFFFFFFFF.toInt()
const val CYAN = 0xFF5CC8E8.toInt()
const val MUTED = 0xFF8A97A6.toInt()
const val AMBER = 0xFFF2B34A.toInt()
const val RED = 0xFFEF6B5B.toInt()

/** One line of a tile. A line with [spark] true is the 24-hour graph instead of text. */
data class TileLine(val text: String, val size: Float, val color: Int = WHITE, val bold: Boolean = false, val spark: Boolean = false)

const val SPARK_ID = "spark"
private const val SPARK_W = 120
private const val SPARK_H = 26

/** A small line graph of [points] as a PNG, drawn in [color]. Empty when there are fewer than two points. */
fun sparkPng(points: List<Double>, color: Int = CYAN, w: Int = SPARK_W * 2, h: Int = SPARK_H * 2): ByteArray {
    if (points.size < 2) return ByteArray(0)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val lo = points.min()
    val span = (points.max() - lo).takeIf { it > 0.0 } ?: 1.0
    val pad = 4f
    val path = Path()
    points.forEachIndexed { i, v ->
        val x = pad + (w - 2 * pad) * i / (points.size - 1)
        val y = h - pad - ((v - lo) / span).toFloat() * (h - 2 * pad)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    canvas.drawPath(path, paint)
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    bmp.recycle()
    return out.toByteArray()
}

/** The resources a tile needs: the graph image when there is one. */
fun tileResources(version: String, spark: List<Double>): ResourceBuilders.Resources {
    val builder = ResourceBuilders.Resources.Builder().setVersion(version)
    val png = sparkPng(spark)
    if (png.isNotEmpty()) {
        builder.addIdToImageMapping(
            SPARK_ID,
            ResourceBuilders.ImageResource.Builder()
                .setInlineResource(
                    ResourceBuilders.InlineImageResource.Builder()
                        .setData(png)
                        .setWidthPx(SPARK_W * 2)
                        .setHeightPx(SPARK_H * 2)
                        .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                        .build(),
                )
                .build(),
        )
    }
    return builder.build()
}

/** A resources version that changes when the graph changes, so the watch asks for the new image. */
fun sparkVersion(spark: List<Double>): String = "s" + spark.joinToString(",") { "%.1f".format(it) }.hashCode()

fun tileLayout(context: Context, lines: List<TileLine>): LayoutElementBuilders.LayoutElement {
    val column = LayoutElementBuilders.Column.Builder()
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
    lines.forEach { line ->
        if (line.spark) {
            column.addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(SPARK_ID)
                    .setWidth(dp(SPARK_W.toFloat()))
                    .setHeight(dp(SPARK_H.toFloat()))
                    .build(),
            )
        } else {
            column.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(line.text)
                    .setMaxLines(2)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(line.size))
                            .setColor(argb(line.color))
                            .setWeight(if (line.bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                            .build(),
                    )
                    .build(),
            )
        }
    }
    val open = ModifiersBuilders.Clickable.Builder()
        .setId("open")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build(),
                )
                .build(),
        )
        .build()
    return LayoutElementBuilders.Box.Builder()
        .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .addContent(column.build())
        .build()
}

/** "1 min ago", "12 min ago". */
fun ago(seconds: Int): String = if (seconds < 90) "1 min ago" else "${seconds / 60} min ago"
