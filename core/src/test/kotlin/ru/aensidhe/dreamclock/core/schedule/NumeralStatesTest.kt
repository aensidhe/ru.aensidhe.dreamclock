package ru.aensidhe.dreamclock.core.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class NumeralStatesTest {
    private val default =
        DaySchedule(
            listOf(
                Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                Window(LocalTime.of(7, 0), StateType.PLAY),
                Window(LocalTime.of(20, 0), StateType.PREPARE),
                Window(LocalTime.of(21, 0), StateType.SLEEP),
            ),
        )

    private fun at(
        h: Int,
        mi: Int,
    ) = LocalDateTime.of(2026, 7, 13, h, mi)

    @Test
    fun `always returns twelve entries, index i for numeral i plus one`() {
        assertEquals(12, ScheduleEngine.numeralStates(at(11, 0), Schedule(default)).size)
        assertEquals(12, ScheduleEngine.numeralStates(at(15, 0), Schedule(default)).size)
    }

    @Test
    fun `am half maps numerals to hours 0-11, numeral 12 to midnight`() {
        val states = ScheduleEngine.numeralStates(at(11, 0), Schedule(default))
        // Numerals 1..6 -> 01:00..06:00 sleep; 7..11 -> 07:00..11:00 play; 12 -> 00:00 sleep.
        (0..5).forEach { assertEquals(StateType.SLEEP, states[it]) }
        (6..10).forEach { assertEquals(StateType.PLAY, states[it]) }
        assertEquals(StateType.SLEEP, states[11])
    }

    @Test
    fun `pm half maps numerals to hours 12-23, numeral 12 to noon`() {
        val states = ScheduleEngine.numeralStates(at(15, 0), Schedule(default))
        // Numerals 1..7 -> 13:00..19:00 play; 8 -> 20:00 prepare; 9..11 -> 21:00..23:00 sleep; 12 -> 12:00 play.
        (0..6).forEach { assertEquals(StateType.PLAY, states[it]) }
        assertEquals(StateType.PREPARE, states[7])
        (8..10).forEach { assertEquals(StateType.SLEEP, states[it]) }
        assertEquals(StateType.PLAY, states[11])
    }

    @Test
    fun `half flips exactly at noon`() {
        val s = Schedule(default)
        // Numeral 8: 08:00 play in the morning, 20:00 prepare from noon on.
        assertEquals(StateType.PLAY, ScheduleEngine.numeralStates(at(11, 59), s)[7])
        assertEquals(StateType.PREPARE, ScheduleEngine.numeralStates(at(12, 0), s)[7])
    }

    @Test
    fun `resolves through a date override`() {
        val holiday = DaySchedule(listOf(Window(LocalTime.MIDNIGHT, StateType.PLAY)))
        val s = Schedule(default, overrides = mapOf(LocalDate.of(2026, 7, 13) to holiday))
        ScheduleEngine.numeralStates(at(3, 0), s).forEach { assertEquals(StateType.PLAY, it) }
    }

    @Test
    fun `a numeral shows its hour's first minute, ignoring mid-hour edges`() {
        val day =
            DaySchedule(
                listOf(
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(9, 30), StateType.PLAY),
                ),
            )
        // 09:00 is still sleep even though play starts at 09:30.
        assertEquals(StateType.SLEEP, ScheduleEngine.numeralStates(at(10, 0), Schedule(day))[8])
    }
}
