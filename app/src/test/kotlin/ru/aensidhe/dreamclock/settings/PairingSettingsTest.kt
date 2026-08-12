package ru.aensidhe.dreamclock.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import ru.aensidhe.dreamclock.R

class PairingSettingsTest {
    @Test
    fun failure_reasons_map_to_specific_labels() {
        assertEquals(R.string.pairing_failed_decrypt, pairingFailureLabel("decrypt"))
        assertEquals(R.string.pairing_failed_host, pairingFailureLabel("host"))
        assertEquals(R.string.pairing_failed_validate, pairingFailureLabel("validate"))
        assertEquals(R.string.pairing_failed_validate, pairingFailureLabel("missing key"))
        assertEquals(R.string.pairing_failed_mint, pairingFailureLabel("mint"))
        assertEquals(R.string.pairing_failed_mint, pairingFailureLabel("missing login"))
    }

    @Test
    fun unknown_failure_reason_falls_back_to_generic() {
        assertEquals(R.string.pairing_failed, pairingFailureLabel("save"))
        assertEquals(R.string.pairing_failed, pairingFailureLabel("whatever"))
    }

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
