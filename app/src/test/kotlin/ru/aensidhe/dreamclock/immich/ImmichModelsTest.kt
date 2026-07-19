package ru.aensidhe.dreamclock.immich

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test

class ImmichModelsTest {
    @Test
    fun `decodes a search response and ignores unknown keys`() {
        val fixture =
            """
            {
              "albums": { "items": [] },
              "assets": {
                "total": 2,
                "count": 2,
                "items": [
                  {
                    "id": "a1",
                    "type": "IMAGE",
                    "localDateTime": "2026-07-19T14:32:00.000Z",
                    "exifInfo": {
                      "dateTimeOriginal": "2026-07-19T14:32:00.000+02:00",
                      "city": "Berlin",
                      "country": "Germany",
                      "exifImageWidth": 3000,
                      "exifImageHeight": 4000,
                      "orientation": "1"
                    }
                  },
                  { "id": "v1", "type": "VIDEO", "exifInfo": null }
                ],
                "nextPage": "2"
              }
            }
            """.trimIndent()
        val decoded = immichJson.decodeFromString<SearchResponse>(fixture)
        assertEquals(2, decoded.assets.total)
        assertEquals(2, decoded.assets.items.size)
        assertEquals("a1", decoded.assets.items[0].id)
        assertEquals(
            "Berlin",
            decoded.assets.items[0]
                .exifInfo
                ?.city,
        )
        assertEquals("2", decoded.assets.nextPage)
    }

    @Test
    fun `encodes a request with defaults included`() {
        val encoded = immichJson.encodeToString(SearchMetadataRequest(takenAfter = "A", takenBefore = "B"))
        assertTrue(encoded.contains("\"takenAfter\":\"A\""))
        assertTrue(encoded.contains("\"takenBefore\":\"B\""))
        assertTrue(encoded.contains("\"type\":\"IMAGE\""))
        assertTrue(encoded.contains("\"withExif\":true"))
    }
}
