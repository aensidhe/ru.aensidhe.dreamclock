package ru.aensidhe.dreamclock.core.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingUrlTest {
    @Test
    fun ipv4_stays_bare() {
        assertEquals(
            "http://192.168.1.42:8973/#k=AAABBB",
            PairingUrl.build("192.168.1.42", 8973, "AAABBB"),
        )
    }

    @Test
    fun ipv6_is_bracketed() {
        assertEquals(
            "http://[fd00::1a2b]:8973/#k=AAABBB",
            PairingUrl.build("fd00::1a2b", 8973, "AAABBB"),
        )
    }

    @Test
    fun hostname_stays_bare() {
        assertEquals(
            "http://immich.local:8973/#k=K",
            PairingUrl.build("immich.local", 8973, "K"),
        )
    }
}
