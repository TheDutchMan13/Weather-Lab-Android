package com.weatherlab.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// Ported from the AZ Weather Lab phone app's theme.dart (read only reference): same colours, same type scale.
// Two looks: navy Dark and Light.

enum class Scheme(val key: String, val label: String) {
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        fun from(key: String?): Scheme = entries.firstOrNull { it.key == key } ?: DARK
    }
}

class Pal(
    val light: Boolean,
    val bg: Color, val card: Color, val line: Color, val bar: Color,
    val cyan: Color, val warn: Color, val bad: Color, val good: Color,
    val muted: Color, val text: Color, val quiet: Color,
)

private fun c(v: Long) = Color(v or 0xFF000000L)

private val Light = Pal(
    light = true,
    bg = c(0xF2F4FA), card = c(0xFFFFFF), line = c(0xD8DDEB), bar = c(0xBCC4DE),
    cyan = c(0x2563EB), warn = c(0x9A5B00), bad = c(0xB3261E), good = c(0x1B6E3E),
    muted = c(0x4A5478), text = c(0x141A33), quiet = c(0x6E78AE),
)
private val Navy = Pal(
    light = false,
    bg = c(0x0A0E27), card = c(0x141A3D), line = c(0x1C2450), bar = c(0x2A3470),
    cyan = c(0x58A6FF), warn = c(0xFFB020), bad = c(0xFF9A9A), good = c(0x5EE0A0),
    muted = c(0xB0B9DD), text = c(0xE8ECFF), quiet = c(0x6E78AE),
)

fun palette(s: Scheme): Pal = when (s) {
    Scheme.LIGHT -> Light
    Scheme.DARK -> Navy
}

val LocalPal = staticCompositionLocalOf { Navy }

/** Short way to read the colours: C.card, C.text and so on, as in the phone app. */
object C {
    val bg: Color @Composable @ReadOnlyComposable get() = LocalPal.current.bg
    val card: Color @Composable @ReadOnlyComposable get() = LocalPal.current.card
    val line: Color @Composable @ReadOnlyComposable get() = LocalPal.current.line
    val bar: Color @Composable @ReadOnlyComposable get() = LocalPal.current.bar
    val cyan: Color @Composable @ReadOnlyComposable get() = LocalPal.current.cyan
    val warn: Color @Composable @ReadOnlyComposable get() = LocalPal.current.warn
    val bad: Color @Composable @ReadOnlyComposable get() = LocalPal.current.bad
    val good: Color @Composable @ReadOnlyComposable get() = LocalPal.current.good
    val muted: Color @Composable @ReadOnlyComposable get() = LocalPal.current.muted
    val text: Color @Composable @ReadOnlyComposable get() = LocalPal.current.text
    val quiet: Color @Composable @ReadOnlyComposable get() = LocalPal.current.quiet
}

// Manrope is a variable font, so one file gives every weight the phone app uses.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Manrope = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(
            R.font.manrope,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

private const val TNUM = "tnum" // figures use equal-width digits so columns line up

/** One type scale for the whole app, same sizes and weights as T in theme.dart. */
object T {
    val title = TextStyle(fontFamily = Manrope, fontSize = 18.sp, fontWeight = FontWeight.W800)
    val heading = TextStyle(fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.W700)
    val body = TextStyle(fontFamily = Manrope, fontSize = 14.sp, fontWeight = FontWeight.W600)
    val note = TextStyle(fontFamily = Manrope, fontSize = 13.sp, fontWeight = FontWeight.W600)
    val label = TextStyle(fontFamily = Manrope, fontSize = 12.sp, fontWeight = FontWeight.W400)
    val section = TextStyle(fontFamily = Manrope, fontSize = 15.sp, fontWeight = FontWeight.W800)
    val pill = TextStyle(fontFamily = Manrope, fontSize = 12.sp, fontWeight = FontWeight.W700)
    val value = TextStyle(fontFamily = Manrope, fontSize = 21.sp, fontWeight = FontWeight.W800, fontFeatureSettings = TNUM)
    val figure = TextStyle(fontFamily = Manrope, fontSize = 15.sp, fontWeight = FontWeight.W800, fontFeatureSettings = TNUM)
    // lineHeight equals the font size (Flutter height 1.0), so the big
    // temperature sits as tight as it does in the phone app.
    val hero = TextStyle(
        fontFamily = Manrope, fontSize = 76.sp, fontWeight = FontWeight.W800, fontFeatureSettings = TNUM,
        lineHeight = 76.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
}

private fun typography(): Typography {
    val d = Typography()
    fun f(s: TextStyle) = s.copy(fontFamily = Manrope, fontFeatureSettings = TNUM)
    return d.copy(
        displayLarge = f(d.displayLarge), displayMedium = f(d.displayMedium), displaySmall = f(d.displaySmall),
        headlineLarge = f(d.headlineLarge), headlineMedium = f(d.headlineMedium), headlineSmall = f(d.headlineSmall),
        titleLarge = f(d.titleLarge), titleMedium = f(d.titleMedium), titleSmall = f(d.titleSmall),
        bodyLarge = f(d.bodyLarge), bodyMedium = f(d.bodyMedium), bodySmall = f(d.bodySmall),
        labelLarge = f(d.labelLarge), labelMedium = f(d.labelMedium), labelSmall = f(d.labelSmall),
    )
}

private fun colorScheme(p: Pal): ColorScheme =
    if (p.light) lightColorScheme(primary = p.cyan, surface = p.card, background = p.bg, onSurface = p.text, onBackground = p.text, error = p.bad)
    else darkColorScheme(primary = p.cyan, surface = p.card, background = p.bg, onSurface = p.text, onBackground = p.text, error = p.bad, onPrimary = p.bg)

@Composable
fun WeatherLabTheme(scheme: Scheme, content: @Composable () -> Unit) {
    val p = palette(scheme)
    CompositionLocalProvider(LocalPal provides p) {
        MaterialTheme(colorScheme = colorScheme(p), typography = typography(), content = content)
    }
}

@Composable
fun navItemColors() = NavigationBarItemDefaults.colors(
    indicatorColor = C.cyan.copy(alpha = 50 / 255f),
    // Same as the phone app: the selected icon is black on the tinted pill, everything else is near white.
    selectedIconColor = Color.Black,
    selectedTextColor = C.text,
    unselectedIconColor = C.text,
    unselectedTextColor = C.text,
)
