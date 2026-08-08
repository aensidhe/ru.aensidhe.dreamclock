package ru.aensidhe.dreamclock.pairing

object PairingCountdown {
    fun format(remainingSeconds: Int): String {
        val safe = remainingSeconds.coerceAtLeast(0)
        return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
    }
}
