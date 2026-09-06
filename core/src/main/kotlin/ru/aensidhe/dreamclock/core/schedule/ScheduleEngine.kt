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

    fun numeralStates(
        now: LocalDateTime,
        schedule: Schedule,
    ): List<StateType> {
        val base = if (now.hour < HOURS_PER_HALF_DAY) 0 else HOURS_PER_HALF_DAY
        return List(HOURS_PER_HALF_DAY) { i ->
            val hour = base + (i + 1) % HOURS_PER_HALF_DAY
            activeState(now.withHour(hour).withMinute(0), schedule).state
        }
    }
}

private const val HOURS_PER_HALF_DAY = 12
