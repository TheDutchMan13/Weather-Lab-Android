package com.weatherlab.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One line in the notices strip on Live. urgent: opens the strip on its own, as a Severe warning does on the PC. */
internal data class LiveNotice(val text: String, val bad: Boolean, val urgent: Boolean = false)

/**
 * What Live has to say besides the readings (0.7.24): a lightning storm close by, data that is more than 10 minutes
 * old, and a problem reaching Weather Lab that isn't the offline case (that one has its own card with Try again).
 */
internal fun liveNotices(s: Snapshot, error: String?, offline: Boolean): List<LiveNotice> {
    val out = mutableListOf<LiveNotice>()
    s.storm?.let { st ->
        if (st.trend == "approaching" || st.trend == "overhead") {
            val away = st.last?.let { "${"%.1f".format(it.distance.value)} ${it.distance.unit}" }
            val text = if (st.trend == "overhead") "Lightning overhead." else "Lightning approaching."
            out += LiveNotice(if (away == null) text else "$text Last strike $away away.", bad = true, urgent = true)
        }
    }
    val age = s.current.ageSeconds ?: s.status.ageSeconds
    if (!offline && age != null && age > 600) {
        out += LiveNotice("Station data is ${Math.round(age / 60.0)} minutes old.", bad = false)
    }
    if (error != null && !offline) out += LiveNotice(error, bad = false)
    return out
}

/** "1 notice" / "3 notices", as on the PC's Home. */
internal fun noticeLabel(count: Int): String = if (count == 1) "1 notice" else "$count notices"

/**
 * The notices strip under the hero, like the PC's Home (0.7.24): one line saying how many notices there are, which
 * opens to show them. It opens by itself when the PC can't be reached or lightning is close, and is hidden when
 * there is nothing to say.
 */
@Composable
internal fun NoticesStrip(s: Snapshot, error: String?, offlineSince: Long?, onRetry: () -> Unit) {
    val offline = error != null && offlineSince != null
    val notes = liveNotices(s, error, offline)
    val count = notes.size + if (offline) 1 else 0
    if (count == 0) return
    val urgent = offline || notes.any { it.urgent }
    var open by rememberSaveable(urgent) { mutableStateOf(urgent) }
    val tint = when {
        notes.any { it.bad } -> C.bad
        offline || notes.isNotEmpty() -> C.warn
        else -> C.muted
    }
    Panel(Modifier.padding(bottom = 10.dp)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (open) "Hide notices" else "Show notices") { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Notifications, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(noticeLabel(count), fontSize = 15.sp, fontWeight = FontWeight.W700, color = C.text, modifier = Modifier.weight(1f))
                Icon(
                    if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null, tint = C.muted,
                )
            }
            if (open) {
                Spacer(Modifier.height(10.dp))
                if (offline) OfflineBanner(s, offlineSince!!, onRetry)
                notes.forEach { Note(it.text, if (it.bad) C.bad else C.warn) }
            }
        }
    }
}
