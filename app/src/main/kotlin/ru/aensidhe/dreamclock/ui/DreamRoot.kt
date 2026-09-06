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
import ru.aensidhe.dreamclock.core.schedule.Schedule

private const val NOON = 12

@Composable
fun DreamRoot(
    state: ClockUiState,
    showAnalog: Boolean,
    deck: SlideDeckModel?,
    imageLoader: ImageLoader?,
    schedule: Schedule,
    everyXthMinute: Int,
    photoSeconds: Int,
    analogSeconds: Int,
) {
    var suppressBottomLeft by remember { mutableStateOf(false) }
    val now = LocalDateTime.now()
    val numerals = remember(now.toLocalDate(), now.hour < NOON, schedule) { numeralColors(now, schedule) }
    Box(Modifier.fillMaxSize()) {
        SlideDeck(
            deck = deck,
            imageLoader = imageLoader,
            showAnalog = showAnalog,
            now = now,
            secondHandColor = stateColor(state.state),
            numeralColors = numerals,
            everyXthMinute = everyXthMinute,
            photoSeconds = photoSeconds,
            analogSeconds = analogSeconds,
            onSuppressBottomLeft = { suppressBottomLeft = it },
        )
        ClockOverlay(ui = state, suppressBottomLeft = suppressBottomLeft)
    }
}
