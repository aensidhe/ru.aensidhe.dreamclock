package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode

@Composable
fun DreamRoot(
    state: ClockUiState,
    showAnalog: Boolean,
    mode: ColorRenderMode,
    deck: SlideDeckModel?,
    imageLoader: ImageLoader?,
    everyXthMinute: Int,
    photoSeconds: Int,
    analogSeconds: Int,
) {
    var suppressBottomLeft by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SlideDeck(
            deck = deck,
            imageLoader = imageLoader,
            showAnalog = showAnalog,
            now = java.time.LocalDateTime.now(),
            secondHandColor = stateColor(state.state),
            everyXthMinute = everyXthMinute,
            photoSeconds = photoSeconds,
            analogSeconds = analogSeconds,
            onSuppressBottomLeft = { suppressBottomLeft = it },
        )
        ClockOverlay(ui = state, mode = mode, suppressBottomLeft = suppressBottomLeft)
    }
}
