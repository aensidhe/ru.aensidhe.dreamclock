package ru.aensidhe.dreamclock.core.time

object RuColloquialFormatter : ColloquialTimeFormatter {
    override fun format(
        hour: Int,
        minute: Int,
    ): String {
        val h12 = hour12(hour)
        val coming = comingHour12(hour)
        val comingOrdinal = comingHourOrdinalGenitive.getValue(coming)
        val comingCardinal = hourCardinalNominative.getValue(coming)
        return when {
            minute == 0 -> exactHour(h12)
            minute == 15 -> "четверть $comingOrdinal"
            minute == 30 -> "половина $comingOrdinal"
            minute == 45 -> "без четверти $comingCardinal"
            minute < 30 -> {
                val num = minuteCardinalFeminine.getValue(minute)
                "$num ${minuteNoun(minute)} $comingOrdinal"
            }
            else -> {
                val rem = 60 - minute
                "без ${minuteGenitive.getValue(rem)} $comingCardinal"
            }
        }
    }

    private fun exactHour(h12: Int): String =
        if (h12 == 1) {
            "час"
        } else {
            "${hourCardinalMasculine.getValue(h12)} ${hourNoun(h12)}"
        }
}
