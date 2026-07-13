package ru.aensidhe.dreamclock.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The locale to force for a given app [Language], or null to follow the system locale. */
fun languageLocale(language: Language): Locale? =
    when (language) {
        Language.RU -> Locale.forLanguageTag("ru")
        Language.EN -> Locale.ENGLISH
        Language.FOLLOW_SYSTEM, Language.UNRECOGNIZED -> null
    }

/** A copy of this context whose resources resolve in the app's chosen [language]. */
fun Context.localizedFor(language: Language): Context {
    val locale = languageLocale(language) ?: return this
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}
