# 📱 Logio - Tam Proje Dokümantasyonu (v1.8)

Bu döküman, Logio uygulamasının amacını, teknik mimarisini, madde madde yapılan geliştirmeleri ve dosya yapısını içeren tek ve en güncel rehberdir.

---

## 🎯 1. Uygulama Amacı
Logio; kullanıcıların günlük yaşantılarını, sağlık verilerini (adım, uyku), finansal harcamalarını ve anılarını (fotoğraf, video) tek bir platformda toplamasını sağlayan, **yapay zeka destekli** ve **gizlilik odaklı** bir dijital günlük uygulamasıdır. Veriler asla buluta çıkmaz, tamamen cihazda şifreli saklanır.

---

## 🚀 2. Teknik Altyapı (Hap Bilgiler)
-   **Dil:** %100 Kotlin
-   **Arayüz:** Jetpack Compose
-   **Mimari:** Clean Architecture + MVVM + Feature Registry (15+ özellik merkezi yönetilir).
-   **Veritabanı:** Room + **SQLCipher** (Donanımsal şifreleme desteği).
-   **AI (Yapay Zeka):** **Google Gemini API** (Görüntü işleme ve ses transkripsiyonu).
-   **Sağlık:** **Google Health Connect** (Cihazlar arası sağlık verisi eşitleme).
-   **Güvenlik:** **BiometricPrompt** (Parmak izi, Yüz tanıma, Cihaz PIN).
-   **Medya:** Media3 Transformer (FFmpeg benzeri profesyonel video birleştirme).

---

## ✅ 3. Madde Madde Yapılan Geliştirmeler

### 🔹 Çekirdek Sistem
-   **Clean Architecture:** Veri (Data), Arayüz (UI) ve Mantık (Logic) katmanları ayrıldı.
-   **Feature Registry:** Uyku, Adım, Yemek, Su, Kahve, Ruh Hali, Alkol, Konum, Fotoğraf, Ses, Video, Harcama, Not, Kitap ve Film özellikleri tek bir merkezden tanımlandı.
-   **Veritabanı Güvenliği:** SQLCipher entegrasyonu ile tüm veriler şifrelendi.

### 🔹 Yapay Zeka (AI) Entegrasyonu
-   **Akıllı Yemek Analizi:** Çekilen bir tabak yemeğin fotoğrafından Gemini AI; yemeğin adını, kalorisini ve makro değerlerini (protein, karbonhidrat, yağ) çıkarıp kaydeder.
-   **Sesli Not Transkripsiyonu:** Ses kayıtları AI ile metne dönüştürülür, kullanıcı dilerse metni kayda ekler.

### 🔹 Medya ve Görsellik
-   **Journey Video (Anı Filmi):** Kullanıcının seçtiği tarihlerdeki fotoğrafları alır, arka plana seçilen müzik temasını (Sinematik, Nostaljik, Neşeli) koyar ve sinematik bir `.mp4` video üretir.
-   **Gelişmiş İstatistikler:** Haftalık ruh hali akış grafiği ve aktivite çubukları (Adım/Su) eklendi. `WeeklyFlowCard` yapısı ile görsel ve metinler sağ-sol dengeli hale getirildi.

### 🔹 Güvenlik ve Gizlilik
-   **App Lock (Uygulama Kilidi):** Ayarlar (Profil) sayfasından aktif edilen biyometrik kilit sistemi eklendi. Uygulama açılışında kimlik doğrulaması zorunlu kılındı.
-   **Health Connect Entegrasyonu:** Android 14+ uyumlu izin sistemi ve otomatik veri senkronizasyonu kuruldu.

---

## 🛠️ 4. Kod Haritası (Nerede Ne Var?)

| Özellik | Dosya Yolu |
| :--- | :--- |
| **Ana Ekran ve Akış** | `ui/timeline/TimelineScreen.kt` |
| **Kayıt Ekleme Pencereleri** | `ui/timeline/TimelineDialogs.kt` |
| **İstatistik ve Grafikler** | `ui/analysis/AnalysisScreen.kt` |
| **Profil ve Ayarlar** | `ui/profile/ProfileScreen.kt` |
| **Video İşleme Mantığı** | `util/JourneyVideoMaker.kt` |
| **AI Servis Tanımları** | `data/remote/GeminiService.kt` |
| **Veritabanı Tablosu** | `data/local/JournalEntryEntity.kt` |
| **Giriş ve Kilit Ekranı** | `MainActivity.kt` |

---

## 📂 5. Klasör Yapısı

-   **`data/local`**: Room veritabanı, DAO'lar.
-   **`data/remote`**: Gemini AI, Yer Arama API, Film/Kitap/Müzik API servisleri.
-   **`data/health`**: Health Connect veri senkronizasyon yöneticisi.
-   **`data/profile`**: Kullanıcı hedefleri ve kilit/dil tercihleri.
-   **`ui/timeline`**: Günlük kart tasarımları ve interaktif dialoglar.
-   **`ui/analysis`**: Metrik kartları ve haftalık akış görselleri.
-   **`res/raw`**: Videolarda kullanılan lisanslı tema müzikleri.

---

## 🔗 6. Veri Akış Mantığı
1.  **Giriş:** Kullanıcı FAB (+) butonuna basar ve bir kayıt türü seçer.
2.  **İşlem:** `TimelineDialogs` içindeki ilgili modal açılır, veriler girilir.
3.  **ViewModel:** `TimelineViewModel.kt` veriyi doğrular ve Room veritabanına iletir.
4.  **Otomatik Güncelleme:** Room'un `Flow` desteği sayesinde ekran hiçbir yenileme yapmadan yeni kaydı en üstte gösterir.

---
**Son Güncelleme:** 2026-10-05
**Hazırlayan:** AI Assistant
**Durum:** ✅ Stabil ve Fonksiyonel
