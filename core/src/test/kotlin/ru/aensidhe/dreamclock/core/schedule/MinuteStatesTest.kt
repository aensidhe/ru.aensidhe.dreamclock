package ru.aensidhe.dreamclock.core.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MinuteStatesTest {
    private val default =
        DaySchedule(
            listOf(
                Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                Window(LocalTime.of(6, 0), StateType.PLAY),
                Window(LocalTime.of(20, 0), StateType.PREPARE),
                Window(LocalTime.of(21, 0), StateType.SLEEP),
            ),
        )

    private fun at(
        h: Int,
        mi: Int,
    ) = LocalDateTime.of(2026, 9, 5, h, mi)

    @Test
    fun `always returns 60 entries`() {
        val s = Schedule(default)
        assertEquals(60, ScheduleEngine.minuteStates(at(0, 0), s).size)
        assertEquals(60, ScheduleEngine.minuteStates(at(23, 59), s).size)
    }

    @Test
    fun `uniform hour returns 60 identical states`() {
        val s = Schedule(default)
        val states = ScheduleEngine.minuteStates(at(10, 17), s)
        assertTrue(states.all { it == StateType.PLAY })
    }

    @Test
    fun `mid-hour window edge splits at its minute`() {
        val day =
            DaySchedule(
                listOf(
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(20, 30), StateType.PREPARE),
                ),
            )
        val states = ScheduleEngine.minuteStates(at(20, 5), Schedule(day))
        assertTrue(states.subList(0, 30).all { it == StateType.SLEEP })
        assertTrue(states.subList(30, 60).all { it == StateType.PREPARE })
    }

    @Test
    fun `date override day resolves through the override`() {
        val holiday = DaySchedule(listOf(Window(LocalTime.MIDNIGHT, StateType.PLAY)))
        val s = Schedule(default, overrides = mapOf(LocalDate.of(2026, 9, 5) to holiday))
        val states = ScheduleEngine.minuteStates(at(3, 0), s)
        assertTrue(states.all { it == StateType.PLAY })
    }

    @Test
    fun `plusHours across midnight resolves through the next day's override`() {
        val holiday = DaySchedule(listOf(Window(LocalTime.MIDNIGHT, StateType.PLAY)))
        val s = Schedule(default, overrides = mapOf(LocalDate.of(2026, 9, 6) to holiday))
        val states = ScheduleEngine.minuteStates(at(23, 30).plusHours(1), s)
        assertTrue(states.all { it == StateType.PLAY })
    }
}
