package ru.aensidhe.dreamclock.ui

import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.ScheduleEngine
import ru.aensidhe.dreamclock.core.schedule.StateType

internal val numeralColor = Color(0xFFEEF2F8)

/**
 * Per-numeral colours for the current half-day: numeral N takes the colour of the state at the
 * first minute of hour N (AM before noon, PM after). PLAY keeps the neutral numeral colour so the
 * dial only highlights the prepare and sleep hours.
 */
fun numeralColors(
    now: LocalDateTime,
    schedule: Schedule,
): List<Color> =
    ScheduleEngine.numeralStates(now, schedule).map { state ->
        if (state == StateType.PLAY) numeralColor else stateColor(state)
    }
