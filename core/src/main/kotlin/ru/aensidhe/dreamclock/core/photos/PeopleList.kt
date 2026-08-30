package ru.aensidhe.dreamclock.core.photos

import ru.aensidhe.dreamclock.core.time.ClockLocale

object PeopleList {
    fun join(
        names: List<String>,
        locale: ClockLocale,
    ): String? {
        val clean = names.map { it.trim() }.filter { it.isNotEmpty() }
        return when (clean.size) {
            0 -> null
            1 -> clean[0]
            2 -> "${clean[0]} ${conjunction(locale)} ${clean[1]}"
            else -> clean.dropLast(1).joinToString(", ") + lastSeparator(locale) + clean.last()
        }
    }

    private fun conjunction(locale: ClockLocale): String =
        when (locale) {
            ClockLocale.RU -> "и"
            ClockLocale.EN -> "and"
        }

    // English takes the Oxford comma; Russian never puts a comma before a single «и».
    private fun lastSeparator(locale: ClockLocale): String =
        when (locale) {
            ClockLocale.RU -> " и "
            ClockLocale.EN -> ", and "
        }
}
