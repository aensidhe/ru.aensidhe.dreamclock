package ru.aensidhe.dreamclock.immich

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import ru.aensidhe.dreamclock.core.photos.SimilarTimeWindows

class ImmichProbeTest {
    private val server = MockWebServer()
    private val window = SimilarTimeWindows.windowFor(LocalDate.of(2026, 8, 22), 1, 0)

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun probe_requests_a_full_page_not_a_single_row() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"assets":{"total":7,"count":7,"items":[]}}"""))
            val api = ImmichClient.api(server.url("/").toString())

            val result = ImmichHealth.probe(api, "key", window, ZoneId.of("UTC"))

            val recorded = server.takeRequest()
            assertEquals("/api/search/metadata", recorded.path)
            assertTrue(recorded.body.readUtf8().contains("\"size\":100"))
            assertEquals(ProbeResult.Reachable(7, more = false), result)
        }

    @Test
    fun probe_flags_more_when_a_next_page_exists() =
        runBlocking {
            server.enqueue(
                MockResponse().setBody("""{"assets":{"total":100,"count":100,"items":[],"nextPage":"2"}}"""),
            )
            val api = ImmichClient.api(server.url("/").toString())

            val result = ImmichHealth.probe(api, "key", window, ZoneId.of("UTC"))

            assertEquals(ProbeResult.Reachable(100, more = true), result)
        }
}
