package ru.aensidhe.dreamclock.immich

import java.io.IOException
import java.time.ZoneId
import retrofit2.HttpException
import ru.aensidhe.dreamclock.core.photos.DateWindow

sealed interface ProbeResult {
    data object Checking : ProbeResult

    data class Reachable(
        val total: Int?,
        val more: Boolean = false,
    ) : ProbeResult

    data object Unauthorized : ProbeResult

    data object Unreachable : ProbeResult

    data class Error(
        val detail: String,
    ) : ProbeResult
}

object ImmichHealth {
    private const val MAX_DETAIL = 100

    // Immich reports assets.total as the number of items in the returned page, not the grand
    // total, so a size-1 probe always counts 1. Request a real page and, when a next page
    // exists, tell the label there are more than we counted.
    private const val PROBE_PAGE_SIZE = 100

    fun truncateDetail(raw: String): String = raw.trim().take(MAX_DETAIL)

    fun classify(
        status: Int,
        body: String,
    ): ProbeResult =
        if (status == 401 || status == 403) ProbeResult.Unauthorized else ProbeResult.Error(truncateDetail(body))

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
                            size = PROBE_PAGE_SIZE,
                        ),
                )
            ProbeResult.Reachable(response.assets.total, more = response.assets.nextPage != null)
        } catch (e: HttpException) {
            val body =
                runCatching {
                    e
                        .response()
                        ?.errorBody()
                        ?.string()
                }.getOrNull().orEmpty()
            classify(e.code(), body)
        } catch (e: IOException) {
            ProbeResult.Unreachable
        }
    }
}
