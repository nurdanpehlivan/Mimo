/*
 * LogioApp: Uygulamanın Application sınıfı; her şeyden önce bir kez çalışır.
 *
 * - @HiltAndroidApp: Hilt'in bağımlılık zincirini burada başlatır.
 * - attachBaseContext: seçili dili uygular (Android 13 altı için).
 * - installAppCheck: Firebase App Check'i kurar. Test sürümünde Debug, Play Store sürümünde
 *   Play Integrity sağlayıcısı kullanılır (src/debug ve src/release içindeki AppCheckSetup.kt).
 */
package com.example.logio

import android.app.Application
import android.content.Context
import com.example.logio.util.AppLocale
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class LogioApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        // Firebase, google-services eklentisi sayesinde kendiliğinden başlar; App Check ilk Gemini isteğinden önce kurulmalı
        installAppCheck()
    }
}
