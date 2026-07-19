package ru.aensidhe.dreamclock.immich

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test

class ImmichRepositoryTest {
    private fun page(
        ids: List<String>,
        nextPage: String?,
    ): String {
        val items =
            ids.joinToString(",") { id ->
                "{\"id\":\"$id\",\"type\":\"IMAGE\",\"exifInfo\":" +
                    "{\"exifImageWidth\":4000,\"exifImageHeight\":3000,\"city\":\"Berlin\",\"country\":\"Germany\"}}"
            }
        val next = if (nextPage == null) "null" else "\"$nextPage\""
        return "{\"assets\":{\"total\":${ids.size},\"count\":${ids.size},\"items\":[$items],\"nextPage\":$next}}"
    }

    private fun jsonResponse(body: String) = MockResponse().addHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `loadAssets paginates within a year and stops at the cap`() =
        runBlocking {
            val server = MockWebServer()
            server.enqueue(jsonResponse(page(listOf("a1", "a2"), "2")))
            server.enqueue(jsonResponse(page(listOf("a3"), null)))
            server.start()
            try {
                val repo =
                    ImmichRepository(
                        apiFactory = { host -> ImmichClient.api(host) },
                        today = { LocalDate.of(2026, 7, 20) },
                        zone = ZoneOffset.ofHours(2),
                    )
                val assets =
                    repo.loadAssets(
                        ImmichCredentials(server.url("/").toString(), "k"),
                        PhotoFetchConfig(daysEitherSide = 3, maxYearsBack = 1),
                    )
                assertEquals(listOf("a1", "a2", "a3"), assets.map { it.id })
                assertEquals(2, server.requestCount)

                val first = server.takeRequest()
                assertTrue(first.body.readUtf8().contains("\"takenAfter\":\"2026-07-17T00:00:00+02:00\""))
            } finally {
                server.shutdown()
            }
        }
}
