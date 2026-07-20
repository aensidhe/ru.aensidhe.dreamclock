package ru.aensidhe.dreamclock.core.photos

import java.time.Duration
import java.time.LocalDateTime

object PredictableClock {
    private val CLOCK_LEAD: Duration = Duration.ofSeconds(60)

    fun nextMark(
        from: LocalDateTime,
        everyXthMinute: Int,
    ): LocalDateTime {
        val x = everyXthMinute.coerceIn(1, 60)
        val onMinute = from.withSecond(0).withNano(0)
        val exactlyOnMark = from == onMinute && from.minute % x == 0
        if (exactlyOnMark) return from
        val nextMultiple = (from.minute / x + 1) * x
        return if (nextMultiple >= 60) {
            from
                .plusHours(1)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
        } else {
            from.withMinute(nextMultiple).withSecond(0).withNano(0)
        }
    }

    fun clockDue(
        now: LocalDateTime,
        everyXthMinute: Int,
    ): Boolean = Duration.between(now, nextMark(now, everyXthMinute)) < CLOCK_LEAD

    fun clockDuration(
        now: LocalDateTime,
        everyXthMinute: Int,
        analogSeconds: Int,
    ): Duration = Duration.between(now, nextMark(now, everyXthMinute)).plusSeconds(analogSeconds.toLong())
}
