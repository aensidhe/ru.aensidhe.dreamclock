package ru.aensidhe.dreamclock.immich

import java.time.Duration
import java.time.Instant
import ru.aensidhe.dreamclock.core.photos.ClockGapPolicy
import ru.aensidhe.dreamclock.core.photos.ClockSlide
import ru.aensidhe.dreamclock.core.photos.PlannedSlide
import ru.aensidhe.dreamclock.core.photos.SlidePlanner

class SlideDriver(
    private val assets: Iterator<SlideAsset>,
    private val planner: SlidePlanner,
    private val maxGap: Duration,
    lastClockAt: Instant,
) {
    private var lastClockAt: Instant = lastClockAt
    private val buffer = ArrayDeque<PlannedSlide>()

    fun next(now: Instant): PlannedSlide {
        if (ClockGapPolicy.shouldForceClock(lastClockAt, now, maxGap)) {
            lastClockAt = now
            return ClockSlide
        }
        while (buffer.isEmpty()) {
            buffer.addAll(planner.offer(assets.next().toPlannerAsset()))
        }
        val slide = buffer.removeFirst()
        if (slide is ClockSlide) lastClockAt = now
        return slide
    }
}
