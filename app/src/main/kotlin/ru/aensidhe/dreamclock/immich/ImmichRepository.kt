package ru.aensidhe.dreamclock.immich

import java.time.LocalDate
import java.time.ZoneId
import ru.aensidhe.dreamclock.core.photos.SimilarTimeWindows
import ru.aensidhe.dreamclock.core.photos.YearWalk

class ImmichRepository(
    private val apiFactory: ImmichApiFactory,
    private val today: () -> LocalDate,
    private val zone: ZoneId,
) {
    suspend fun loadAssets(
        credentials: ImmichCredentials,
        config: PhotoFetchConfig,
    ): List<SlideAsset> {
        val api = apiFactory.create(credentials.host)
        val all = mutableListOf<SlideAsset>()
        var yearsQueried = 0
        var emptyStreak = 0
        while (true) {
            val year = fetchYear(api, credentials.apiKey, yearsQueried, config)
            all += year
            emptyStreak = if (year.isEmpty()) emptyStreak + 1 else 0
            yearsQueried += 1
            if (!YearWalk.shouldQueryNextYear(yearsQueried, emptyStreak, config.maxYearsBack)) break
        }
        return all
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
