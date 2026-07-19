package ru.aensidhe.dreamclock.core.photos

enum class Orientation { PORTRAIT, LANDSCAPE }

object AssetOrientation {
    private val ROTATING_TAGS = setOf(5, 6, 7, 8)

    fun of(
        width: Int,
        height: Int,
        exifOrientation: Int?,
    ): Orientation {
        if (width <= 0 || height <= 0) return Orientation.LANDSCAPE
        val rotated = exifOrientation in ROTATING_TAGS
        val effectiveWidth = if (rotated) height else width
        val effectiveHeight = if (rotated) width else height
        return if (effectiveHeight > effectiveWidth) Orientation.PORTRAIT else Orientation.LANDSCAPE
    }
}
