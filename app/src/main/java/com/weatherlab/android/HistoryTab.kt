package com.weatherlab.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The views on the History tab (0.7.18), the same set as the History page in Weather Lab, plus the phone's own. */
internal enum class HistoryView(val label: String) {
    DAILY("Daily"), RECORDS("Records"), RANKINGS("Rankings"), COMPARE("Compare"), MONTH("Month"), YEAR("Year"),
    PHONE("On this phone"),
}

/** History: pick a view from the chips at the top; the page under them is that view. A day opens full screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryTab(
    address: String, token: String, view: HistoryView, onView: (HistoryView) -> Unit, prefs: Prefs,
    dayKey: String?, onOpenDay: (String) -> Unit, onOpenMore: (MoreSub) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (dayKey == null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryView.entries.forEach { v ->
                    FilterChip(
                        selected = view == v,
                        onClick = { onView(v) },
                        label = { Text(v.label, color = C.text) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = C.card,
                            selectedContainerColor = C.cyan.copy(alpha = 50 / 255f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = view == v,
                            borderColor = C.line, selectedBorderColor = C.cyan,
                        ),
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                dayKey != null -> DayDetailPage(address, token, dayKey)
                view == HistoryView.DAILY -> DailyPage(address, token, onOpenDay)
                view == HistoryView.RECORDS -> RecordsPage(address, token, onOpenMore)
                view == HistoryView.RANKINGS -> RankingsPage(address, token)
                view == HistoryView.COMPARE -> ComparePage(address, token)
                view == HistoryView.MONTH -> MonthCardPage(address, token)
                view == HistoryView.YEAR -> YearPage(address, token)
                else -> PhoneHistoryPage(prefs)
            }
        }
    }
}
