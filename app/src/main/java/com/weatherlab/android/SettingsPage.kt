package com.weatherlab.android

import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first

// Settings (moved out of MainActivity.kt in 0.7.30), with the "On your watch" section.

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsPage(
    prefs: Prefs,
    address: String,
    token: String,
    scheme: Scheme,
    snapshot: Snapshot?,
    onSave: (String, String) -> Unit,
    onScheme: (Scheme) -> Unit,
    textScale: Float,
    onTextScale: (Float) -> Unit,
    stormOn: Boolean,
    onStorm: (Boolean) -> Unit,
    pauseBg: Boolean,
    onPauseBg: (Boolean) -> Unit,
    keepHistory: Boolean,
    onKeepHistory: (Boolean) -> Unit,
    historyDays: Int,
    onHistoryDays: (Int) -> Unit,
    lockAfter: Int,
    onLockAfter: (Int) -> Unit,
) {
    val context = LocalContext.current
    var watchTick by remember { mutableIntStateOf(0) }
    var lockNote by remember { mutableStateOf<String?>(null) }
    var codeText by remember { mutableStateOf("") }
    var codeNote by remember { mutableStateOf<String?>(null) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var a by remember { mutableStateOf(address) }
    var t by remember { mutableStateOf(token) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = C.text, unfocusedTextColor = C.text,
        focusedBorderColor = C.cyan, unfocusedBorderColor = C.line,
        focusedLabelColor = C.cyan, unfocusedLabelColor = C.muted,
        cursorColor = C.cyan,
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        FoldSection(
            Icons.Outlined.Link, "Connection",
            if (address.isBlank() || token.isBlank()) "Not connected yet" else "Connected to $address",
            startOpen = address.isBlank() || token.isBlank(),
        ) {
            Column {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The address is shown in Weather Lab's Settings under Android app, for example 192.168.1.50:8600. " +
                        "The token is the one you copied there.",
                    style = T.label, color = C.muted,
                )
                OutlinedTextField(
                    value = a, onValueChange = { a = it }, label = { Text("Address") },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
                addressWarning(a)?.let { Note(it, C.warn) }
                // Hidden like a password, and the keyboard is asked not to learn it (0.7.26).
                OutlinedTextField(
                    value = t, onValueChange = { t = it }, label = { Text("Token") },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Button(
                    onClick = { onSave(a, t) },
                    enabled = a.isNotBlank() && t.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
                ) { Text("Save and connect") }
            }
        
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = C.line)
                Text("Move to a new phone", style = T.body, color = C.text)
                Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "A setup code holds your address and token. Copy it here, send it to yourself, and paste it on the new phone. " +
                        "Treat it like a password.",
                    style = T.label, color = C.muted,
                )
                Button(
                    onClick = {
                        if (a.isNotBlank() && t.isNotBlank()) {
                            copyText(context, "Weather Lab setup code", SetupCode.make(a, t), sensitive = true)
                            codeNote = "Setup code copied."
                        } else {
                            codeNote = "Fill in the address and token first."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.card, contentColor = C.text),
                ) { Text("Copy setup code") }
                OutlinedTextField(
                    value = codeText, onValueChange = { codeText = it }, label = { Text("Paste a setup code") },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val found = SetupCode.read(codeText)
                        if (found == null) {
                            codeNote = "That isn't a Weather Lab setup code."
                        } else {
                            a = found.first
                            t = found.second
                            codeText = ""
                            codeNote = "Address and token filled in. Tap Save and connect above."
                        }
                    },
                    enabled = codeText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg),
                ) { Text("Use this code") }
                codeNote?.let { Text(it, style = T.label, color = C.muted) }
            }
        }
        }
        FoldSection(Icons.Outlined.Notifications, "Notifications", if (stormOn) "Storm countdown on" else "Storm countdown off", startOpen = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Storm countdown", style = T.body, color = C.text)
                    Text(
                        "Shows a notification while lightning is near. Checked about every 15 minutes in the background.",
                        style = T.label, color = C.muted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = stormOn,
                    onCheckedChange = { on ->
                        onStorm(on)
                        if (on && Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.cyan, checkedThumbColor = C.bg),
                )
            }
        }
        FoldSection(
            Icons.Outlined.Watch, "On your watch",
            run {
                watchTick // read so the summary follows the switches inside
                if (!prefs.watchAlerts) "Alerts off"
                else if (prefs.watchQuietOn) "Alerts on · quiet ${hourText(prefs.watchQuietStart)} to ${hourText(prefs.watchQuietEnd)}"
                else "Alerts on"
            },
        ) { WatchSettings(prefs) { watchTick++ } }
        FoldSection(Icons.Outlined.Palette, "Appearance", "${scheme.label} · ${when (textScale) { 1.15f -> "large text"; 1.3f -> "largest text"; else -> "normal text" }}", startOpen = false) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Scheme.options().forEach { s ->
                    FilterChip(
                        selected = scheme == s,
                        onClick = { onScheme(s) },
                        label = { Text(s.label, color = C.text) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = C.card,
                            selectedContainerColor = C.cyan.copy(alpha = 50 / 255f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = scheme == s,
                            borderColor = C.line, selectedBorderColor = C.cyan,
                        ),
                    )
                }
            }
            if (scheme == Scheme.WALLPAPER) {
                Text(
                    "Colours come from your wallpaper and follow your phone's dark mode. Warning colours stay the same.",
                    style = T.label, color = C.muted,
                )
            }
            Text("Text size", style = T.body, color = C.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Normal" to 1.0f, "Large" to 1.15f, "Largest" to 1.3f).forEach { (name, size) ->
                    FilterChip(
                        selected = textScale == size,
                        onClick = { onTextScale(size) },
                        label = { Text(name, color = C.text) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = C.card,
                            selectedContainerColor = C.cyan.copy(alpha = 50 / 255f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = textScale == size,
                            borderColor = C.line, selectedBorderColor = C.cyan,
                        ),
                    )
                }
            }
            Text(
                "Makes all text in the app bigger. It adds to the font size set in your phone settings.",
                style = T.label, color = C.muted,
            )
            }
        }
        FoldSection(Icons.Outlined.Lock, "Security and battery", "App lock ${(AppLock.CHOICES.firstOrNull { it.second == lockAfter }?.first ?: "Off").lowercase()} · ${if (pauseBg) "background refresh paused" else "refreshes in the background"}", startOpen = false) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("App lock", style = T.body, color = C.text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLock.CHOICES.forEach { (name, seconds) ->
                        FilterChip(
                            selected = lockAfter == seconds,
                            onClick = {
                                if (seconds >= 0 && !AppLock.deviceHasLock(context)) {
                                    lockNote = "Set a screen lock (PIN, pattern, password or fingerprint) in your phone's settings first."
                                } else {
                                    lockNote = if (seconds >= 0) "Takes effect the next time the app starts." else null
                                    onLockAfter(seconds)
                                }
                            },
                            label = { Text(name, color = C.text) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = C.card,
                                selectedContainerColor = C.cyan.copy(alpha = 50 / 255f),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = lockAfter == seconds,
                                borderColor = C.line, selectedBorderColor = C.cyan,
                            ),
                        )
                    }
                }
                Text(
                    "Uses your phone's own fingerprint, face, PIN, pattern or password. It also hides the app in the recent apps list.",
                    style = T.label, color = C.muted,
                )
                lockNote?.let { Text(it, style = T.label, color = C.warn) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pause refreshing in the background", style = T.body, color = C.text)
                        Text(
                            "Home stops updating while the app isn't on screen and catches up when you return. " +
                                "The storm countdown above has its own switch.",
                            style = T.label, color = C.muted,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = pauseBg, onCheckedChange = onPauseBg,
                        colors = SwitchDefaults.colors(checkedTrackColor = C.cyan, checkedThumbColor = C.bg),
                    )
                }
            }
        }
        FoldSection(Icons.Outlined.History, "History on this phone", if (!keepHistory) "Off" else "Keeping ${when (historyDays) { 30 -> "30 days"; 90 -> "90 days"; else -> "1 year" }}", startOpen = false) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep history on this phone", style = T.body, color = C.text)
                        Text(
                            "Saves the readings the phone receives, one every 5 minutes, so History, On this phone still shows days and a 24-hour line when your PC can't be reached. Off by default.",
                            style = T.label, color = C.muted,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = keepHistory, onCheckedChange = onKeepHistory,
                        colors = SwitchDefaults.colors(checkedTrackColor = C.cyan, checkedThumbColor = C.bg),
                    )
                }
                if (keepHistory) {
                    Text("Keep for", style = T.body, color = C.text)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30 to "30 days", 90 to "90 days", 365 to "1 year").forEach { (days, name) ->
                            FilterChip(
                                selected = historyDays == days,
                                onClick = { onHistoryDays(days) },
                                label = { Text(name, color = C.text) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = C.card,
                                    selectedContainerColor = C.cyan.copy(alpha = 50 / 255f),
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true, selected = historyDays == days,
                                    borderColor = C.line, selectedBorderColor = C.cyan,
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (snapshot != null) {
            FoldSection(
                Icons.Outlined.Info, "Station",
                listOf(snapshot.status.station.ifBlank { null }, snapshot.status.appVersion.ifBlank { null }?.let { "Weather Lab $it" },
                    if (snapshot.current.metric) "Metric" else "US").filterNotNull().joinToString(" · "),
            ) {
            ListPanel(
                listOf(
                    { InfoRow("Station", snapshot.status.station.ifBlank { "--" }) },
                    { InfoRow("Weather Lab version", snapshot.status.appVersion.ifBlank { "--" }) },
                    { InfoRow("Units", if (snapshot.current.metric) "Metric" else "US") },
                ),
            )
            }
        }
    }
}

private fun hourText(h: Int): String = when {
    h == 0 -> "12 am"
    h < 12 -> "$h am"
    h == 12 -> "12 pm"
    else -> "${h - 12} pm"
}

/** One folding group of settings (0.7.30): a row with an icon, the title and a one-line summary; tap to open or close. */
@Composable
private fun FoldSection(icon: ImageVector, title: String, summary: String, startOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    Spacer(Modifier.height(10.dp))
    Panel {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(S.sm))
                    .clickable(onClickLabel = if (open) "Close $title" else "Open $title") { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = C.muted)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = T.heading, color = C.text)
                    Text(summary, style = T.label, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Open" else "Closed", tint = C.muted,
                )
            }
            AnimatedVisibility(open) { Column(Modifier.padding(top = 14.dp)) { content() } }
        }
    }
}

/** Settings > On your watch (0.7.28): alerts on the watch, and its quiet hours. Sent to the watch on every change. */
@Composable
private fun WatchSettings(prefs: Prefs, onChanged: () -> Unit) {
    val context = LocalContext.current
    var alertsOn by remember { mutableStateOf(prefs.watchAlerts) }
    var quietOn by remember { mutableStateOf(prefs.watchQuietOn) }
    var start by remember { mutableIntStateOf(prefs.watchQuietStart) }
    var end by remember { mutableIntStateOf(prefs.watchQuietEnd) }
    val save = {
        prefs.watchAlerts = alertsOn
        prefs.watchQuietOn = quietOn
        prefs.watchQuietStart = start
        prefs.watchQuietEnd = end
        WatchSync.pushSettings(context)
        StormWatch.schedule(context)
        onChanged()
    }
    run {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Storm and alert notifications", style = T.body, color = C.text)
                    Text(
                        "Sends lightning and Weather Lab alerts to the Weather Lab watch app, with a short buzz.",
                        style = T.label, color = C.muted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = alertsOn, onCheckedChange = { alertsOn = it; save() },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.cyan, checkedThumbColor = C.bg),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quiet hours", style = T.body, color = C.text)
                    Text("Alerts still show on the watch, without the buzz.", style = T.label, color = C.muted)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = quietOn, onCheckedChange = { quietOn = it; save() },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.cyan, checkedThumbColor = C.bg),
                )
            }
            if (quietOn) {
                HourStepper("From", start) { start = it; save() }
                HourStepper("Until", end) { end = it; save() }
            }
        }
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.body, color = C.muted, modifier = Modifier.width(64.dp))
        TextButton(onClick = { onChange((hour + 23) % 24) }) { Text("−", color = C.cyan, fontSize = 20.sp) }
        Text(hourText(hour), style = T.body, color = C.text, modifier = Modifier.width(64.dp))
        TextButton(onClick = { onChange((hour + 1) % 24) }) { Text("+", color = C.cyan, fontSize = 20.sp) }
    }
}
