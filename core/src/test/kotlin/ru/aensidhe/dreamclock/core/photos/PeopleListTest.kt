package ru.aensidhe.dreamclock.core.photos

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.time.ClockLocale

class PeopleListTest {
    @Test
    fun `empty list yields null`() {
        assertNull(PeopleList.join(emptyList(), ClockLocale.EN))
        assertNull(PeopleList.join(emptyList(), ClockLocale.RU))
    }

    @Test
    fun `blank names are dropped and the rest trimmed`() {
        assertNull(PeopleList.join(listOf("", "   "), ClockLocale.EN))
        assertEquals("Anna", PeopleList.join(listOf("  Anna "), ClockLocale.EN))
    }

    @Test
    fun `one name is returned as is`() {
        assertEquals("Anna", PeopleList.join(listOf("Anna"), ClockLocale.EN))
        assertEquals("Аня", PeopleList.join(listOf("Аня"), ClockLocale.RU))
    }

    @Test
    fun `two names use the conjunction without a comma`() {
        assertEquals("Anna and Boris", PeopleList.join(listOf("Anna", "Boris"), ClockLocale.EN))
        assertEquals("Аня и Боря", PeopleList.join(listOf("Аня", "Боря"), ClockLocale.RU))
    }

    @Test
    fun `three names in english use the oxford comma`() {
        assertEquals(
            "Anna, Boris, and Vasya",
            PeopleList.join(listOf("Anna", "Boris", "Vasya"), ClockLocale.EN),
        )
    }

    @Test
    fun `three names in russian have no comma before the conjunction`() {
        assertEquals(
            "Аня, Боря и Вася",
            PeopleList.join(listOf("Аня", "Боря", "Вася"), ClockLocale.RU),
        )
    }

    @Test
    fun `four names follow the same pattern`() {
        assertEquals("A, B, C, and D", PeopleList.join(listOf("A", "B", "C", "D"), ClockLocale.EN))
        assertEquals("А, Б, В и Г", PeopleList.join(listOf("А", "Б", "В", "Г"), ClockLocale.RU))
    }
}
