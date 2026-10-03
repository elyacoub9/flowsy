package com.elyacoub9.flowsy.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    FRENCH("fr");

    companion object {
        fun fromTag(tag: String?): AppLanguage? =
            entries.firstOrNull { tag != null && Locale.forLanguageTag(tag).language == it.tag }
    }
}

/**
 * AppCompat is the live source of the app language. Callers also write the tag to
 * Settings.languageTag so it is included in backups.
 */
class LanguageManager(private val systemLocale: () -> Locale) {

    /** Language explicitly chosen in the app, or null when following the system. */
    fun chosen(): AppLanguage? =
        AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag())

    /** Language currently shown: the chosen one, else the system's if supported, else English. */
    fun current(): AppLanguage =
        chosen() ?: AppLanguage.fromTag(systemLocale().toLanguageTag()) ?: AppLanguage.ENGLISH

    /** Backups without a language: follow the phone again (clears the in-app choice). */
    fun followSystem() {
        if (chosen() == null) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    /** Recreates running activities when the language changes. */
    fun apply(language: AppLanguage) {
        if (chosen() == language) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }
}
