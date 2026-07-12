package ru.aensidhe.dreamclock.core.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ScheduleEngineTest {
    private val default =
        DaySchedule(
            listOf(
                Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                Window(LocalTime.of(7, 0), StateType.PLAY),
                Window(LocalTime.of(20, 0), StateType.PREPARE),
                Window(LocalTime.of(21, 0), StateType.SLEEP, "спать"),
            ),
        )

    private fun at(
        y: Int,
        mo: Int,
        d: Int,
        h: Int,
        mi: Int,
    ) = LocalDateTime.of(y, mo, d, h, mi)

    @Test
    fun `picks window by time of day`() {
        val s = Schedule(default)
        assertEquals(StateType.SLEEP, ScheduleEngine.activeState(at(2026, 7, 13, 6, 59), s).state)
        assertEquals(StateType.PLAY, ScheduleEngine.activeState(at(2026, 7, 13, 7, 0), s).state)
        assertEquals(StateType.PREPARE, ScheduleEngine.activeState(at(2026, 7, 13, 20, 30), s).state)
        val night = ScheduleEngine.activeState(at(2026, 7, 13, 21, 0), s)
        assertEquals(StateType.SLEEP, night.state)
        assertEquals("спать", night.textOverride)
    }

    @Test
    fun `day-of-week overrides default`() {
        val weekend =
            DaySchedule(
                listOf(
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(9, 0), StateType.PLAY),
                ),
            )
        val s = Schedule(default, byDayOfWeek = mapOf(DayOfWeek.SUNDAY to weekend))
        // 2026-07-12 is a Sunday.
        assertEquals(StateType.SLEEP, ScheduleEngine.activeState(at(2026, 7, 12, 8, 0), s).state)
        assertEquals(StateType.PLAY, ScheduleEngine.activeState(at(2026, 7, 12, 9, 0), s).state)
    }

    @Test
    fun `date override wins over everything`() {
        val holiday = DaySchedule(listOf(Window(LocalTime.MIDNIGHT, StateType.PLAY)))
        val s =
            Schedule(
                default,
                byDayOfWeek = mapOf(DayOfWeek.MONDAY to default),
                overrides = mapOf(LocalDate.of(2026, 7, 13) to holiday),
            )
        assertEquals(StateType.PLAY, ScheduleEngine.activeState(at(2026, 7, 13, 3, 0), s).state)
    }
}
