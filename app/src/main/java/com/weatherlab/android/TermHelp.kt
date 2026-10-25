package com.weatherlab.android

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The small blue ring with a ? beside a weather term. Tap it to read what the term means. */
@Composable
fun TermHelpMark(term: Term, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .border(2.dp, C.cyan, CircleShape)
            .semantics { contentDescription = "What is ${term.name.lowercase()}?" },
        contentAlignment = Alignment.Center,
    ) {
        Text("?", color = C.cyan, fontSize = 13.sp, fontWeight = FontWeight.W800, textAlign = TextAlign.Center)
    }
}

/** The explanation, as a sheet from the bottom: the term, its one-line meaning, then the fuller explanation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermSheet(term: Term, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = C.card) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(term.name, style = T.title, color = C.text)
            Text(term.short, style = T.body, color = C.text)
            Text(term.long, style = T.body, color = C.muted)
            Spacer(Modifier.height(6.dp))
            FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Got it") }
        }
    }
}
