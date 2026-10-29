package com.weatherlab.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WatchApp() }
    }
}

private sealed interface Ui {
    data object Loading : Ui
    data object NotSetUp : Ui
    data class Error(val message: String) : Ui
    data class Ready(val data: WatchData) : Ui
}

private val Cyan = Color(0xFF58A6FF)
private val Muted = Color(0xFFB0B9DD)

@Composable
private fun WatchApp() {
    val context = LocalContext.current
    var ui by remember { mutableStateOf<Ui>(Ui.Loading) }
    var reload by remember { mutableIntStateOf(0) }

    // Loads when opened, when Refresh is tapped, and then every 5 minutes while the app is on screen.
    LaunchedEffect(reload) {
        while (true) {
            val store = WatchStore(context)
            if (!store.configured) ConfigSync.pullExisting(context)
            if (!store.configured) {
                ui = Ui.NotSetUp
            } else {
                if (ui !is Ui.Ready) ui = Ui.Loading
                ui = try {
                    Ui.Ready(withContext(Dispatchers.IO) { WatchApi.load(store.address, store.token) })
                } catch (e: Exception) {
                    Ui.Error(e.message ?: "Couldn't reach Weather Lab. Is the PC on, and the watch on Wi-Fi?")
                }
            }
            delay(300_000)
        }
    }

    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                autoCentering = androidx.wear.compose.foundation.lazy.AutoCenteringParams(itemIndex = 0),
                flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(
                    state = androidx.wear.compose.foundation.lazy.rememberScalingLazyListState(),
                ),
            ) {
                when (val shown = ui) {
                    Ui.Loading -> item { CircularProgressIndicator(Modifier.padding(top = 24.dp)) }
                    Ui.NotSetUp -> {
                        item { Message("Open Weather Lab on your phone to connect this watch.") }
                        item { RefreshChip { reload++ } }
                    }
                    is Ui.Error -> {
                        item { Message(shown.message) }
                        item { RefreshChip { reload++ } }
                    }
                    is Ui.Ready -> {
                        val d = shown.data
                        item {
                            Text(d.tempText, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        d.feelsText?.let { item { Text(it, fontSize = 14.sp, color = Muted) } }
                        item { Text(d.highLowText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Cyan) }
                        d.rainChance?.let { item { Text("Rain chance $it", fontSize = 14.sp, color = Color.White) } }
                        if (d.detailText.isNotBlank()) item { Text(d.detailText, fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center) }
                        d.headline?.let {
                            item {
                                Text(
                                    it, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center,
                                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                        item { RefreshChip { reload++ } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text, fontSize = 13.sp, color = Color.White, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RefreshChip(onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text("Refresh") },
        colors = ChipDefaults.secondaryChipColors(),
    )
}
