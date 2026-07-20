package ru.aensidhe.dreamclock.immich

import java.io.IOException
import java.time.ZoneId
import retrofit2.HttpException
import ru.aensidhe.dreamclock.core.photos.DateWindow

sealed interface ProbeResult {
    data object Checking : ProbeResult

    data class Reachable(
        val total: Int?,
    ) : ProbeResult

    data object Unauthorized : ProbeResult

    data object Unreachable : ProbeResult

    data class Error(
        val detail: String,
    ) : ProbeResult
}

object ImmichHealth {
    private const val MAX_DETAIL = 100

    fun truncateDetail(raw: String): String = raw.trim().take(MAX_DETAIL)

    fun classify(
        status: Int,
        body: String,
    ): ProbeResult =
        when {
            status == 401 || status == 403 -> ProbeResult.Unauthorized
            status in 200..299 -> ProbeResult.Reachable(null)
            else -> ProbeResult.Error(truncateDetail(body))
        }

    @Suppress("SwallowedException")
    suspend fun probe(
        api: ImmichApi,
        apiKey: String,
        window: DateWindow,
        zone: ZoneId,
    ): ProbeResult {
        val bounds = ImmichSearchBoundsFactory.forWindow(window, zone)
        return try {
            val response =
                api.searchMetadata(
                    apiKey = apiKey,
                    request =
                        SearchMetadataRequest(
                            takenAfter = bounds.takenAfter,
                            takenBefore = bounds.takenBefore,
                            page = 1,
                            size = 1,
                        ),
                )
            ProbeResult.Reachable(response.assets.total)
        } catch (e: HttpException) {
            classify(
                e.code(),
                e
                    .response()
                    ?.errorBody()
                    ?.string()
                    .orEmpty(),
            )
        } catch (e: IOException) {
            ProbeResult.Unreachable
        }
    }
}
