package ru.aensidhe.dreamclock.settings

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings =
        Settings
            .newBuilder()
            .setShowColloquial(true)
            .setShowSeconds(true)
            .setShowAnalogSlide(true)
            .setPhotosEnabled(false)
            .setImmichHost("")
            .setDaysEitherSide(3)
            .setMaxYearsBack(0)
            .setPhotoIntervalSeconds(8)
            .setAnalogEveryNSlides(5)
            .setMaxClockGapSeconds(300)
            .setAnalogSlideSeconds(10)
            .build()

    override suspend fun readFrom(input: InputStream): Settings = Settings.parseFrom(input)

    override suspend fun writeTo(
        t: Settings,
        output: OutputStream,
    ) = t.writeTo(output)
}
