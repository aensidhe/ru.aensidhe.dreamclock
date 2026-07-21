package ru.aensidhe.dreamclock.immich

import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

/**
 * OkHttp rethrows a non-IOException on its own dispatcher thread after notifying the callback,
 * which kills the process no matter what the calling coroutine does. Those have to become
 * IOException so the normal failure path handles them.
 */
class FatalToIoInterceptorTest {
    @Test
    fun `converts a SecurityException into IOException`() {
        val boom = SecurityException("Permission denied (missing INTERNET permission?)")
        val error = assertFailsWith<IOException> { asIoFailure { throw boom } }
        assertSame(boom, error.cause)
    }

    @Test
    fun `converts any RuntimeException into IOException`() {
        assertFailsWith<IOException> { asIoFailure { throw IllegalStateException("nope") } }
    }

    @Test
    fun `lets IOException through unchanged`() {
        val boom = IOException("unreachable")
        val error = assertFailsWith<IOException> { asIoFailure { throw boom } }
        assertSame(boom, error)
    }

    @Test
    fun `lets Error through so real fatals stay fatal`() {
        assertFailsWith<OutOfMemoryError> { asIoFailure { throw OutOfMemoryError("heap") } }
    }

    @Test
    fun `returns the value when nothing throws`() {
        assertEquals(200, asIoFailure { 200 })
    }
}
