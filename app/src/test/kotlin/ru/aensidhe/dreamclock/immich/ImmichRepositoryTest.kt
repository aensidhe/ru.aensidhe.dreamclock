package ru.aensidhe.dreamclock.immich

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
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
    fun `loadAssets paginates the current year then stops one empty year below the cache`() =
        runBlocking {
            val server = MockWebServer()
            server.enqueue(jsonResponse(page(listOf("a1", "a2"), "2")))
            server.enqueue(jsonResponse(page(listOf("a3"), null)))
            server.enqueue(jsonResponse(page(emptyList(), null)))
            server.start()
            try {
                val repo =
                    ImmichRepository(
                        apiFactory = { host -> ImmichClient.api(host) },
                        today = { LocalDate.of(2026, 7, 20) },
                        zone = ZoneOffset.ofHours(2),
                    )
                val load =
                    repo.loadAssets(
                        ImmichCredentials(server.url("/").toString(), "k"),
                        PhotoFetchConfig(daysEitherSide = 3, maxEmptyYearsBack = 1, cachedOldestYear = 2026),
                    )
                assertEquals(listOf("a1", "a2", "a3"), load.assets.map { it.id })
                assertEquals(2026, load.oldestPopulatedYear)
                assertEquals(3, server.requestCount)
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun `empty years at or above the cache do not count toward the stop streak`() =
        runBlocking {
            val server = MockWebServer()
            server.enqueue(jsonResponse(page(emptyList(), null)))
            server.enqueue(jsonResponse(page(emptyList(), null)))
            server.enqueue(jsonResponse(page(listOf("b1"), null)))
            server.enqueue(jsonResponse(page(emptyList(), null)))
            server.enqueue(jsonResponse(page(emptyList(), null)))
            server.start()
            try {
                val repo =
                    ImmichRepository(
                        apiFactory = { host -> ImmichClient.api(host) },
                        today = { LocalDate.of(2026, 7, 20) },
                        zone = ZoneOffset.ofHours(2),
                    )
                val load =
                    repo.loadAssets(
                        ImmichCredentials(server.url("/").toString(), "k"),
                        PhotoFetchConfig(daysEitherSide = 3, maxEmptyYearsBack = 2, cachedOldestYear = 2024),
                    )
                assertEquals(listOf("b1"), load.assets.map { it.id })
                assertEquals(2024, load.oldestPopulatedYear)
                assertEquals(5, server.requestCount)
            } finally {
                server.shutdown()
            }
        }
}
