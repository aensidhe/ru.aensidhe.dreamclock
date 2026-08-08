package ru.aensidhe.dreamclock.core.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterfaceSelectionTest {
    private fun up(
        name: String,
        addr: String,
    ) = NicAddress(name, addr, isUp = true, isLoopback = false)

    @Test
    fun drops_loopback_down_and_link_local() {
        val records =
            listOf(
                NicAddress("lo", "127.0.0.1", isUp = true, isLoopback = true),
                NicAddress("eth0", "192.168.1.42", isUp = false, isLoopback = false),
                up("eth1", "169.254.5.5"),
                up("eth2", "fe80::1"),
                up("eth3", "192.168.1.50"),
            )
        assertEquals(
            listOf(PairingAddress("eth3", "192.168.1.50", AddressFamily.IPV4)),
            InterfaceSelection.candidates(records),
        )
    }

    @Test
    fun orders_by_family_and_scope() {
        val records =
            listOf(
                up("a", "2001:db8::1"), // v6 global
                up("b", "8.8.8.8"), // v4 global
                up("c", "fd00::1"), // v6 ULA
                up("d", "10.0.0.5"), // v4 site-local
            )
        assertEquals(
            listOf("10.0.0.5", "8.8.8.8", "fd00::1", "2001:db8::1"),
            InterfaceSelection.candidates(records).map { it.address },
        )
    }

    @Test
    fun resolve_prefers_saved_name_and_family() {
        val records = listOf(up("eth0", "192.168.1.42"), up("wlan0", "10.0.0.9"))
        assertEquals(
            "10.0.0.9",
            InterfaceSelection.resolve(records, "wlan0", AddressFamily.IPV4)?.address,
        )
    }

    @Test
    fun resolve_falls_back_to_top_when_saved_is_gone() {
        val records = listOf(up("eth0", "192.168.1.42"))
        assertEquals(
            "192.168.1.42",
            InterfaceSelection.resolve(records, "wlan0", AddressFamily.IPV4)?.address,
        )
    }

    @Test
    fun resolve_is_null_when_nothing_qualifies() {
        assertNull(InterfaceSelection.resolve(emptyList(), null, null))
    }
}
