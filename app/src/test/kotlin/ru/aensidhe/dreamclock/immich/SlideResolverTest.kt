package ru.aensidhe.dreamclock.immich

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.photos.CaptionLines
import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.ClockSlide
import ru.aensidhe.dreamclock.core.photos.Orientation
import ru.aensidhe.dreamclock.core.photos.PairedPhotoSlide
import ru.aensidhe.dreamclock.core.photos.PlannerAsset
import ru.aensidhe.dreamclock.core.photos.SinglePhotoSlide
import ru.aensidhe.dreamclock.core.photos.SlideMediaKind
import ru.aensidhe.dreamclock.core.photos.VideoSlide
import ru.aensidhe.dreamclock.core.time.ClockLocale

class SlideResolverTest {
    private fun photo(id: String) = PlannerAsset(id, SlideMediaKind.PHOTO, Orientation.LANDSCAPE)

    private val captions =
        mapOf(
            "p1" to CaptionSource(LocalDateTime.of(2026, 7, 19, 14, 32), "Berlin", "Germany"),
            "p2" to CaptionSource(null, null, null),
        )
    private val resolver = SlideResolver("https://immich.example", captions, ClockLocale.EN)

    @Test
    fun `single photo carries its urls and formatted caption`() {
        val slide = resolver.resolve(SinglePhotoSlide(photo("p1"))) as RenderPhoto
        assertEquals("https://immich.example/api/assets/p1/thumbnail?size=preview", slide.previewUrl)
        assertEquals("https://immich.example/api/assets/p1/thumbnail?size=thumbnail", slide.placeholderUrl)
        assertEquals(CaptionLines("19 July 2026 | 14:32", "Berlin, Germany"), slide.caption)
    }

    @Test
    fun `a photo with no caption data resolves to a null caption`() {
        val slide = resolver.resolve(SinglePhotoSlide(photo("p2"))) as RenderPhoto
        assertNull(slide.caption)
    }

    @Test
    fun `an unknown id resolves to a null caption`() {
        val slide = resolver.resolve(SinglePhotoSlide(photo("missing"))) as RenderPhoto
        assertNull(slide.caption)
    }

    @Test
    fun `a paired slide resolves each half independently`() {
        val slide = resolver.resolve(PairedPhotoSlide(photo("p1"), photo("p2"))) as RenderPairedPhoto
        assertEquals("Berlin, Germany", slide.left.caption?.location)
        assertNull(slide.right.caption)
    }

    @Test
    fun `clock and video both resolve to the clock render`() {
        assertTrue(resolver.resolve(ClockSlide) is RenderClock)
        assertTrue(resolver.resolve(VideoSlide(photo("v1"))) is RenderClock)
    }
}
