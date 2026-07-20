package ru.aensidhe.dreamclock.core.photos

enum class SlideMediaKind { PHOTO, VIDEO }

data class PlannerAsset(
    val id: String,
    val kind: SlideMediaKind,
    val orientation: Orientation,
)

sealed interface PlannedSlide

data class SinglePhotoSlide(
    val asset: PlannerAsset,
) : PlannedSlide

data class PairedPhotoSlide(
    val left: PlannerAsset,
    val right: PlannerAsset,
) : PlannedSlide

data class VideoSlide(
    val asset: PlannerAsset,
) : PlannedSlide

data object ClockSlide : PlannedSlide

class SlidePlanner {
    private var pendingPortrait: PlannerAsset? = null

    fun offer(asset: PlannerAsset): List<PlannedSlide> {
        val isPortraitPhoto = asset.kind == SlideMediaKind.PHOTO && asset.orientation == Orientation.PORTRAIT
        if (isPortraitPhoto) {
            val held = pendingPortrait
            return if (held == null) {
                pendingPortrait = asset
                emptyList()
            } else {
                pendingPortrait = null
                listOf(PairedPhotoSlide(held, asset))
            }
        }
        return if (asset.kind == SlideMediaKind.VIDEO) listOf(VideoSlide(asset)) else listOf(SinglePhotoSlide(asset))
    }
}
