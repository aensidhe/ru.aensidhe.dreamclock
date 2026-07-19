package ru.aensidhe.dreamclock.immich

import java.time.LocalDateTime
import java.time.OffsetDateTime
import ru.aensidhe.dreamclock.core.photos.AssetOrientation
import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.SlideMediaKind

object AssetMapper {
    fun toSlideAsset(asset: ImmichAsset): SlideAsset? {
        if (asset.type != "IMAGE" || asset.id.isBlank()) return null
        val exif = asset.exifInfo
        val orientation =
            AssetOrientation.of(
                width = exif?.exifImageWidth ?: 0,
                height = exif?.exifImageHeight ?: 0,
                exifOrientation = exif?.orientation?.trim()?.toIntOrNull(),
            )
        return SlideAsset(
            id = asset.id,
            kind = SlideMediaKind.PHOTO,
            orientation = orientation,
            caption =
                CaptionSource(
                    takenAt = parseTakenAt(exif?.dateTimeOriginal ?: asset.localDateTime),
                    city = exif?.city,
                    country = exif?.country,
                ),
        )
    }

    private fun parseTakenAt(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toLocalDateTime() }
            .recoverCatching { LocalDateTime.parse(value) }
            .getOrNull()
    }
}
