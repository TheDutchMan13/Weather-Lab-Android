package com.weatherlab.android

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow

/** Whether the app is on screen, so refreshing can pause while it is in the background. */
object AppState {
    val foreground = MutableStateFlow(true)
}

/**
 * App lock uses the phone's own screen lock (fingerprint, face, PIN, pattern or password), so there is no separate
 * code to remember. lockAfter in Prefs: -1 off, 0 every time the app is reopened, otherwise seconds away.
 */
object AppLock {
    val CHOICES = listOf("Off" to -1, "Every time" to 0, "After 1 minute" to 60, "After 5 minutes" to 300, "After 15 minutes" to 900)

    fun deviceHasLock(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    fun unlockIntent(context: Context): Intent? =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
            .createConfirmDeviceCredentialIntent("Weather Lab", "Unlock to see your weather")
}

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onUnlocked()
    }
    val ask: () -> Unit = {
        val intent = AppLock.unlockIntent(context)
        // A phone with no screen lock any more can't ask, so let the owner in rather than lock them out.
        if (intent == null) onUnlocked() else launcher.launch(intent)
    }
    LaunchedEffect(Unit) { ask() }
    Column(
        Modifier.fillMaxSize().background(C.bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Weather Lab is locked", fontSize = 22.sp, fontWeight = FontWeight.W800, color = C.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Use your phone's screen lock to open it.", style = T.body, color = C.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = ask, colors = ButtonDefaults.buttonColors(containerColor = C.cyan, contentColor = C.bg)) { Text("Unlock") }
    }
}

/** A setup code carries the address and token so a new phone can be set up by pasting it. */
object SetupCode {
    private const val PREFIX = "WL1|"

    fun make(address: String, token: String): String =
        Base64.encodeToString((PREFIX + address + "|" + token).toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

    /** The address and token inside a code, or null when it isn't a Weather Lab setup code. */
    fun read(code: String): Pair<String, String>? = try {
        val text = String(Base64.decode(code.trim(), Base64.URL_SAFE or Base64.NO_WRAP))
        val parts = text.removePrefix(PREFIX).split("|", limit = 2)
        if (!text.startsWith(PREFIX) || parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) null else parts[0] to parts[1]
    } catch (_: Exception) {
        null
    }
}
