package ru.aensidhe.dreamclock.core.time

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class RussianNumberWordsTest {
    @Test
    fun `plural one few many`() {
        assertEquals(PluralForm.ONE, russianPlural(1))
        assertEquals(PluralForm.ONE, russianPlural(21))
        assertEquals(PluralForm.FEW, russianPlural(2))
        assertEquals(PluralForm.FEW, russianPlural(24))
        assertEquals(PluralForm.MANY, russianPlural(5))
        assertEquals(PluralForm.MANY, russianPlural(11))
        assertEquals(PluralForm.MANY, russianPlural(12))
        assertEquals(PluralForm.MANY, russianPlural(14))
        assertEquals(PluralForm.MANY, russianPlural(20))
    }

    @Test
    fun `minute noun agreement`() {
        assertEquals("минута", minuteNoun(1))
        assertEquals("минуты", minuteNoun(3))
        assertEquals("минут", minuteNoun(11))
        assertEquals("минута", minuteNoun(21))
    }

    @Test
    fun `hour noun agreement`() {
        assertEquals("час", hourNoun(1))
        assertEquals("часа", hourNoun(2))
        assertEquals("часов", hourNoun(9))
        assertEquals("часов", hourNoun(12))
    }

    @Test
    fun `word tables`() {
        assertEquals("десятого", comingHourOrdinalGenitive[10])
        assertEquals("десять", hourCardinalNominative[10])
        assertEquals("час", hourCardinalNominative[1])
        assertEquals("двадцать три", minuteCardinalFeminine[23])
        assertEquals("две", minuteCardinalFeminine[2])
        assertEquals("двадцати пяти", minuteGenitive[25])
    }
}
