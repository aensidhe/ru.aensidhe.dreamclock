package ru.aensidhe.dreamclock.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import ru.aensidhe.dreamclock.immich.OverlaySuppression
import ru.aensidhe.dreamclock.immich.RenderClock
import ru.aensidhe.dreamclock.immich.RenderPairedPhoto
import ru.aensidhe.dreamclock.immich.RenderPhoto
import ru.aensidhe.dreamclock.immich.SlideTiming

@Suppress("LongParameterList")
@Composable
fun SlideDeck(
    deck: SlideDeckModel?,
    imageLoader: ImageLoader?,
    showAnalog: Boolean,
    now: LocalDateTime,
    secondHandColor: Color,
    onSuppressBottomLeft: (Boolean) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (deck == null || imageLoader == null) {
            LaunchedEffect(Unit) { onSuppressBottomLeft(false) }
            if (showAnalog) AnalogClockSlide(now, secondHandColor)
            return@Box
        }
        val context = LocalPlatformContext.current
        var current by remember(deck) { mutableStateOf(deck.nextRender(Instant.now())) }
        LaunchedEffect(deck) {
            var shown = current
            while (true) {
                onSuppressBottomLeft(OverlaySuppression.suppressBottomLeft(shown))
                val upcoming = deck.nextRender(Instant.now())
                deck.preload(upcoming, imageLoader, context)
                delay(SlideTiming.durationFor(shown, deck.photoSeconds, deck.analogSeconds).toMillis())
                current = upcoming
                shown = upcoming
            }
        }
        Crossfade(targetState = current, label = "slide") { slide ->
            when (slide) {
                is RenderPhoto -> PhotoSlide(slide, imageLoader, Modifier.fillMaxSize())
                is RenderPairedPhoto -> PairedPhotoSlide(slide, imageLoader, Modifier.fillMaxSize())
                RenderClock -> if (showAnalog) AnalogClockSlide(now, secondHandColor)
            }
        }
    }
}
