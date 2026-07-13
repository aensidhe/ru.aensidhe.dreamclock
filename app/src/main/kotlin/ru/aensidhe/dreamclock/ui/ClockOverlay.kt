package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode
import ru.aensidhe.dreamclock.ui.colorrender.RenderOverlay

@Composable
fun ClockOverlay(
    ui: ClockUiState,
    mode: ColorRenderMode,
) {
    Box(Modifier.fillMaxSize().padding(48.dp)) {
        Text(
            ui.digital,
            Modifier.align(Alignment.TopStart),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(Modifier.align(Alignment.BottomCenter)) {
            mode.RenderOverlay(ui.state) { textColor ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ui.colloquial?.let { Text(it, color = textColor, fontSize = 24.sp) }
                    ui.statusText?.let { Text(it, color = textColor, fontSize = 24.sp) }
                }
            }
        }
    }
}
