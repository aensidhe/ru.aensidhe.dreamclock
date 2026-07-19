package ru.aensidhe.dreamclock.core.photos

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class SlidePlannerTest {
    private fun photo(
        id: String,
        o: Orientation,
    ) = PlannerAsset(id, SlideMediaKind.PHOTO, o)

    private fun video(id: String) = PlannerAsset(id, SlideMediaKind.VIDEO, Orientation.LANDSCAPE)

    @Test
    fun `two consecutive portraits pair`() {
        val planner = SlidePlanner(analogCadence = 100)
        assertEquals(emptyList(), planner.offer(photo("a", Orientation.PORTRAIT)))
        assertEquals(
            listOf(PairedPhotoSlide(photo("a", Orientation.PORTRAIT), photo("b", Orientation.PORTRAIT))),
            planner.offer(photo("b", Orientation.PORTRAIT)),
        )
    }

    @Test
    fun `landscape between portraits is emitted alone and portraits still pair`() {
        val planner = SlidePlanner(analogCadence = 100)
        assertEquals(emptyList(), planner.offer(photo("p1", Orientation.PORTRAIT)))
        assertEquals(
            listOf(SinglePhotoSlide(photo("l", Orientation.LANDSCAPE))),
            planner.offer(photo("l", Orientation.LANDSCAPE)),
        )
        assertEquals(
            listOf(PairedPhotoSlide(photo("p1", Orientation.PORTRAIT), photo("p2", Orientation.PORTRAIT))),
            planner.offer(photo("p2", Orientation.PORTRAIT)),
        )
    }

    @Test
    fun `video is emitted alone and does not consume a pending portrait`() {
        val planner = SlidePlanner(analogCadence = 100)
        assertEquals(emptyList(), planner.offer(photo("p1", Orientation.PORTRAIT)))
        assertEquals(listOf(VideoSlide(video("v"))), planner.offer(video("v")))
        assertEquals(
            listOf(PairedPhotoSlide(photo("p1", Orientation.PORTRAIT), photo("p2", Orientation.PORTRAIT))),
            planner.offer(photo("p2", Orientation.PORTRAIT)),
        )
    }

    @Test
    fun `clock slide injected every cadence content slides`() {
        val planner = SlidePlanner(analogCadence = 2)
        assertEquals(listOf(SinglePhotoSlide(photo("l1", Orientation.LANDSCAPE))), planner.offer(photo("l1", Orientation.LANDSCAPE)))
        assertEquals(
            listOf(SinglePhotoSlide(photo("l2", Orientation.LANDSCAPE)), ClockSlide),
            planner.offer(photo("l2", Orientation.LANDSCAPE)),
        )
        assertEquals(listOf(SinglePhotoSlide(photo("l3", Orientation.LANDSCAPE))), planner.offer(photo("l3", Orientation.LANDSCAPE)))
        assertEquals(
            listOf(SinglePhotoSlide(photo("l4", Orientation.LANDSCAPE)), ClockSlide),
            planner.offer(photo("l4", Orientation.LANDSCAPE)),
        )
    }

    @Test
    fun `a paired slide counts as one content slide toward cadence`() {
        val planner = SlidePlanner(analogCadence = 1)
        assertEquals(emptyList(), planner.offer(photo("p1", Orientation.PORTRAIT)))
        assertEquals(
            listOf(
                PairedPhotoSlide(photo("p1", Orientation.PORTRAIT), photo("p2", Orientation.PORTRAIT)),
                ClockSlide,
            ),
            planner.offer(photo("p2", Orientation.PORTRAIT)),
        )
    }
}
