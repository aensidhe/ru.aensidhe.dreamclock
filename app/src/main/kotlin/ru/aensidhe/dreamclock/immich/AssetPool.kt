package ru.aensidhe.dreamclock.immich

import kotlin.random.Random

class AssetPool(
    private val assets: List<SlideAsset>,
    private val random: Random,
) {
    init {
        require(assets.isNotEmpty()) { "assets must not be empty" }
    }

    fun endlessSequence(): Sequence<SlideAsset> =
        sequence {
            while (true) {
                yieldAll(assets.shuffled(random))
            }
        }
}
