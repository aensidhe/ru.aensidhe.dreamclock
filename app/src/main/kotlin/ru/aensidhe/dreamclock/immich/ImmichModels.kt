package ru.aensidhe.dreamclock.immich

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val immichJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

@Serializable
data class SearchMetadataRequest(
    val takenAfter: String,
    val takenBefore: String,
    val type: String = "IMAGE",
    val withExif: Boolean = true,
    val page: Int = 1,
    val size: Int = 100,
)

@Serializable
data class SearchResponse(
    val assets: SearchAssets,
)

@Serializable
data class SearchAssets(
    val total: Int = 0,
    val count: Int = 0,
    val items: List<ImmichAsset> = emptyList(),
    val nextPage: String? = null,
)

@Serializable
data class ImmichAsset(
    val id: String,
    val type: String,
    val localDateTime: String? = null,
    val exifInfo: ExifInfo? = null,
)

@Serializable
data class ExifInfo(
    val dateTimeOriginal: String? = null,
    val city: String? = null,
    val country: String? = null,
    val exifImageWidth: Int? = null,
    val exifImageHeight: Int? = null,
    val orientation: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val accessToken: String,
)

@Serializable
data class CreateApiKeyRequest(
    val name: String,
    val permissions: List<String>,
)

@Serializable
data class CreateApiKeyResponse(
    val secret: String,
)

@Serializable
data class ImmichUser(
    val name: String = "",
    val email: String = "",
) {
    /** The name Immich shows for the account, falling back to the email when the name is blank. */
    fun displayName(): String = name.ifBlank { email }
}

val MINT_PERMISSIONS: List<String> = listOf("asset.read", "asset.view", "asset.download")
