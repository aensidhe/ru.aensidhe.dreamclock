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
    fun withObservedOldestYearKeepsTheEarliestRegardlessOfOrder() {
        var later = PhotoHistory.withObservedOldestYear(PhotoHistoryProto.getDefaultInstance(), "https://a", 2001)
        later = PhotoHistory.withObservedOldestYear(later, "https://a", 2010)
        assertEquals(2001, PhotoHistory.oldestYear(later, "https://a", 2026))

        var earlier = PhotoHistory.withObservedOldestYear(PhotoHistoryProto.getDefaultInstance(), "https://a", 2010)
        earlier = PhotoHistory.withObservedOldestYear(earlier, "https://a", 2001)
        assertEquals(2001, PhotoHistory.oldestYear(earlier, "https://a", 2026))
    }

    @Test
    fun testingTheSameHostKeepsItsCache() {
        // Real order: a successful connectivity test marks the host, then a fetch observes its oldest year.
        var h = PhotoHistory.resetOnHostChange(PhotoHistoryProto.getDefaultInstance(), "https://a")
        h = PhotoHistory.withObservedOldestYear(h, "https://a", 2001)
        // Testing the same host again (e.g. only the key changed) keeps the cached oldest year.
        h = PhotoHistory.resetOnHostChange(h, "https://a")
        assertEquals(2001, PhotoHistory.oldestYear(h, "https://a", 2026))
    }

    @Test
    fun testingADifferentHostClearsThatHostsStaleCache() {
        var h = PhotoHistory.resetOnHostChange(PhotoHistoryProto.getDefaultInstance(), "https://a")
        h = PhotoHistory.withObservedOldestYear(h, "https://b", 1995)
        // Switching to and testing host b clears b's stale cache so it re-walks from scratch.
        h = PhotoHistory.resetOnHostChange(h, "https://b")
        assertEquals(2026, PhotoHistory.oldestYear(h, "https://b", 2026))
    }
}
