package ru.aensidhe.dreamclock.immich

data class ImmichCredentials(
    val host: String,
    val apiKey: String,
)

data class PhotoFetchConfig(
    val daysEitherSide: Int,
    val maxYearsBack: Int,
    val pageSize: Int = 100,
)
