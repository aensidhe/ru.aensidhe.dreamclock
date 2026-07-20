package ru.aensidhe.dreamclock.immich

import java.time.Duration
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.photos.CaptionLines

class SlideTimingTest {
    private val photo = RenderPhoto("p", "t", null)

    @Test
    fun `a photo slide shows for the photo interval`() {
        assertEquals(Duration.ofSeconds(8), SlideTiming.durationFor(photo, photoSeconds = 8, analogSeconds = 10))
    }

    @Test
    fun `a paired slide shows for the photo interval`() {
        val paired = RenderPairedPhoto(photo, RenderPhoto("p2", "t2", CaptionLines("d", null)))
        assertEquals(Duration.ofSeconds(8), SlideTiming.durationFor(paired, photoSeconds = 8, analogSeconds = 10))
    }

    @Test
    fun `the clock slide shows for the analog interval`() {
        assertEquals(Duration.ofSeconds(10), SlideTiming.durationFor(RenderClock, photoSeconds = 8, analogSeconds = 10))
    }
}
