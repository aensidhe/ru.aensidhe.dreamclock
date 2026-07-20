package ru.aensidhe.dreamclock.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import java.time.Duration
import java.time.Instant
import ru.aensidhe.dreamclock.immich.RenderClock
import ru.aensidhe.dreamclock.immich.RenderPairedPhoto
import ru.aensidhe.dreamclock.immich.RenderPhoto
import ru.aensidhe.dreamclock.immich.RenderSlide
import ru.aensidhe.dreamclock.immich.SlideDriver
import ru.aensidhe.dreamclock.immich.SlideResolver

class SlideDeckModel(
    private val driver: SlideDriver,
    private val resolver: SlideResolver,
) {
    fun next(
        now: Instant,
        everyXthMinute: Int,
        photoSeconds: Int,
        analogSeconds: Int,
    ): TimedRender {
        val timed = driver.next(now, everyXthMinute, photoSeconds, analogSeconds)
        return TimedRender(resolver.resolve(timed.slide), timed.duration)
    }

    fun preload(
        slide: RenderSlide,
        imageLoader: ImageLoader,
        context: PlatformContext,
    ) {
        urlsOf(slide).forEach { imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
    }

    private fun urlsOf(slide: RenderSlide): List<String> =
        when (slide) {
            is RenderPhoto -> listOf(slide.previewUrl, slide.placeholderUrl)
            is RenderPairedPhoto -> urlsOf(slide.left) + urlsOf(slide.right)
            RenderClock -> emptyList()
        }
}

data class TimedRender(
    val slide: RenderSlide,
    val duration: Duration,
)
