package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime

@Composable
fun SlideDeck(
    showAnalog: Boolean,
    now: LocalDateTime,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (showAnalog) AnalogClockSlide(now)
    }
}
