package ru.aensidhe.dreamclock.core.photos

import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredictableClockTest {
    private fun t(
        h: Int,
        m: Int,
        s: Int = 0,
    ) = LocalDateTime.of(2026, 7, 20, h, m, s)

    @Test
    fun nextMarkExactlyOnMarkReturnsSame() {
        assertEquals(t(12, 5), PredictableClock.nextMark(t(12, 5), 5))
    }

    @Test
    fun nextMarkMidIntervalRoundsUp() {
        assertEquals(t(12, 5), PredictableClock.nextMark(t(12, 4, 30), 5))
        assertEquals(t(12, 10), PredictableClock.nextMark(t(12, 6), 5))
    }

    @Test
    fun nextMarkPastMarkSecondsRoundsToNext() {
        assertEquals(t(12, 10), PredictableClock.nextMark(t(12, 5, 1), 5))
    }

    @Test
    fun nextMarkWrapsToTopOfNextHour() {
        assertEquals(t(13, 0), PredictableClock.nextMark(t(12, 57), 5))
        assertEquals(t(13, 0), PredictableClock.nextMark(t(12, 30), 60))
    }

    @Test
    fun nextMarkHandlesNonDivisorX() {
        assertEquals(t(12, 56), PredictableClock.nextMark(t(12, 56), 7))
        assertEquals(t(13, 0), PredictableClock.nextMark(t(12, 57), 7))
    }

    @Test
    fun clockDueWithinLeadWindow() {
        assertTrue(PredictableClock.clockDue(t(12, 4, 30), 5))
        assertTrue(PredictableClock.clockDue(t(12, 5), 5))
        assertFalse(PredictableClock.clockDue(t(12, 3), 5))
    }

    @Test
    fun clockDurationCoversMarkPlusAnalog() {
        assertEquals(Duration.ofSeconds(60), PredictableClock.clockDuration(t(12, 4, 30), 5, 30))
        assertEquals(Duration.ofSeconds(30), PredictableClock.clockDuration(t(12, 5), 5, 30))
    }
}
