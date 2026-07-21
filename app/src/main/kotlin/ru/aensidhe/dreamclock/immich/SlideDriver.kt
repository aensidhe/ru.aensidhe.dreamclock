package ru.aensidhe.dreamclock.immich

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import ru.aensidhe.dreamclock.core.photos.PlannedSlide
import ru.aensidhe.dreamclock.core.photos.PredictableClock
import ru.aensidhe.dreamclock.core.photos.SlidePlanner

class SlideDriver(
    private val assets: Iterator<SlideAsset>,
    private val planner: SlidePlanner,
    private val zone: ZoneId,
) {
    private val buffer = ArrayDeque<PlannedSlide>()

    /** The next photo or video slide. Independent of the clock, so a clock slot never consumes one. */
    fun nextMedia(): PlannedSlide {
        while (buffer.isEmpty()) {
            buffer.addAll(planner.offer(assets.next().toPlannerAsset()))
        }
        return buffer.removeFirst()
    }

    /** How long to show the clock if it is due at [now], or null to show media instead. */
    fun clockSlot(
        now: Instant,
        everyXthMinute: Int,
        photoSeconds: Int,
        analogSeconds: Int,
    ): Duration? {
        val local = LocalDateTime.ofInstant(now, zone)
        if (!PredictableClock.clockDue(local, everyXthMinute, photoSeconds)) return null
        return PredictableClock.clockDuration(local, everyXthMinute, analogSeconds)
    }
}
