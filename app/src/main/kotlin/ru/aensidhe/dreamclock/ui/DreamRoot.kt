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
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import ru.aensidhe.dreamclock.core.schedule.Schedule

@Composable
fun DreamRoot(
    state: ClockUiState,
    schedule: Schedule,
    showAnalog: Boolean,
    deck: SlideDeckModel?,
    imageLoader: ImageLoader?,
    everyXthMinute: Int,
    photoSeconds: Int,
    analogSeconds: Int,
) {
    var suppressBottomLeft by remember { mutableStateOf(false) }
    val now = LocalDateTime.now()
    val ticks = remember(now.truncatedTo(ChronoUnit.HOURS), schedule) { tickStyles(now, schedule) }
    Box(Modifier.fillMaxSize()) {
        SlideDeck(
            deck = deck,
            imageLoader = imageLoader,
            showAnalog = showAnalog,
            now = now,
            secondHandColor = stateColor(state.state),
            tickStyles = ticks,
            everyXthMinute = everyXthMinute,
            photoSeconds = photoSeconds,
            analogSeconds = analogSeconds,
            onSuppressBottomLeft = { suppressBottomLeft = it },
        )
        ClockOverlay(ui = state, suppressBottomLeft = suppressBottomLeft)
    }
}
