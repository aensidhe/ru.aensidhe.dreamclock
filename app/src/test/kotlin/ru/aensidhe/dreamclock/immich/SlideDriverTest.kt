package ru.aensidhe.dreamclock.immich

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.ClockSlide
import ru.aensidhe.dreamclock.core.photos.Orientation
import ru.aensidhe.dreamclock.core.photos.PredictableClock
import ru.aensidhe.dreamclock.core.photos.SinglePhotoSlide
import ru.aensidhe.dreamclock.core.photos.SlideMediaKind
import ru.aensidhe.dreamclock.core.photos.SlidePlanner

class SlideDriverTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val noCaption = CaptionSource(null, null, null)
    private val everyXthMinute = 5
    private val photoSeconds = 30
    private val analogSeconds = 20

    private fun landscape(id: String) = SlideAsset(id, SlideMediaKind.PHOTO, Orientation.LANDSCAPE, noCaption)

    private fun instantOf(local: LocalDateTime) = local.atZone(zone).toInstant()

    @Test
    fun `outside the clock lead window returns a content slide timed by photoSeconds`() {
        val driver =
            SlideDriver(
                assets = listOf(landscape("l1"), landscape("l2")).iterator(),
                planner = SlidePlanner(),
                zone = zone,
            )
        // 10:02:00 leaves 3 minutes to the 10:05:00 mark, far more than a 30s photo needs.
        val now = instantOf(LocalDateTime.of(2026, 7, 20, 10, 2, 0))

        val timed = driver.next(now, everyXthMinute, photoSeconds, analogSeconds)

        assertIs<SinglePhotoSlide>(timed.slide)
        assertEquals("l1", timed.slide.asset.id)
        assertEquals(Duration.ofSeconds(photoSeconds.toLong()), timed.duration)
    }

    @Test
    fun `inside the clock lead window returns a clock slide timed by PredictableClock`() {
        val driver =
            SlideDriver(
                assets = listOf(landscape("l1"), landscape("l2")).iterator(),
                planner = SlidePlanner(),
                zone = zone,
            )
        // 10:04:40 leaves 20s to the 10:05:00 mark, less than the 30s a photo would occupy.
        val local = LocalDateTime.of(2026, 7, 20, 10, 4, 40)
        val now = instantOf(local)

        val timed = driver.next(now, everyXthMinute, photoSeconds, analogSeconds)

        assertEquals(ClockSlide, timed.slide)
        assertEquals(PredictableClock.clockDuration(local, everyXthMinute, analogSeconds), timed.duration)
    }
}
