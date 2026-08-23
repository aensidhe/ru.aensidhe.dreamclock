package ru.aensidhe.dreamclock.immich

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import ru.aensidhe.dreamclock.core.photos.SimilarTimeWindows

class ImmichProbeTest {
    private val server = MockWebServer()
    private val window = SimilarTimeWindows.windowFor(LocalDate.of(2026, 8, 22), 1, 0)

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun probe_reports_reachable_when_the_search_endpoint_answers() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"assets":{"total":0,"count":0,"items":[]}}"""))
            val api = ImmichClient.api(server.url("/").toString())

            val result = ImmichHealth.probe(api, "key", window, ZoneId.of("UTC"))

            assertEquals("/api/search/metadata", server.takeRequest().path)
            assertEquals(ProbeResult.Reachable, result)
        }
}
