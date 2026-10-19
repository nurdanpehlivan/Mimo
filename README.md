# Mimo

Günün tamamını tek bir zaman tünelinde tutan bir Android günlük uygulaması. Not, fotoğraf, ses kaydı, ruh hali, su, kahve, uyku, adım, harcama, film, kitap ve daha fazlası aynı yerde, tarih sırasıyla durur. Veriler telefonda şifreli olarak saklanır.

Uygulama Türkçe, İngilizce, Almanca ve İspanyolca kullanılabilir.

## Özellikler

**Kayıt türleri**
- Yazı, ruh hali, yapılacaklar listesi ve çizim
- Fotoğraf, video ve ses kaydı (ses kaydı yazıya çevrilebilir)
- Konum (harita üzerinden seçilir)
- Su, kahve, alkol ve harcama takibi
- Film / dizi, kitap ve müzik (internetten aranıp kapak görseliyle eklenir)
- Link (paylaş menüsünden "Mimo'ya kaydet" ile, önizlemesiyle birlikte)
- Yemek: fotoğrafı çekilen yemeğin içeriği ve kalorisi yapay zekayla tahmin edilir

**Takip ve istatistik**
- Adım ve uyku Health Connect üzerinden otomatik okunur (Samsung Health vb. ile uyumlu)
- Haftalık, aylık ve yıllık istatistikler, mini grafikler
- Takvim görünümü, seriler ve rozetler

**Anılar**
- Journey videosu: seçilen fotoğraflardan müzikli kısa video
- Profilde "Mimo senin için hatırlattı" ile geçmişten bir anı

**Gizlilik ve güvenlik**
- Veritabanı SQLCipher ile şifreli; anahtar telefonun donanımında (Android Keystore) tutulur
- Parmak izi / yüz tanıma ile uygulama kilidi
- Android'in otomatik bulut yedeği kapalı; veriler Google Drive'a ya da başka bir telefona kendiliğinden gitmez
- Telefon değiştirirken: şifreyle korunan tek bir `.mimo` yedek dosyası dışa aktarılıp yeni telefonda içe aktarılır

> Yemek analizi ve sesi yazıya çevirme özelliklerinde fotoğraf ve ses, işlenmek üzere Google'ın Gemini modeline gönderilir. Diğer bütün veriler cihazda kalır.

## Kullanılan teknolojiler

| Konu | Kullanılan |
|---|---|
| Dil / arayüz | Kotlin, Jetpack Compose, Material 3 |
| Mimari | MVVM + Repository, Hilt |
| Veritabanı | Room + SQLCipher |
| Yapay zeka | Gemini (Firebase AI Logic), Firebase App Check |
| Sağlık verisi | Health Connect |
| Harita / konum | Google Maps Compose, Play Services Location |
| Medya | Coil, Media3 Transformer |
| Arama | TVmaze (film / dizi), Open Library (kitap), iTunes Search (müzik) |

`minSdk 26` (Android 8.0) · `targetSdk 36` · `compileSdk 37`

## Derleme

Gereksinimler: güncel Android Studio, JDK 17.

1. Depoyu indir:
   ```
   git clone https://github.com/nurdanpehlivan/Logio.git
   ```
2. **Firebase:** Kendi Firebase projende bir Android uygulaması oluştur, `google-services.json` dosyasını indirip `app/` klasörüne koy. Yemek analizi için Firebase konsolunda **AI Logic**'i (Gemini Developer API) aç. Test sürümünde App Check, ilk açılışta Logcat'e bir debug token yazar; bu token'ı Firebase → App Check → Manage debug tokens kısmına ekle.
3. **Google Maps:** Proje kök dizinindeki `local.properties` dosyasına bir Maps SDK for Android anahtarı ekle:
   ```
   MAPS_API_KEY=anahtarın
   ```
4. Android Studio'da projeyi aç ve çalıştır, ya da komut satırından:
   ```
   ./gradlew installDebug
   ```

`google-services.json` ve `local.properties` anahtar içerdiği için depoya eklenmez.

Birim testleri:
```
./gradlew test
```

## Proje yapısı

```
app/src/main/java/com/example/logio/
  data/        Veritabanı, yedekleme, Health Connect, Gemini, arama, ses
  di/          Hilt modülleri
  ui/          Ekranlar: timeline (günlük), calendar, analysis (istatistik), profile, lock, share
  util/        Dil, uygulama kilidi, Journey videosu
app/src/main/res/values*/strings.xml   Yazılar (4 dil)
app/schemas/                           Veritabanı şema geçmişi
```

Kod içindeki `logio` adları uygulamanın eski adından kalmadır.
