/*
 * AppCheckSetup (DEBUG sürümü): Android Studio'dan çalıştırılan test sürümünde kullanılır.
 *
 * Debug sağlayıcısı, ilk açılışta Logcat'e bir "debug secret" yazar:
 *   "Enter this debug secret into the allow list in the Firebase Console..."
 * Bu kodu Firebase konsolu → App Check → Uygulamalar → ⋮ → "Debug token'ları yönet" kısmına eklemen gerekir.
 * Play Store'a giden sürüm bu dosyayı DEĞİL, src/release içindeki eşini kullanır.
 */
package com.example.logio

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal fun installAppCheck() {
    Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
