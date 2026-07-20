package ru.aensidhe.dreamclock.immich

import java.time.LocalDate
import java.time.ZoneId
import ru.aensidhe.dreamclock.core.photos.SimilarTimeWindows
import ru.aensidhe.dreamclock.core.photos.YearWalk

data class AssetLoad(
    val assets: List<SlideAsset>,
    val oldestPopulatedYear: Int?,
)

class ImmichRepository(
    private val apiFactory: ImmichApiFactory,
    private val today: () -> LocalDate,
    private val zone: ZoneId,
) {
    suspend fun loadAssets(
        credentials: ImmichCredentials,
        config: PhotoFetchConfig,
    ): AssetLoad {
        val api = apiFactory.create(credentials.host)
        val currentYear = today().year
        val all = mutableListOf<SlideAsset>()
        var oldestPopulatedYear: Int? = null
        var emptyBelowOldest = 0
        var candidateYear = currentYear
        while (YearWalk.shouldQueryOlderYear(
                candidateYear,
                config.cachedOldestYear,
                emptyBelowOldest,
                config.maxEmptyYearsBack,
            )
        ) {
            val yearOffset = currentYear - candidateYear
            val year = fetchYear(api, credentials.apiKey, yearOffset, config)
            all += year
            if (year.isNotEmpty()) oldestPopulatedYear = candidateYear
            if (YearWalk.countsTowardEmptyStreak(candidateYear, config.cachedOldestYear)) {
                emptyBelowOldest = if (year.isEmpty()) emptyBelowOldest + 1 else 0
            }
            candidateYear -= 1
        }
        return AssetLoad(all, oldestPopulatedYear)
    }

    private suspend fun fetchYear(
        api: ImmichApi,
        apiKey: String,
        yearOffset: Int,
        config: PhotoFetchConfig,
    ): List<SlideAsset> {
        val window = SimilarTimeWindows.windowFor(today(), config.daysEitherSide, yearOffset)
        val bounds = ImmichSearchBoundsFactory.forWindow(window, zone)
        val result = mutableListOf<SlideAsset>()
        var page: Int? = 1
        while (page != null) {
            val response =
                api.searchMetadata(
                    apiKey = apiKey,
                    request =
                        SearchMetadataRequest(
                            takenAfter = bounds.takenAfter,
                            takenBefore = bounds.takenBefore,
                            page = page,
                            size = config.pageSize,
                        ),
                )
            response.assets.items
                .mapNotNull(AssetMapper::toSlideAsset)
                .forEach(result::add)
            page = response.assets.nextPage?.toIntOrNull()
        }
        return result
    }
}
