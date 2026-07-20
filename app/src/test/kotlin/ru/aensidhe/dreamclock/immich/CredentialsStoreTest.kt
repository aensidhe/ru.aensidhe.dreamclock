package ru.aensidhe.dreamclock.immich

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class CredentialsStoreTest {
    @Test
    fun `the no-credentials store yields nothing`() {
        assertNull(NoCredentialsStore.current())
    }

    @Test
    fun `a static store yields its credentials`() {
        val creds = ImmichCredentials("https://immich.example", "secret")
        assertEquals(creds, StaticCredentialsStore(creds).current())
    }

    @Test
    fun `a static store built from null yields nothing`() {
        assertNull(StaticCredentialsStore(null).current())
    }
}
