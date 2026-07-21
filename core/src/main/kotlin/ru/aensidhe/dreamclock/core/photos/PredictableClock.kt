package ru.aensidhe.dreamclock.core.photos

import java.time.Duration
import java.time.LocalDateTime

object PredictableClock {
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

    /**
     * True when a photo starting now would still be on screen at the next mark. The clock takes
     * the slot instead, so it is already up when the mark arrives rather than arriving late.
     */
    fun clockDue(
        now: LocalDateTime,
        everyXthMinute: Int,
        photoSeconds: Int,
    ): Boolean = Duration.between(now, nextMark(now, everyXthMinute)) < Duration.ofSeconds(photoSeconds.toLong())

    fun clockDuration(
        now: LocalDateTime,
        everyXthMinute: Int,
        analogSeconds: Int,
    ): Duration = Duration.between(now, nextMark(now, everyXthMinute)).plusSeconds(analogSeconds.toLong())
}
