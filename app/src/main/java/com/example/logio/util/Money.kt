/*
 * Money: Harcama tutarlarını kullanıcının ülkesinin para birimiyle gösterir (Türkiye ₺, Almanya €, ABD $).
 *
 * Uygulama dili değil kullanıcının ÜLKESİ esas alınır: uygulamayı İngilizce kullanan biri Türkiye'deyse ₺ görür.
 * Ülke sırayla şuralardan okunur, ilk dolu olan kullanılır:
 *   1. Telefonun dil + bölge ayarı (ör. tr-TR). Bazı telefonlarda bölgesiz gelir ("tr"), o zaman:
 *   2. SIM kartın ülkesi, 3. bağlı olunan mobil şebekenin ülkesi (izin gerekmez),
 *   4. Java'nın varsayılan dili (son çare).
 * Tutarlar veritabanında para birimi olmadan sayı olarak saklanır; yalnızca gösterim ülkeye göre değişir.
 */
package com.example.logio.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.telephony.TelephonyManager
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {

    private var appContext: Context? = null

    /** LogioApp.onCreate'te çağrılır; SIM/şebeke ülkesini okumak için gerekir. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Telefonun kendi dil ayarları (uygulamanın dil seçiminden etkilenmez), öncelik sırasıyla. */
    private fun systemLocales(): List<Locale> {
        val list = mutableListOf<Locale>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext?.getSystemService(LocaleManager::class.java)?.systemLocales?.let { locales ->
                for (i in 0 until locales.size()) list += locales[i]
            }
        }
        val res = Resources.getSystem().configuration.locales
        for (i in 0 until res.size()) list += res[i]
        return list
    }

    /** Kullanıcının ülkesi (ör. "TR", "DE"); hiçbir yerden bulunamazsa boş. */
    private fun country(): String {
        systemLocales().firstOrNull { it.country.isNotBlank() }?.let { return it.country }
        val telephony = appContext?.getSystemService(TelephonyManager::class.java)
        val fromPhone = runCatching { telephony?.simCountryIso }.getOrNull().orEmpty()
            .ifBlank { runCatching { telephony?.networkCountryIso }.getOrNull().orEmpty() }
        if (fromPhone.isNotBlank()) return fromPhone.uppercase(Locale.ROOT)
        return Locale.getDefault().country
    }

    /** Telefonun dili + kullanıcının ülkesi: sayı biçimi dile, para birimi ülkeye göre olur. */
    private fun moneyLocale(): Locale {
        val language = systemLocales().firstOrNull()?.language ?: Locale.getDefault().language
        return Locale(language, country())
    }

    /**
     * Tutarı para birimiyle biçimler: "₺250", "250 €", "$12.50".
     * Tam sayıysa kuruş gösterilmez; [wholeOnly] ise her zaman tam sayıya yuvarlanır (istatistik toplamları).
     * Ülke hiçbir yerden bulunamazsa yalnızca sayı gösterilir.
     */
    fun format(amount: Double, wholeOnly: Boolean = false): String {
        val locale = moneyLocale()
        val format = runCatching { NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance(locale) } }
            .getOrElse { NumberFormat.getNumberInstance(locale) }
        if (wholeOnly || amount % 1.0 == 0.0) {
            format.minimumFractionDigits = 0
            format.maximumFractionDigits = 0
        }
        return format.format(amount)
    }

    /** Para biriminin simgesi (₺, €, $); ülke bilinmiyorsa boş. Tutar girilen alanda gösterilir. */
    fun symbol(): String = runCatching {
        val locale = moneyLocale()
        Currency.getInstance(locale).getSymbol(locale)
    }.getOrDefault("")
}
