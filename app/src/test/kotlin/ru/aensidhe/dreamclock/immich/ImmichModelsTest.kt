package ru.aensidhe.dreamclock.immich

import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `decodes the current user and ignores unknown keys`() {
        val decoded =
            immichJson.decodeFromString<ImmichUser>(
                """{ "id": "u1", "name": "Alice", "email": "alice@example.com", "isAdmin": true }""",
            )
        assertEquals("Alice", decoded.name)
        assertEquals("alice@example.com", decoded.email)
    }

    @Test
    fun `display name falls back to email when the name is blank`() {
        assertEquals("Alice", ImmichUser(name = "Alice", email = "alice@example.com").displayName())
        assertEquals("bob@example.com", ImmichUser(name = "", email = "bob@example.com").displayName())
    }

    @Test
    fun `encodes a request with defaults included`() {
        val encoded = immichJson.encodeToString(SearchMetadataRequest(takenAfter = "A", takenBefore = "B"))
        assertTrue(encoded.contains("\"takenAfter\":\"A\""))
        assertTrue(encoded.contains("\"takenBefore\":\"B\""))
        assertTrue(encoded.contains("\"type\":\"IMAGE\""))
        assertTrue(encoded.contains("\"withExif\":true"))
    }

    @Test
    fun `decodes people with faces, hidden flag, empty and null names`() {
        val fixture =
            """
            {
              "id": "a1",
              "type": "IMAGE",
              "people": [
                {
                  "id": "p1",
                  "name": "Anna",
                  "birthDate": null,
                  "thumbnailPath": "/x",
                  "isHidden": false,
                  "faces": [
                    {
                      "id": "f1", "boundingBoxX1": 120, "boundingBoxY1": 10,
                      "boundingBoxX2": 200, "boundingBoxY2": 90,
                      "imageWidth": 4000, "imageHeight": 3000, "sourceType": "machine-learning"
                    }
                  ]
                },
                { "id": "p2", "name": "", "isHidden": false, "faces": [] },
                { "id": "p3", "name": null, "isHidden": false, "faces": [] },
                { "id": "p4", "name": "Ghost", "isHidden": true, "faces": [] },
                { "id": "p5", "name": "Boris", "isHidden": false }
              ]
            }
            """.trimIndent()
        val asset = immichJson.decodeFromString<ImmichAsset>(fixture)
        assertEquals(5, asset.people.size)
        assertEquals("Anna", asset.people[0].name)
        assertEquals(
            120,
            asset.people[0]
                .faces
                .single()
                .boundingBoxX1,
        )
        assertEquals("", asset.people[1].name)
        assertNull(asset.people[2].name)
        assertTrue(asset.people[3].isHidden)
        assertTrue(asset.people[4].faces.isEmpty())
    }

    @Test
    fun `an asset without a people key decodes to an empty list`() {
        val asset = immichJson.decodeFromString<ImmichAsset>("""{ "id": "a1", "type": "IMAGE" }""")
        assertTrue(asset.people.isEmpty())
    }

    @Test
    fun `search request asks for people`() {
        val encoded = immichJson.encodeToString(SearchMetadataRequest(takenAfter = "A", takenBefore = "B"))
        assertTrue(encoded.contains("\"withPeople\":true"))
    }
}
