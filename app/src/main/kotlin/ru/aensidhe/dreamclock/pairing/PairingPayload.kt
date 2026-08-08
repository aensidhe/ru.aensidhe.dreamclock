package ru.aensidhe.dreamclock.pairing

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PairingEnvelope(
    val iv: String,
    val ciphertext: String,
)

@Serializable
data class PairingPayload(
    val mode: String,
    val host: String,
    val apiKey: String? = null,
    val email: String? = null,
    val password: String? = null,
) {
    companion object {
        const val MODE_KEY = "key"
        const val MODE_LOGIN = "login"
    }
}

object PairingCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseEnvelope(text: String): PairingEnvelope = json.decodeFromString(text)

    fun parsePayload(text: String): PairingPayload = json.decodeFromString(text)

    fun decodeBase64Url(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}
