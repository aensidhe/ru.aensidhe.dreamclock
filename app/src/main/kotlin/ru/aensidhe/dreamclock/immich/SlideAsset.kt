package ru.aensidhe.dreamclock.immich

import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.Orientation
import ru.aensidhe.dreamclock.core.photos.PlannerAsset
import ru.aensidhe.dreamclock.core.photos.SlideMediaKind

data class SlideAsset(
    val id: String,
    val kind: SlideMediaKind,
    val orientation: Orientation,
    val caption: CaptionSource,
) {
    fun toPlannerAsset(): PlannerAsset = PlannerAsset(id, kind, orientation)
}
