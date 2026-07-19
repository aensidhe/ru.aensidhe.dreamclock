package ru.aensidhe.dreamclock.core.photos

import java.time.Duration
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ClockGapPolicyTest {
    private val base = Instant.parse("2026-07-20T10:00:00Z")
    private val fiveMinutes = Duration.ofMinutes(5)

    @Test
    fun `forces clock when gap exceeded`() {
        assertTrue(ClockGapPolicy.shouldForceClock(base, base.plus(Duration.ofMinutes(6)), fiveMinutes))
    }

    @Test
    fun `forces clock exactly at the gap`() {
        assertTrue(ClockGapPolicy.shouldForceClock(base, base.plus(fiveMinutes), fiveMinutes))
    }

    @Test
    fun `does not force clock under the gap`() {
        assertFalse(ClockGapPolicy.shouldForceClock(base, base.plus(Duration.ofMinutes(4)), fiveMinutes))
    }
}
