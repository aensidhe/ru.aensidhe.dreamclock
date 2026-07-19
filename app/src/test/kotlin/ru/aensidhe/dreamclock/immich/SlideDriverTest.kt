package ru.aensidhe.dreamclock.immich

import java.time.Duration
import java.time.Instant
import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.ClockSlide
import ru.aensidhe.dreamclock.core.photos.Orientation
import ru.aensidhe.dreamclock.core.photos.SinglePhotoSlide
import ru.aensidhe.dreamclock.core.photos.SlideMediaKind
import ru.aensidhe.dreamclock.core.photos.SlidePlanner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SlideDriverTest {
    private fun landscape(id: String) =
        SlideAsset(id, SlideMediaKind.PHOTO, Orientation.LANDSCAPE, CaptionSource(null, null, null))

    private fun idOf(slide: Any): String = (slide as SinglePhotoSlide).asset.id

    @Test
    fun `forces a clock slide once the gap since the last clock is reached`() {
        val base = Instant.parse("2026-07-20T10:00:00Z")
        val driver =
            SlideDriver(
                assets = listOf(landscape("l1"), landscape("l2"), landscape("l3")).iterator(),
                planner = SlidePlanner(analogCadence = 100),
                maxGap = Duration.ofMinutes(5),
                lastClockAt = base,
            )
        assertEquals("l1", idOf(driver.next(base)))
        assertEquals("l2", idOf(driver.next(base.plus(Duration.ofMinutes(1)))))
        assertTrue(driver.next(base.plus(Duration.ofMinutes(6))) is ClockSlide)
        // Clock just fired at +6m, so the gap resets and the next slide is content again.
        assertEquals("l3", idOf(driver.next(base.plus(Duration.ofMinutes(7)))))
    }

    @Test
    fun `count cadence clock also resets the gap timer`() {
        val base = Instant.parse("2026-07-20T10:00:00Z")
        val driver =
            SlideDriver(
                assets = listOf(landscape("l1"), landscape("l2"), landscape("l3")).iterator(),
                planner = SlidePlanner(analogCadence = 1),
                maxGap = Duration.ofMinutes(5),
                lastClockAt = base,
            )
        assertEquals("l1", idOf(driver.next(base)))
        // analogCadence = 1 means a clock is queued after each content slide.
        assertTrue(driver.next(base.plus(Duration.ofMinutes(1))) is ClockSlide)
        assertEquals("l2", idOf(driver.next(base.plus(Duration.ofMinutes(2)))))
    }
}
