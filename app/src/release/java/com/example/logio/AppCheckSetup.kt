/*
 * AppCheckSetup (RELEASE sürümü): Play Store'a yüklenen sürümde kullanılır.
 *
 * Play Integrity, Google Play'in "bu uygulama Play Store'dan indirilmiş, değiştirilmemiş Logio" onayıdır.
 * Firebase konsolu → App Check → Logio → Play Integrity'ye, Play Console'daki
 * "Uygulama imzalama anahtarı" SHA-256 parmak izinin eklenmiş olması gerekir.
 */
package com.example.logio

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal fun installAppCheck() {
    Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
