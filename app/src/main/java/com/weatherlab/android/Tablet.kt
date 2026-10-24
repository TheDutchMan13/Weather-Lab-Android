package com.weatherlab.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * On a wide screen (a tablet, or a foldable opened up) a tab shows two panes side by side with a thin divider.
 * On a phone it shows only the main pane, exactly as before.
 */
@Composable
fun Paned(wide: Boolean, right: @Composable () -> Unit, left: @Composable () -> Unit) {
    if (!wide) {
        left()
        return
    }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) { left() }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.line))
        Box(Modifier.weight(1f).fillMaxHeight()) { right() }
    }
}
