package ru.aensidhe.dreamclock.immich

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Runs [block], reporting any runtime failure as an [IOException].
 *
 * OkHttp's dispatcher notifies the caller and then rethrows a non-IOException on its own thread,
 * where nothing can catch it and the process dies. A `SecurityException` from a DNS lookup on a
 * device without the INTERNET permission is one such case. `Error` is left alone: a real fatal
 * should stay fatal.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> asIoFailure(block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw e
    } catch (e: RuntimeException) {
        throw IOException(e)
    }

/** Keeps a failed request on OkHttp's normal failure path instead of killing the process. */
object FatalToIoInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = asIoFailure { chain.proceed(chain.request()) }
}
