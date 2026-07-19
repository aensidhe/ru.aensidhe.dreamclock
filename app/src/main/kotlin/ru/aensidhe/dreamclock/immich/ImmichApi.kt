package ru.aensidhe.dreamclock.immich

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ImmichApi {
    @POST("api/search/metadata")
    suspend fun searchMetadata(
        @Header("x-api-key") apiKey: String,
        @Body request: SearchMetadataRequest,
    ): SearchResponse
}

fun interface ImmichApiFactory {
    fun create(host: String): ImmichApi
}
