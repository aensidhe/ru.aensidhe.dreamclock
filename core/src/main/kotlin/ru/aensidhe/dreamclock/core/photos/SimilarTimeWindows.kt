package ru.aensidhe.dreamclock.core.photos

import java.time.LocalDate

data class DateWindow(
    val fromInclusive: LocalDate,
    val toInclusive: LocalDate,
)

object SimilarTimeWindows {
    fun windowFor(
        today: LocalDate,
        daysEitherSide: Int,
        yearsBack: Int,
    ): DateWindow {
        val center = today.minusYears(yearsBack.toLong())
        return DateWindow(
            fromInclusive = center.minusDays(daysEitherSide.toLong()),
            toInclusive = center.plusDays(daysEitherSide.toLong()),
        )
    }
}
