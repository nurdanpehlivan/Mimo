/*
 * LogioApp: Uygulamanın Application sınıfı; her şeyden önce bir kez çalışır.
 *
 * - @HiltAndroidApp: Hilt'in bağımlılık zincirini burada başlatır.
 * - attachBaseContext: seçili dili uygular (Android 13 altı için).
 * - Timber: logları yalnızca test (debug) sürümünde açar.
 * - installAppCheck: Firebase App Check'i kurar. Test sürümünde Debug, Play Store sürümünde
 *   Play Integrity sağlayıcısı kullanılır (src/debug ve src/release içindeki AppCheckSetup.kt).
 * - PremiumManager.refresh: Mimo Premium satın alımını Google Play'den yeniden okur (iade, başka telefonda alım).
 */
package com.example.logio

import android.app.Application
import android.content.Context
import com.example.logio.data.premium.PremiumManager
import com.example.logio.util.AppLocale
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class LogioApp : Application() {
    @Inject lateinit var premiumManager: PremiumManager

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Loglar yalnızca Android Studio'dan çalıştırılan test sürümünde yazılır; Play Store sürümü hiçbir şey loglamaz
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Firebase, google-services eklentisi sayesinde kendiliğinden başlar; App Check ilk Gemini isteğinden önce kurulmalı
        installAppCheck()
        premiumManager.refresh()
    }
}
