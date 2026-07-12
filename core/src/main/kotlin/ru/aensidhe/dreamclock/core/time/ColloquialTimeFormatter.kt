package ru.aensidhe.dreamclock.core.time

interface ColloquialTimeFormatter {
    /** hour in 0..23, minute in 0..59. */
    fun format(
        hour: Int,
        minute: Int,
    ): String
}

fun colloquialFormatter(locale: ClockLocale): ColloquialTimeFormatter =
    when (locale) {
        ClockLocale.RU -> RuColloquialFormatter
        ClockLocale.EN -> EnColloquialFormatter
    }

internal object RuColloquialFormatter : ColloquialTimeFormatter {
    override fun format(
        hour: Int,
        minute: Int,
    ): String = TODO("Task 5")
}

internal object EnColloquialFormatter : ColloquialTimeFormatter {
    override fun format(
        hour: Int,
        minute: Int,
    ): String = TODO("Task 6")
}
