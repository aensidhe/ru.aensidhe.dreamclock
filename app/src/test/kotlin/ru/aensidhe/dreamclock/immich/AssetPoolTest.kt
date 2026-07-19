package ru.aensidhe.dreamclock.immich

import kotlin.random.Random
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.Orientation
import ru.aensidhe.dreamclock.core.photos.SlideMediaKind

class AssetPoolTest {
    private fun asset(id: String) =
        SlideAsset(id, SlideMediaKind.PHOTO, Orientation.LANDSCAPE, CaptionSource(null, null, null))

    @Test
    fun `cycles deterministically with a seeded random`() {
        val items = listOf(asset("a"), asset("b"), asset("c"))
        val expected =
            buildList {
                val r = Random(42)
                repeat(2) { addAll(items.shuffled(r)) }
            }
        val actual = AssetPool(items, Random(42)).endlessSequence().take(6).toList()
        assertEquals(expected, actual)
        assertEquals(6, actual.size)
        assertEquals(listOf("a", "a", "b", "b", "c", "c").sorted(), actual.map { it.id }.sorted())
    }
}
