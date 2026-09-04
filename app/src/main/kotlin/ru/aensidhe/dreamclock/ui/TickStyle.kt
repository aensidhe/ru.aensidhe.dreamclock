package ru.aensidhe.dreamclock.ui

import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.ScheduleEngine

/**
 * One tick's paint: [base] fills the tick; [incoming] takes over the outer [incomingFraction]
 * of its length, announcing the coming hour on the last four ticks (zero elsewhere).
 */
data class TickStyle(
    val base: Color,
    val incoming: Color,
    val incomingFraction: Float,
)

private const val TICK_COUNT = 60
private const val TAIL_START = 56
private const val TAIL_SPAN = 5f

fun tickStyles(
    now: LocalDateTime,
    schedule: Schedule,
): List<TickStyle> {
    val current = ScheduleEngine.minuteStates(now, schedule).map(::stateColor)
    val next = ScheduleEngine.minuteStates(now.plusHours(1), schedule).map(::stateColor)
    return List(TICK_COUNT) { i ->
        if (i >= TAIL_START) {
            TickStyle(current[i], next[i], (i - TAIL_START + 1) / TAIL_SPAN)
        } else {
            TickStyle(current[i], current[i], 0f)
        }
    }
}
