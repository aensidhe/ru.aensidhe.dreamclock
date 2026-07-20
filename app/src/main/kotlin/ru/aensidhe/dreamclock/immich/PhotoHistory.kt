package ru.aensidhe.dreamclock.immich

object PhotoHistory {
    fun oldestYear(
        history: PhotoHistoryProto,
        host: String,
        currentYear: Int,
    ): Int = history.oldestYearByHostMap[host] ?: currentYear

    fun withObservedOldestYear(
        history: PhotoHistoryProto,
        host: String,
        year: Int,
    ): PhotoHistoryProto {
        val existing = history.oldestYearByHostMap[host]
        val next = if (existing == null) year else minOf(existing, year)
        return history.toBuilder().putOldestYearByHost(host, next).build()
    }

    fun resetOnHostChange(
        history: PhotoHistoryProto,
        testedHost: String,
    ): PhotoHistoryProto {
        if (history.lastTestedHost == testedHost) return history
        return history
            .toBuilder()
            .removeOldestYearByHost(testedHost)
            .setLastTestedHost(testedHost)
            .build()
    }
}
