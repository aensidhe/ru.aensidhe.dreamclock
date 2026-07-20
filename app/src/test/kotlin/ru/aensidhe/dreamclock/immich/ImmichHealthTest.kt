package ru.aensidhe.dreamclock.immich

import kotlin.test.Test
import kotlin.test.assertEquals

class ImmichHealthTest {
    @Test
    fun detailTruncatesToHundred() {
        val raw = "e".repeat(250)
        assertEquals(100, ImmichHealth.truncateDetail(raw).length)
    }

    @Test
    fun detailKeepsShortMessages() {
        assertEquals("boom", ImmichHealth.truncateDetail("boom"))
    }

    @Test
    fun statusForUnauthorizedFromHttp401() {
        assertEquals(ProbeResult.Unauthorized, ImmichHealth.classify(status = 401, body = "nope"))
    }

    @Test
    fun statusForServerErrorCarriesDetail() {
        val result = ImmichHealth.classify(status = 500, body = "internal boom")
        assertEquals(ProbeResult.Error("internal boom"), result)
    }
}
