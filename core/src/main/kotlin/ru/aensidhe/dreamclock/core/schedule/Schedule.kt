package ru.aensidhe.dreamclock.core.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class Window(
    val start: LocalTime,
    val state: StateType,
    val textOverride: String? = null,
)

class DaySchedule(
    windows: List<Window>,
) {
    val windows: List<Window> = windows.sortedBy { it.start }

    init {
        require(this.windows.firstOrNull()?.start == LocalTime.MIDNIGHT) {
            "a day schedule must start with a 00:00 window"
        }
    }
}

data class Schedule(
    val default: DaySchedule,
    val byDayOfWeek: Map<DayOfWeek, DaySchedule> = emptyMap(),
    val overrides: Map<LocalDate, DaySchedule> = emptyMap(),
)

data class ActiveState(
    val state: StateType,
    val textOverride: String?,
)
