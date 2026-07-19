package ru.aensidhe.dreamclock.immich

import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.ClockSlide
import ru.aensidhe.dreamclock.core.photos.PairedPhotoSlide
import ru.aensidhe.dreamclock.core.photos.PhotoCaption
import ru.aensidhe.dreamclock.core.photos.PlannedSlide
import ru.aensidhe.dreamclock.core.photos.PlannerAsset
import ru.aensidhe.dreamclock.core.photos.SinglePhotoSlide
import ru.aensidhe.dreamclock.core.photos.VideoSlide
import ru.aensidhe.dreamclock.core.time.ClockLocale

class SlideResolver(
    private val host: String,
    private val captions: Map<String, CaptionSource>,
    private val locale: ClockLocale,
) {
    fun resolve(slide: PlannedSlide): RenderSlide =
        when (slide) {
            is SinglePhotoSlide -> photo(slide.asset)
            is PairedPhotoSlide -> RenderPairedPhoto(photo(slide.left), photo(slide.right))
            is VideoSlide -> RenderClock
            ClockSlide -> RenderClock
        }

    private fun photo(asset: PlannerAsset): RenderPhoto =
        RenderPhoto(
            previewUrl = ImmichImageUrls.preview(host, asset.id),
            placeholderUrl = ImmichImageUrls.placeholder(host, asset.id),
            caption = captions[asset.id]?.let { PhotoCaption.format(it, locale) },
        )
}
