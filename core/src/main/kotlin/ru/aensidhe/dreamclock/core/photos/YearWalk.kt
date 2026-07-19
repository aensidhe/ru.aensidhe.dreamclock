package ru.aensidhe.dreamclock.core.photos

object YearWalk {
    const val MAX_EMPTY_STREAK = 20

    fun shouldQueryNextYear(
        yearsQueried: Int,
        consecutiveEmptyYears: Int,
        maxYearsCap: Int,
    ): Boolean {
        if (maxYearsCap > 0 && yearsQueried >= maxYearsCap) return false
        if (consecutiveEmptyYears >= MAX_EMPTY_STREAK) return false
        return true
    }
}
