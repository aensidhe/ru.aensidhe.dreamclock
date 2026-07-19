package ru.aensidhe.dreamclock.immich

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ImmichImageUrlsTest {
    @Test
    fun `preview points at the sharp thumbnail variant`() {
        assertEquals(
            "https://immich.example/api/assets/abc/thumbnail?size=preview",
            ImmichImageUrls.preview("https://immich.example", "abc"),
        )
    }

    @Test
    fun `placeholder points at the small thumbnail variant`() {
        assertEquals(
            "https://immich.example/api/assets/abc/thumbnail?size=thumbnail",
            ImmichImageUrls.placeholder("https://immich.example", "abc"),
        )
    }

    @Test
    fun `a trailing slash on the host is normalized away`() {
        assertEquals(
            "https://immich.example/api/assets/xyz/thumbnail?size=preview",
            ImmichImageUrls.preview("https://immich.example/", "xyz"),
        )
    }
}
