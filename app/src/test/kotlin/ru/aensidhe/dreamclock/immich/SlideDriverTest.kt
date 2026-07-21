package ru.aensidhe.dreamclock.immich

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.photos.CaptionSource
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

    private fun driver() =
        SlideDriver(
            assets = listOf(landscape("l1"), landscape("l2")).iterator(),
            planner = SlidePlanner(),
            zone = zone,
        )

    private fun instantOf(local: LocalDateTime) = local.atZone(zone).toInstant()

    @Test
    fun `nextMedia walks the asset stream`() {
        val driver = driver()

        val first = driver.nextMedia()
        val second = driver.nextMedia()

        assertIs<SinglePhotoSlide>(first)
        assertIs<SinglePhotoSlide>(second)
        assertEquals("l1", first.asset.id)
        assertEquals("l2", second.asset.id)
    }

    @Test
    fun `no clock slot well before the mark`() {
        // 10:02:00 leaves 3 minutes to the 10:05:00 mark, far more than a 30s photo needs.
        val now = instantOf(LocalDateTime.of(2026, 7, 20, 10, 2, 0))

        assertNull(driver().clockSlot(now, everyXthMinute, photoSeconds, analogSeconds))
    }

    @Test
    fun `clock slot near the mark is timed by PredictableClock`() {
        // 10:04:40 leaves 20s to the 10:05:00 mark, less than the 30s a photo would occupy.
        val local = LocalDateTime.of(2026, 7, 20, 10, 4, 40)

        val slot = driver().clockSlot(instantOf(local), everyXthMinute, photoSeconds, analogSeconds)

        assertEquals(PredictableClock.clockDuration(local, everyXthMinute, analogSeconds), slot)
    }

    @Test
    fun `asking for a clock slot does not consume media`() {
        val driver = driver()

        val nearMark = instantOf(LocalDateTime.of(2026, 7, 20, 10, 4, 40))
        driver.clockSlot(nearMark, everyXthMinute, photoSeconds, analogSeconds)
        val first = driver.nextMedia()

        assertEquals("l1", assertIs<SinglePhotoSlide>(first).asset.id)
    }
}
