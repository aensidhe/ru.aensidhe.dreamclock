package ru.aensidhe.dreamclock.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingServerAssetsTest {
    @Test
    fun maps_asset_names_to_content_types() {
        assertEquals("text/html; charset=utf-8", PairingServer.contentTypeFor("index.html"))
        assertEquals("text/javascript; charset=utf-8", PairingServer.contentTypeFor("noble-ciphers.js"))
    }
}
