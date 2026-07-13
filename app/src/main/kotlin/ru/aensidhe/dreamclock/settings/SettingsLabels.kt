package ru.aensidhe.dreamclock.settings

import androidx.annotation.StringRes
import ru.aensidhe.dreamclock.R

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

@StringRes
fun colorModeLabel(mode: ColorRenderModeProto): Int =
    when (mode) {
        ColorRenderModeProto.TEXT_TINT, ColorRenderModeProto.UNRECOGNIZED -> R.string.render_text_tint_label
        ColorRenderModeProto.PANEL_TINT -> R.string.render_panel_tint_label
        ColorRenderModeProto.FULL_SCRIM -> R.string.render_full_scrim_label
        ColorRenderModeProto.ACCENT -> R.string.render_accent_label
    }

@StringRes
fun colorModeDescription(mode: ColorRenderModeProto): Int =
    when (mode) {
        ColorRenderModeProto.TEXT_TINT, ColorRenderModeProto.UNRECOGNIZED -> R.string.render_text_tint_desc
        ColorRenderModeProto.PANEL_TINT -> R.string.render_panel_tint_desc
        ColorRenderModeProto.FULL_SCRIM -> R.string.render_full_scrim_desc
        ColorRenderModeProto.ACCENT -> R.string.render_accent_desc
    }
