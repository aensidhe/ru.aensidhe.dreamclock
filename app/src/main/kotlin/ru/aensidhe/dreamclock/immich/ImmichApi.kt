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

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest,
    ): LoginResponse

    @POST("api/api-keys")
    suspend fun createApiKey(
        @Header("Authorization") authorization: String,
        @Body request: CreateApiKeyRequest,
    ): CreateApiKeyResponse
}

fun interface ImmichApiFactory {
    fun create(host: String): ImmichApi
}
