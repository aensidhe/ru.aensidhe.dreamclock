package ru.aensidhe.dreamclock.immich

import java.time.Duration

object SlideTiming {
    fun durationFor(
        slide: RenderSlide,
        photoSeconds: Int,
        analogSeconds: Int,
    ): Duration =
        when (slide) {
            is RenderPhoto, is RenderPairedPhoto -> Duration.ofSeconds(photoSeconds.toLong())
            RenderClock -> Duration.ofSeconds(analogSeconds.toLong())
        }
}
