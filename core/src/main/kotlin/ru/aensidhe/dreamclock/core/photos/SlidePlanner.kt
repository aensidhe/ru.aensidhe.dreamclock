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

class SlidePlanner(
    private val analogCadence: Int,
) {
    init {
        require(analogCadence >= 1) { "analogCadence must be >= 1" }
    }

    private var pendingPortrait: PlannerAsset? = null
    private var contentSinceClock = 0

    fun offer(asset: PlannerAsset): List<PlannedSlide> {
        val out = mutableListOf<PlannedSlide>()
        val isPortraitPhoto = asset.kind == SlideMediaKind.PHOTO && asset.orientation == Orientation.PORTRAIT
        if (isPortraitPhoto) {
            val held = pendingPortrait
            if (held == null) {
                pendingPortrait = asset
            } else {
                pendingPortrait = null
                emitContent(PairedPhotoSlide(held, asset), out)
            }
        } else if (asset.kind == SlideMediaKind.VIDEO) {
            emitContent(VideoSlide(asset), out)
        } else {
            emitContent(SinglePhotoSlide(asset), out)
        }
        return out
    }

    private fun emitContent(
        slide: PlannedSlide,
        out: MutableList<PlannedSlide>,
    ) {
        out.add(slide)
        contentSinceClock++
        if (contentSinceClock >= analogCadence) {
            out.add(ClockSlide)
            contentSinceClock = 0
        }
    }
}
