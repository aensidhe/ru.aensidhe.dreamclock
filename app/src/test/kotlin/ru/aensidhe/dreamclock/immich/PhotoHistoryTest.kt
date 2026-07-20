package ru.aensidhe.dreamclock.immich

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoHistoryTest {
    @Test
    fun oldestYearFallsBackToCurrentWhenNoEntry() {
        val h = PhotoHistoryProto.getDefaultInstance()
        assertEquals(2026, PhotoHistory.oldestYear(h, "https://a", 2026))
    }

    @Test
    fun oldestYearUsesCachedEntry() {
        val h = PhotoHistory.withObservedOldestYear(PhotoHistoryProto.getDefaultInstance(), "https://a", 2001)
        assertEquals(2001, PhotoHistory.oldestYear(h, "https://a", 2026))
    }

    @Test
    fun withObservedOldestYearKeepsTheEarliest() {
        var h = PhotoHistory.withObservedOldestYear(PhotoHistoryProto.getDefaultInstance(), "https://a", 2001)
        h = PhotoHistory.withObservedOldestYear(h, "https://a", 2010)
        assertEquals(2001, PhotoHistory.oldestYear(h, "https://a", 2026))
    }

    @Test
    fun resetOnHostChangeClearsChangedHostButKeepsKeyOnlyChange() {
        var h = PhotoHistory.withObservedOldestYear(PhotoHistoryProto.getDefaultInstance(), "https://a", 2001)
        h = PhotoHistory.resetOnHostChange(h, "https://a")
        assertEquals(2001, PhotoHistory.oldestYear(h, "https://a", 2026))
        h = PhotoHistory.resetOnHostChange(h, "https://b")
        assertEquals(2026, PhotoHistory.oldestYear(h, "https://b", 2026))
    }
}
