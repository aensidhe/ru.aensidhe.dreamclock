package ru.aensidhe.dreamclock.settings

import android.content.Context
import androidx.annotation.StringRes
import ru.aensidhe.dreamclock.R
import ru.aensidhe.dreamclock.immich.ProbeResult

const val SCREENSAVER_SETTINGS_ACTION: String = "android.settings.DREAM_SETTINGS"
const val TV_SETTINGS_PACKAGE: String = "com.android.tv.settings"
const val DAYDREAM_ACTIVITY: String =
    "com.android.tv.settings.device.display.daydream.DaydreamActivity"
const val ADB_SCREENSAVER_COMMAND: String =
    "adb shell settings put secure screensaver_components ru.aensidhe.dreamclock/.dream.TvDreamService"

@StringRes
fun languageLabel(language: Language): Int =
    when (language) {
        Language.FOLLOW_SYSTEM, Language.UNRECOGNIZED -> R.string.lang_follow_system
        Language.RU -> R.string.lang_ru
        Language.EN -> R.string.lang_en
    }

fun probeStatusLabel(
    context: Context,
    result: ProbeResult,
): String =
    when (result) {
        ProbeResult.Checking -> context.getString(R.string.probe_checking)
        ProbeResult.Reachable -> context.getString(R.string.probe_connected)
        ProbeResult.Unauthorized -> context.getString(R.string.probe_unauthorized)
        ProbeResult.Unreachable -> context.getString(R.string.probe_unreachable)
        is ProbeResult.Error -> context.getString(R.string.probe_error, result.detail)
    }
