package ru.aensidhe.dreamclock.core.time

object EnColloquialFormatter : ColloquialTimeFormatter {
    override fun format(
        hour: Int,
        minute: Int,
    ): String {
        val h12 = hour12(hour)
        val coming = comingHour12(hour)
        return when {
            minute == 0 -> "${englishHour.getValue(h12)} o'clock"
            minute == 15 -> "quarter past ${englishHour.getValue(h12)}"
            minute == 30 -> "half past ${englishHour.getValue(h12)}"
            minute == 45 -> "quarter to ${englishHour.getValue(coming)}"
            minute < 30 -> "${englishCardinal(minute)} past ${englishHour.getValue(h12)}"
            else -> "${englishCardinal(60 - minute)} to ${englishHour.getValue(coming)}"
        }
    }
}
