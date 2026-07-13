package ru.aensidhe.dreamclock.settings

import java.util.Locale
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class LocalizationTest {
    private val russian = Locale.forLanguageTag("ru")

    @Test
    fun `explicit russian resolves to ru`() {
        assertEquals("ru", effectiveLocale(Language.RU, Locale.ENGLISH).language)
    }

    @Test
    fun `explicit english resolves to en`() {
        assertEquals("en", effectiveLocale(Language.EN, russian).language)
    }

    @Test
    fun `follow system on a russian device resolves to ru`() {
        assertEquals("ru", effectiveLocale(Language.FOLLOW_SYSTEM, russian).language)
    }

    @Test
    fun `follow system on an english device resolves to en`() {
        assertEquals("en", effectiveLocale(Language.FOLLOW_SYSTEM, Locale.ENGLISH).language)
    }

    @Test
    fun `follow system on any other device falls back to en`() {
        assertEquals("en", effectiveLocale(Language.FOLLOW_SYSTEM, Locale.FRENCH).language)
    }

    @Test
    fun `unrecognized on a russian device resolves to ru`() {
        assertEquals("ru", effectiveLocale(Language.UNRECOGNIZED, russian).language)
    }
}
