package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ClockOverlay(
    ui: ClockUiState,
    suppressBottomLeft: Boolean = false,
) {
    Box(Modifier.fillMaxSize().padding(PaddingValues(start = 48.dp, top = 48.dp, end = 48.dp, bottom = 16.dp))) {
        Text(
            ui.digital,
            Modifier.align(Alignment.TopStart),
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        if (!suppressBottomLeft) {
            val textColor = stateColor(ui.state)
            Box(Modifier.align(Alignment.BottomStart)) {
                Column(horizontalAlignment = Alignment.Start) {
                    ui.statusText?.takeIf { it.isNotBlank() }?.let { Text(it, color = textColor, fontSize = 24.sp) }
                    ui.colloquial?.let { Text(it, color = textColor, fontSize = 24.sp) }
                }
            }
        }
    }
}
