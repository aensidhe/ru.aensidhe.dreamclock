package ru.aensidhe.dreamclock.ui

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

private const val DISK_CACHE_BYTES = 256L * 1024 * 1024

object ImmichImageLoader {
    fun create(
        context: Context,
        host: String,
        apiKey: String,
    ): ImageLoader {
        val immichHost = host.toHttpUrlOrNull()?.host
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val stamped =
                        if (request.url.host == immichHost) {
                            request.newBuilder().header("x-api-key", apiKey).build()
                        } else {
                            request
                        }
                    chain.proceed(stamped)
                }.build()
        return ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("immich_images").toOkioPath())
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }.crossfade(true)
            .build()
    }
}
