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
import androidx.compose.runtime.rememberUpdatedState
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
import ru.aensidhe.dreamclock.immich.RenderSlide

private const val MILLIS_PER_SECOND = 1000L

@Composable
fun SlideDeck(
    deck: SlideDeckModel?,
    imageLoader: ImageLoader?,
    showAnalog: Boolean,
    now: LocalDateTime,
    secondHandColor: Color,
    everyXthMinute: Int,
    photoSeconds: Int,
    analogSeconds: Int,
    onSuppressBottomLeft: (Boolean) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (deck == null || imageLoader == null) {
            LaunchedEffect(Unit) { onSuppressBottomLeft(false) }
            if (showAnalog) AnalogClockSlide(now, secondHandColor)
            return@Box
        }
        val context = LocalPlatformContext.current
        val latestEveryX by rememberUpdatedState(everyXthMinute)
        val latestPhotoSeconds by rememberUpdatedState(photoSeconds)
        val latestAnalogSeconds by rememberUpdatedState(analogSeconds)
        var current by remember(deck) { mutableStateOf<RenderSlide?>(null) }
        LaunchedEffect(deck) {
            // One media slide is kept loaded ahead. A clock slot never consumes it: the clock
            // simply takes the turn, and the same photo is still waiting afterwards.
            var upcoming = deck.nextMedia()
            deck.preload(upcoming, imageLoader, context)
            while (true) {
                val clock = deck.clockSlot(Instant.now(), latestEveryX, latestPhotoSeconds, latestAnalogSeconds)
                if (clock != null) {
                    current = RenderClock
                    onSuppressBottomLeft(OverlaySuppression.suppressBottomLeft(RenderClock))
                    delay(clock.toMillis())
                } else {
                    current = upcoming
                    onSuppressBottomLeft(OverlaySuppression.suppressBottomLeft(upcoming))
                    val following = deck.nextMedia()
                    deck.preload(following, imageLoader, context)
                    delay(latestPhotoSeconds.toLong() * MILLIS_PER_SECOND)
                    upcoming = following
                }
            }
        }
        Crossfade(targetState = current, label = "slide") { slide ->
            when (slide) {
                is RenderPhoto -> PhotoSlide(slide, imageLoader, Modifier.fillMaxSize())
                is RenderPairedPhoto -> PairedPhotoSlide(slide, imageLoader, Modifier.fillMaxSize())
                RenderClock -> if (showAnalog) AnalogClockSlide(now, secondHandColor)
                null -> {}
            }
        }
    }
}
