package ru.aensidhe.dreamclock.settings

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class LocalizationTest {
    @Test
    fun `russian maps to ru locale`() {
        assertEquals("ru", languageLocale(Language.RU)?.language)
    }

    @Test
    fun `english maps to en locale`() {
        assertEquals("en", languageLocale(Language.EN)?.language)
    }

    @Test
    fun `follow system has no override`() {
        assertNull(languageLocale(Language.FOLLOW_SYSTEM))
    }

    @Test
    fun `unrecognized has no override`() {
        assertNull(languageLocale(Language.UNRECOGNIZED))
    }
}
