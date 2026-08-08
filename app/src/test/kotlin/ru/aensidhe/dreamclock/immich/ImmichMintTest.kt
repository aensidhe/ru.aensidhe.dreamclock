package ru.aensidhe.dreamclock.immich

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class ImmichMintTest {
    private val server = MockWebServer()

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun login_posts_credentials_and_reads_the_token() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"accessToken":"tok123"}"""))
            val api = ImmichClient.api(server.url("/").toString())

            val response = api.login(LoginRequest("a@b.c", "pw"))

            val recorded = server.takeRequest()
            assertEquals("/api/auth/login", recorded.path)
            assertTrue(recorded.body.readUtf8().contains("\"email\":\"a@b.c\""))
            assertEquals("tok123", response.accessToken)
        }

    @Test
    fun create_api_key_sends_bearer_and_read_only_permissions() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"secret":"minted-key"}"""))
            val api = ImmichClient.api(server.url("/").toString())

            val response =
                api.createApiKey("Bearer tok123", CreateApiKeyRequest("Reverie TV", MINT_PERMISSIONS))

            val recorded = server.takeRequest()
            assertEquals("/api/api-keys", recorded.path)
            assertEquals("Bearer tok123", recorded.getHeader("Authorization"))
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("asset.read"))
            assertTrue(body.contains("asset.view"))
            assertTrue(body.contains("asset.download"))
            assertEquals("minted-key", response.secret)
        }
}
