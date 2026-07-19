package ru.aensidhe.dreamclock.core.photos

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class YearWalkTest {
    @Test
    fun `continues while under cap and streak`() {
        assertTrue(YearWalk.shouldQueryNextYear(yearsQueried = 3, consecutiveEmptyYears = 2, maxYearsCap = 0))
    }

    @Test
    fun `stops at positive cap`() {
        assertFalse(YearWalk.shouldQueryNextYear(yearsQueried = 10, consecutiveEmptyYears = 0, maxYearsCap = 10))
    }

    @Test
    fun `continues just under positive cap`() {
        assertTrue(YearWalk.shouldQueryNextYear(yearsQueried = 9, consecutiveEmptyYears = 0, maxYearsCap = 10))
    }

    @Test
    fun `cap of zero means unlimited`() {
        assertTrue(YearWalk.shouldQueryNextYear(yearsQueried = 100, consecutiveEmptyYears = 5, maxYearsCap = 0))
    }

    @Test
    fun `stops after twenty consecutive empty years`() {
        assertFalse(YearWalk.shouldQueryNextYear(yearsQueried = 40, consecutiveEmptyYears = 20, maxYearsCap = 0))
    }

    @Test
    fun `continues at nineteen empty years`() {
        assertTrue(YearWalk.shouldQueryNextYear(yearsQueried = 40, consecutiveEmptyYears = 19, maxYearsCap = 0))
    }
}
