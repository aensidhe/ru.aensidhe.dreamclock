package ru.aensidhe.dreamclock.immich

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test

class ImmichClientTest {
    @Test
    fun `searchMetadata posts to the right path with api key and body`() =
        runBlocking {
            val server = MockWebServer()
            server.enqueue(
                MockResponse()
                    .addHeader("Content-Type", "application/json")
                    .setBody("{\"assets\":{\"total\":0,\"count\":0,\"items\":[],\"nextPage\":null}}"),
            )
            server.start()
            try {
                val api = ImmichClient.api(server.url("/").toString())
                val request = SearchMetadataRequest(takenAfter = "A", takenBefore = "B")
                val response = api.searchMetadata("secret-key", request)
                assertEquals(0, response.assets.total)

                val recorded = server.takeRequest()
                assertEquals("POST", recorded.method)
                assertEquals("/api/search/metadata", recorded.path)
                assertEquals("secret-key", recorded.getHeader("x-api-key"))
                assertTrue(recorded.body.readUtf8().contains("\"takenAfter\":\"A\""))
            } finally {
                server.shutdown()
            }
        }
}
