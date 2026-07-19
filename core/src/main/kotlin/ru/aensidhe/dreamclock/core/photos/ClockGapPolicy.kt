package ru.aensidhe.dreamclock.core.photos

import java.time.Duration
import java.time.Instant

object ClockGapPolicy {
    fun shouldForceClock(
        lastClockAt: Instant,
        now: Instant,
        maxGap: Duration,
    ): Boolean = Duration.between(lastClockAt, now) >= maxGap
}
