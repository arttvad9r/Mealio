package com.arttvad9r.mealio.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The languages Mealio ships. English is the Android default (`values/`),
 * Russian is the localised resource set (`values-ru/`); any other system
 * language therefore falls back to English.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    RUSSIAN("ru"),
    ENGLISH("en"),
    ;

    companion object {

        /**
         * The language currently applied to the app. An empty application
         * locale means "follow the system", which is also the fallback when the
         * stored tag is unknown.
         */
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty()
            return fromTag(tag)
        }

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) && it != SYSTEM } ?: SYSTEM

        /**
         * Applies [language] via the standard per-app locale API. Recreates the
         * activity, so call this on the main thread.
         */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}
