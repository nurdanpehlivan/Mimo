# Logio – R8 (küçültme) kuralları
#
# Play Store sürümünde (release) R8 kullanılmayan kodu siler ve sınıf/alan adlarını kısaltır.
# Kütüphanelerin çoğu (Room, Hilt, Firebase, Health Connect, Media3) kendi kurallarını getirir;
# burada yalnızca Logio'ya özgü ve kütüphanelerin kapsamadığı durumlar var.
# Bir kural silinmeden önce release sürüm telefonda denenmeli.

# --- Enum adları ---
# EntryType veritabanına adıyla ("COFFEE") yazılır ve okunurken EntryType.valueOf("COFFEE") ile çevrilir.
# Adlar değişirse eski kayıtların hepsi "not" (TEXT) görünür. JourneyTemplate da adıyla taşınır.
# Güvenli olsun diye uygulamanın bütün enum'ları adlarıyla korunur.
-keep enum com.example.logio.** { *; }

# --- SQLCipher ---
# Veritabanı şifrelemesi yerel (C) kütüphane üzerinden çalışır; JNI'nin çağırdığı sınıflar korunmalı.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# --- Hata raporları ---
# Çökme kayıtlarında dosya adı ve satır numarası okunabilsin (Play Console bunları mapping dosyasıyla çözer)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
