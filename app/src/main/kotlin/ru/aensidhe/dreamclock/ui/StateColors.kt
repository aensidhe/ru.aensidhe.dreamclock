package ru.aensidhe.dreamclock.ui

import androidx.compose.ui.graphics.Color
import ru.aensidhe.dreamclock.core.schedule.StateType

fun stateColor(state: StateType): Color =
    when (state) {
        StateType.PLAY -> Color(0xFF7CB342)
        StateType.PREPARE -> Color(0xFFFFB300)
        StateType.SLEEP -> Color(0xFF5E35B1)
    }
