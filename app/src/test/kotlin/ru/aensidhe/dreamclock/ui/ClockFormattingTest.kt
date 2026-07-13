package ru.aensidhe.dreamclock.ui

import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.schedule.DaySchedule
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.core.schedule.Window
import ru.aensidhe.dreamclock.settings.Language
import ru.aensidhe.dreamclock.settings.Settings

class ClockFormattingTest {
    private val schedule =
        Schedule(
            DaySchedule(
                listOf(
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(21, 0), StateType.SLEEP),
                ),
            ),
        )

    @Test
    fun `russian digital and colloquial with seconds`() {
        val settings =
            Settings
                .newBuilder()
                .setLanguage(Language.RU)
                .setShowColloquial(true)
                .setShowSeconds(true)
                .build()
        val ui =
            buildClockUiState(
                LocalDateTime.of(2026, 7, 13, 21, 45, 5),
                settings,
                schedule,
                Locale.ENGLISH,
            ) { _, _ -> "спать" }
        assertEquals("21:45:05", ui.digital)
        assertEquals("без четверти десять", ui.colloquial)
        assertEquals("спать", ui.statusText)
    }

    @Test
    fun `seconds off and colloquial off`() {
        val settings =
            Settings
                .newBuilder()
                .setLanguage(Language.RU)
                .setShowColloquial(false)
                .setShowSeconds(false)
                .build()
        val ui =
            buildClockUiState(
                LocalDateTime.of(2026, 7, 13, 21, 45, 5),
                settings,
                schedule,
                Locale.ENGLISH,
            ) { _, _ -> "спать" }
        assertEquals("21:45", ui.digital)
        assertEquals(null, ui.colloquial)
    }

    @Test
    fun `follow system picks colloquial language from the system locale`() {
        val settings =
            Settings
                .newBuilder()
                .setLanguage(Language.FOLLOW_SYSTEM)
                .setShowColloquial(true)
                .setShowSeconds(false)
                .build()
        val now = LocalDateTime.of(2026, 7, 13, 21, 45, 5)
        val ru = buildClockUiState(now, settings, schedule, Locale.forLanguageTag("ru")) { _, _ -> "" }.colloquial
        val en = buildClockUiState(now, settings, schedule, Locale.ENGLISH) { _, _ -> "" }.colloquial
        assertEquals("без четверти десять", ru)
        assertNotEquals(ru, en)
    }
}
