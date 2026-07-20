package ru.aensidhe.dreamclock.immich

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import ru.aensidhe.dreamclock.core.photos.ClockSlide
import ru.aensidhe.dreamclock.core.photos.PlannedSlide
import ru.aensidhe.dreamclock.core.photos.PredictableClock
import ru.aensidhe.dreamclock.core.photos.SlidePlanner

data class TimedSlide(
    val slide: PlannedSlide,
    val duration: Duration,
)

class SlideDriver(
    private val assets: Iterator<SlideAsset>,
    private val planner: SlidePlanner,
    private val zone: ZoneId,
) {
    private val buffer = ArrayDeque<PlannedSlide>()

    fun next(
        now: Instant,
        everyXthMinute: Int,
        photoSeconds: Int,
        analogSeconds: Int,
    ): TimedSlide {
        val local = LocalDateTime.ofInstant(now, zone)
        if (PredictableClock.clockDue(local, everyXthMinute)) {
            return TimedSlide(ClockSlide, PredictableClock.clockDuration(local, everyXthMinute, analogSeconds))
        }
        while (buffer.isEmpty()) {
            buffer.addAll(planner.offer(assets.next().toPlannerAsset()))
        }
        return TimedSlide(buffer.removeFirst(), Duration.ofSeconds(photoSeconds.toLong()))
    }
}
