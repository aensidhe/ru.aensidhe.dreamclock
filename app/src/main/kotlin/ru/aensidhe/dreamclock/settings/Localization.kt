package ru.aensidhe.dreamclock.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import ru.aensidhe.dreamclock.core.time.ClockLocale

private val RUSSIAN: Locale = Locale.forLanguageTag("ru")

/**
 * Reverie ships Russian and English only. FOLLOW_SYSTEM (and the synthetic protobuf UNRECOGNIZED)
 * resolve to Russian when the system locale is Russian, otherwise English — so the spoken time,
 * status text and settings labels always agree on one language.
 */
fun effectiveLocale(
    language: Language,
    systemLocale: Locale,
): Locale =
    when (language) {
        Language.RU -> RUSSIAN
        Language.EN -> Locale.ENGLISH
        Language.FOLLOW_SYSTEM, Language.UNRECOGNIZED ->
            if (systemLocale.language == RUSSIAN.language) RUSSIAN else Locale.ENGLISH
    }

/** The colloquial-time locale (Russian or English only) for [language] under [systemLocale]. */
fun clockLocale(
    language: Language,
    systemLocale: Locale,
): ClockLocale =
    if (effectiveLocale(language, systemLocale).language == RUSSIAN.language) ClockLocale.RU else ClockLocale.EN

/** A copy of this context whose resources resolve in the app's effective language. */
fun Context.localizedFor(language: Language): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(effectiveLocale(language, Locale.getDefault()))
    return createConfigurationContext(config)
}
