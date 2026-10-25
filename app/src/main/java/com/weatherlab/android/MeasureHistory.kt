package com.weatherlab.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

private val PERIODS = listOf("24 hours" to 1, "7 days" to 7, "30 days" to 30)

private fun figure(v: Double?, unit: String): String =
    if (v == null) "--" else {
        val n = if (Math.abs(v) >= 100) Math.round(v).toString() else "%.1f".format(v)
        if (unit.isBlank()) n else if (unit == "%" || unit.startsWith("°")) n + unit else "$n $unit"
    }

/** A reading over 24 hours, 7 days or 30 days, opened by tapping a Live tile. Needs Weather Lab 0.37.9 or newer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureHistorySheet(address: String, token: String, key: String, title: String, onDismiss: () -> Unit) {
    var days by remember { mutableIntStateOf(1) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.card) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = T.title, color = C.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PERIODS.forEach { (name, d) ->
                    FilterChip(
                        selected = days == d,
                        onClick = { days = d },
                        label = { Text(name, color = C.text) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = C.card,
                            selectedContainerColor = C.cyan.copy(alpha = 50 / 255f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = days == d, borderColor = C.line, selectedBorderColor = C.cyan,
                        ),
                    )
                }
            }
            Fetch(address, token, "$key/$days", { it.measureHistory(key, days) }) { h ->
                if (h.points.size < 2) {
                    Text("Not enough readings in this period yet.", style = T.body, color = C.muted)
                } else {
                    TrendChart(h.points.map { it.second }, unit = h.unit.let { if (it == "%" || it.startsWith("°")) it else "" }, height = 180.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Stat("High", figure(h.high, h.unit))
                        Stat("Low", figure(h.low, h.unit))
                        Stat("Average", figure(h.average, h.unit))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = T.label, color = C.muted)
        Text(value, style = T.body, color = C.text)
    }
}
