package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode

@Composable
fun DreamRoot(
    state: ClockUiState,
    showAnalog: Boolean,
    mode: ColorRenderMode,
) {
    Box(Modifier.fillMaxSize()) {
        SlideDeck(showAnalog = showAnalog, now = java.time.LocalDateTime.now())
        ClockOverlay(ui = state, mode = mode)
    }
}
