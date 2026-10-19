/*
 * Money: Harcama tutarlarını telefonun bölgesinin para birimiyle gösterir (Türkiye ₺, Almanya €, ABD $).
 *
 * Uygulama dili değil telefonun BÖLGESİ esas alınır: uygulamayı İngilizce kullanan biri Türkiye'deyse ₺ görür.
 * AppLocale uygulama dilini Locale.getDefault()'a yazdığı için bölge, sistemin kendi ayarından okunur.
 * Tutarlar veritabanında para birimi olmadan sayı olarak saklanır; yalnızca gösterim bölgeye göre değişir.
 */
package com.example.logio.util

import android.content.res.Resources
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {

    /** Telefonun dil + bölge ayarı (ör. tr-TR, de-DE, en-US); uygulamanın kendi dil seçiminden etkilenmez. */
    private fun deviceLocale(): Locale = Resources.getSystem().configuration.locales[0]

    /**
     * Tutarı para birimiyle biçimler: "₺250", "250 €", "$12.50".
     * Tam sayıysa kuruş gösterilmez; [wholeOnly] ise her zaman tam sayıya yuvarlanır (istatistik toplamları).
     * Telefonda bölge seçili değilse yalnızca sayı gösterilir.
     */
    fun format(amount: Double, wholeOnly: Boolean = false): String {
        val locale = deviceLocale()
        val format = if (locale.country.isBlank()) NumberFormat.getNumberInstance(locale)
            else NumberFormat.getCurrencyInstance(locale)
        if (wholeOnly || amount % 1.0 == 0.0) {
            format.minimumFractionDigits = 0
            format.maximumFractionDigits = 0
        }
        return format.format(amount)
    }

    /** Para biriminin simgesi (₺, €, $); bölge bilinmiyorsa boş. Tutar girilen alanda gösterilir. */
    fun symbol(): String = runCatching {
        val locale = deviceLocale()
        Currency.getInstance(locale).getSymbol(locale)
    }.getOrDefault("")
}
