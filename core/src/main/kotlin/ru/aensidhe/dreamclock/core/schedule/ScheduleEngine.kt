package ru.aensidhe.dreamclock.core.schedule

import java.time.LocalDateTime

object ScheduleEngine {
    fun activeState(
        now: LocalDateTime,
        schedule: Schedule,
    ): ActiveState {
        val day =
            schedule.overrides[now.toLocalDate()]
                ?: schedule.byDayOfWeek[now.dayOfWeek]
                ?: schedule.default
        val time = now.toLocalTime()
        val window = day.windows.last { it.start <= time }
        return ActiveState(window.state, window.textOverride)
    }

    fun minuteStates(
        now: LocalDateTime,
        schedule: Schedule,
    ): List<StateType> = List(MINUTES_PER_HOUR) { minute -> activeState(now.withMinute(minute), schedule).state }

    private const val MINUTES_PER_HOUR = 60
}
