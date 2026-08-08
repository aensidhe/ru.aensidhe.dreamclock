package ru.aensidhe.dreamclock.pairing

import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import ru.aensidhe.dreamclock.core.pairing.PairingCrypto
import ru.aensidhe.dreamclock.core.photos.SimilarTimeWindows
import ru.aensidhe.dreamclock.immich.CreateApiKeyRequest
import ru.aensidhe.dreamclock.immich.ImmichApi
import ru.aensidhe.dreamclock.immich.ImmichCredentials
import ru.aensidhe.dreamclock.immich.ImmichHealth
import ru.aensidhe.dreamclock.immich.LoginRequest
import ru.aensidhe.dreamclock.immich.MINT_PERMISSIONS
import ru.aensidhe.dreamclock.immich.ProbeResult

sealed interface PairingOutcome {
    data object Saved : PairingOutcome

    data class Failed(
        val reason: String,
    ) : PairingOutcome
}

class PairingController(
    private val key: ByteArray,
    private val apiFactory: (String) -> ImmichApi,
    private val zone: ZoneId,
    private val save: suspend (ImmichCredentials) -> Unit,
) {
    @Suppress("ReturnCount")
    suspend fun receive(
        envelopeJson: String,
        today: LocalDate,
        daysEitherSide: Int,
    ): PairingOutcome {
        val payload =
            runCatching {
                val envelope = PairingCodec.parseEnvelope(envelopeJson)
                val iv = PairingCodec.decodeBase64Url(envelope.iv)
                val ciphertext = PairingCodec.decodeBase64Url(envelope.ciphertext)
                PairingCodec.parsePayload(String(PairingCrypto.decrypt(key, iv, ciphertext)))
            }.getOrElse { return PairingOutcome.Failed("decrypt") }

        if (payload.host.isBlank()) return PairingOutcome.Failed("host")
        val api = runCatching { apiFactory(payload.host) }.getOrElse { return PairingOutcome.Failed("host") }
        return when (payload.mode) {
            PairingPayload.MODE_KEY -> saveValidatedKey(api, payload.host, payload.apiKey, today, daysEitherSide)
            PairingPayload.MODE_LOGIN -> mintAndSave(api, payload.host, payload.email, payload.password)
            else -> PairingOutcome.Failed("mode")
        }
    }

    private suspend fun saveValidatedKey(
        api: ImmichApi,
        host: String,
        apiKey: String?,
        today: LocalDate,
        daysEitherSide: Int,
    ): PairingOutcome {
        if (apiKey.isNullOrBlank()) return PairingOutcome.Failed("missing key")
        val window = SimilarTimeWindows.windowFor(today, daysEitherSide, 0)
        return when (ImmichHealth.probe(api, apiKey, window, zone)) {
            is ProbeResult.Reachable ->
                runCatching {
                    save(ImmichCredentials(host, apiKey))
                    PairingOutcome.Saved
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    PairingOutcome.Failed("save")
                }
            else -> PairingOutcome.Failed("validate")
        }
    }

    private suspend fun mintAndSave(
        api: ImmichApi,
        host: String,
        email: String?,
        password: String?,
    ): PairingOutcome {
        if (email.isNullOrBlank() || password.isNullOrBlank()) return PairingOutcome.Failed("missing login")
        return runCatching {
            val token = api.login(LoginRequest(email, password)).accessToken
            val secret =
                api.createApiKey("Bearer $token", CreateApiKeyRequest("Reverie TV", MINT_PERMISSIONS)).secret
            save(ImmichCredentials(host, secret))
            PairingOutcome.Saved
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            PairingOutcome.Failed("mint")
        }
    }
}
