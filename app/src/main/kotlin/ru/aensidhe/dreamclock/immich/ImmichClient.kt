package ru.aensidhe.dreamclock.immich

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ImmichClient {
    fun defaultClient(): OkHttpClient = OkHttpClient.Builder().addInterceptor(FatalToIoInterceptor).build()

    fun api(
        host: String,
        client: OkHttpClient = defaultClient(),
    ): ImmichApi =
        Retrofit
            .Builder()
            .baseUrl(normalizeBaseUrl(host))
            .client(client)
            .addConverterFactory(immichJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ImmichApi::class.java)

    private fun normalizeBaseUrl(host: String): String = if (host.endsWith("/")) host else "$host/"
}
