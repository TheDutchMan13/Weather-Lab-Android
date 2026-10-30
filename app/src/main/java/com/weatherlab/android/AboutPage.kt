package com.weatherlab.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat

private const val SOURCE_URL = "https://github.com/TheDutchMan13/Weather-Lab-Android"

/** The app's own version, read from the installed package so it never drifts from the build. */
private fun versionText(context: Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "${info.versionName} (build ${PackageInfoCompat.getLongVersionCode(info)})"
} catch (_: Exception) {
    "--"
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** One row of the About page: an icon, a title, a line under it, and an optional smaller line. */
@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    sub: String,
    subColor: Color,
    first: Boolean = false,
    extra: String? = null,
    onClick: (() -> Unit)? = null,
) {
    if (!first) HorizontalDivider(thickness = 1.dp, color = C.line)
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = C.muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.heading, color = C.text)
            Spacer(Modifier.size(4.dp))
            Text(sub, style = T.body, color = subColor)
            if (extra != null) {
                Spacer(Modifier.size(2.dp))
                Text(extra, style = T.label, color = C.quiet)
            }
        }
    }
}

/** More, Settings, About: privacy, version, changelog, source code and licence. */
@Composable
fun AboutPage(pcVersion: String, onChangelog: () -> Unit) {
    val context = LocalContext.current
    PageColumn {
        AboutRow(
            Icons.Outlined.Lock, "Your data stays on your network",
            "Weather Lab reads your PC over home Wi-Fi or Tailscale. No account, no ads, no tracking. The access token is encrypted on this phone.",
            C.muted, first = true,
        )
        AboutRow(
            Icons.Outlined.RocketLaunch, "Version", versionText(context), C.muted,
            extra = if (pcVersion.isNotBlank()) "Weather Lab on your PC: $pcVersion" else null,
        )
        AboutRow(Icons.Outlined.AutoAwesome, "Changelog", "See what's new in Weather Lab", C.muted, onClick = onChangelog)
        AboutRow(Icons.Outlined.Code, "Source code", "See on GitHub", C.cyan, onClick = { openUrl(context, SOURCE_URL) })
        AboutRow(
            Icons.Outlined.Copyright, "Licence", "GNU General Public License v3.0 or later", C.muted,
            extra = "\u00A9 2026 Hans De Kryger",
        )
    }
}
