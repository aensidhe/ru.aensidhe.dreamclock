package ru.aensidhe.dreamclock.core.time

fun hour12(hour: Int): Int {
    val h = hour % 12
    return if (h == 0) 12 else h
}

fun comingHour12(hour: Int): Int = hour12(hour) % 12 + 1
