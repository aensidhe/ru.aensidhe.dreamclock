package ru.aensidhe.dreamclock.core.photos

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.aensidhe.dreamclock.core.time.ClockLocale

data class CaptionSource(
    val takenAt: LocalDateTime?,
    val city: String?,
    val country: String?,
)

data class CaptionLines(
    val dateTime: String?,
    val location: String?,
)

object PhotoCaption {
    private fun jvmLocale(locale: ClockLocale): Locale =
        when (locale) {
            ClockLocale.RU -> Locale.forLanguageTag("ru")
            ClockLocale.EN -> Locale.ENGLISH
        }

    fun format(
        source: CaptionSource,
        locale: ClockLocale,
    ): CaptionLines? {
        val jvm = jvmLocale(locale)
        val dateTime =
            source.takenAt?.let {
                val date = it.format(DateTimeFormatter.ofPattern("d MMMM yyyy", jvm))
                val time = it.format(DateTimeFormatter.ofPattern("HH:mm", jvm))
                "$date | $time"
            }
        val location =
            listOfNotNull(source.city, source.country)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(", ")
                .ifEmpty { null }
        return if (dateTime == null && location == null) null else CaptionLines(dateTime, location)
    }
}
