package ru.aensidhe.dreamclock.core.photos

object YearWalk {
    fun countsTowardEmptyStreak(
        candidateYear: Int,
        cachedOldestYear: Int,
    ): Boolean = candidateYear < cachedOldestYear

    fun shouldQueryOlderYear(
        candidateYear: Int,
        cachedOldestYear: Int,
        consecutiveEmptyBelowOldest: Int,
        maxEmptyYearsBack: Int,
    ): Boolean {
        if (candidateYear >= cachedOldestYear) return true
        return consecutiveEmptyBelowOldest < maxEmptyYearsBack
    }
}
