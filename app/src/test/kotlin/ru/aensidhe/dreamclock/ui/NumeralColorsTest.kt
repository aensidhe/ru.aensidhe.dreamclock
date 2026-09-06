package ru.aensidhe.dreamclock.ui

import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.schedule.DaySchedule
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.core.schedule.Window

class NumeralColorsTest {
    private val default =
        DaySchedule(
            listOf(
                Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                Window(LocalTime.of(6, 0), StateType.PLAY),
                Window(LocalTime.of(20, 0), StateType.PREPARE),
                Window(LocalTime.of(21, 0), StateType.SLEEP),
            ),
        )

    private val neutral = Color(0xFFEEF2F8)
    private val amber = Color(0xFFFFB300)
    private val purple = Color(0xFF5E35B1)

    @Test
    fun `play hours keep the neutral numeral colour`() {
        val colors = numeralColors(LocalDateTime.of(2026, 7, 13, 11, 0), Schedule(default))
        // Numerals 6..11 -> 06:00..11:00 play: neutral, never green.
        (5..10).forEach { assertEquals(neutral, colors[it]) }
    }

    @Test
    fun `prepare and sleep hours take their state colours`() {
        val colors = numeralColors(LocalDateTime.of(2026, 7, 13, 15, 0), Schedule(default))
        // Numeral 8 -> 20:00 prepare; 9..11 -> 21:00..23:00 sleep; 12 -> noon play.
        assertEquals(amber, colors[7])
        (8..10).forEach { assertEquals(purple, colors[it]) }
        assertEquals(neutral, colors[11])
    }
}
