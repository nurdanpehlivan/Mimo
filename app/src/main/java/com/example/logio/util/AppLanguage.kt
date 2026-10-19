package com.example.logio.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Uygulama dilleri. SYSTEM: telefonun dilini izler (desteklenmiyorsa İngilizce kaynaklar kullanılır: res/values). */
enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("", ""),
    TURKISH("tr", "Türkçe"),
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch"),
    SPANISH("es", "Español");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it != SYSTEM && it.tag == tag?.substringBefore('-') } ?: SYSTEM
    }
}

/**
 * Uygulama içi dil seçimi.
 * - Android 13+: sistemin uygulama başına dil özelliği (LocaleManager) kullanılır; sistem ayarlarında da görünür.
 * - Daha eski sürümler: seçim SharedPreferences'ta tutulur, aktivite/uygulama bağlamı [wrap] ile sarılır.
 */
object AppLocale {
    private const val PREFS = "logio_settings"
    private const val KEY_LANGUAGE = "language"

    fun current(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].language)
        } else {
            AppLanguage.fromTag(prefs(context).getString(KEY_LANGUAGE, null))
        }

    /** Dili değiştirir. Android 13 altında ekranın yeni dille çizilmesi için aktivite yeniden oluşturulur. */
    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Sistem, yapılandırma değişikliği olarak aktiviteyi kendisi yeniden oluşturur
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        } else {
            prefs(activity).edit().putString(KEY_LANGUAGE, language.tag).apply()
            activity.applicationContext.resources.let { res ->
                val config = Configuration(res.configuration).apply { setLocale(localeFor(language)) }
                @Suppress("DEPRECATION")
                res.updateConfiguration(config, res.displayMetrics)
            }
            activity.recreate()
        }
    }

    /** Android 13 altında bağlamı seçili dile sarar; 13+ için sistem bunu zaten yapar. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = AppLanguage.fromTag(prefs(base).getString(KEY_LANGUAGE, null))
        if (language == AppLanguage.SYSTEM) return base
        val locale = localeFor(language)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    private fun localeFor(language: AppLanguage): Locale =
        if (language == AppLanguage.SYSTEM) Locale.getDefault() else Locale.forLanguageTag(language.tag)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
