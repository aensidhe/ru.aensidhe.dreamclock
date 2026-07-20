package ru.aensidhe.dreamclock.immich

object OverlaySuppression {
    fun suppressBottomLeft(slide: RenderSlide): Boolean = slide is RenderPairedPhoto && slide.left.caption != null
}
