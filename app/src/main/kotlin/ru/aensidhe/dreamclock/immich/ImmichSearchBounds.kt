package ru.aensidhe.dreamclock.immich

import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import ru.aensidhe.dreamclock.core.photos.DateWindow

data class ImmichSearchBounds(
    val takenAfter: String,
    val takenBefore: String,
)

object ImmichSearchBoundsFactory {
    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun forWindow(
        window: DateWindow,
        zone: ZoneId,
    ): ImmichSearchBounds {
        val after = window.fromInclusive.atStartOfDay(zone).toOffsetDateTime()
        val before =
            window.toInclusive
                .atTime(LocalTime.of(23, 59, 59))
                .atZone(zone)
                .toOffsetDateTime()
        return ImmichSearchBounds(after.format(FORMAT), before.format(FORMAT))
    }
}
