package ru.aensidhe.dreamclock.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingSettingsTest {
    @Test
    fun persists_interface_name_and_family() {
        val settings =
            Settings
                .newBuilder()
                .setPairingInterfaceName("eth0")
                .setPairingAddressFamily(AddressFamilyProto.IPV6)
                .build()
        assertEquals("eth0", settings.pairingInterfaceName)
        assertEquals(AddressFamilyProto.IPV6, settings.pairingAddressFamily)
    }

    @Test
    fun defaults_address_family_to_ipv4_and_blank_interface() {
        val settings = Settings.getDefaultInstance()
        assertEquals(AddressFamilyProto.IPV4, settings.pairingAddressFamily)
        assertEquals("", settings.pairingInterfaceName)
    }
}
