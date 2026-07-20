package ru.aensidhe.dreamclock.core.photos

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YearWalkTest {
    @Test
    fun yearsAtOrAboveOldestNeverCountEmpties() {
        assertFalse(YearWalk.countsTowardEmptyStreak(candidateYear = 2010, cachedOldestYear = 2001))
        assertFalse(YearWalk.countsTowardEmptyStreak(candidateYear = 2001, cachedOldestYear = 2001))
        assertTrue(YearWalk.countsTowardEmptyStreak(candidateYear = 2000, cachedOldestYear = 2001))
    }

    @Test
    fun alwaysWalkWhileAtOrAboveOldest() {
        assertTrue(
            YearWalk.shouldQueryOlderYear(
                candidateYear = 2005,
                cachedOldestYear = 2001,
                consecutiveEmptyBelowOldest = 0,
                maxEmptyYearsBack = 10,
            ),
        )
    }

    @Test
    fun stopBelowOldestAfterThresholdEmpties() {
        assertTrue(
            YearWalk.shouldQueryOlderYear(2000, 2001, consecutiveEmptyBelowOldest = 9, maxEmptyYearsBack = 10),
        )
        assertFalse(
            YearWalk.shouldQueryOlderYear(1991, 2001, consecutiveEmptyBelowOldest = 10, maxEmptyYearsBack = 10),
        )
    }
}
