package ru.aensidhe.dreamclock.immich

import ru.aensidhe.dreamclock.core.photos.CaptionLines

sealed interface RenderSlide

data class RenderPhoto(
    val previewUrl: String,
    val placeholderUrl: String,
    val caption: CaptionLines?,
) : RenderSlide

data class RenderPairedPhoto(
    val left: RenderPhoto,
    val right: RenderPhoto,
) : RenderSlide

data object RenderClock : RenderSlide
