/*
 * LogioDatabase: Room veritabanının tanımı (tek tablo: journal_entries).
 *
 * version: Tablo yapısı her değiştiğinde 1 artırılır ve bir Migration yazılır; yoksa uygulama açılışta hata verir.
 * exportSchema: Her sürümün yapısı app/schemas/ klasörüne JSON olarak kaydedilir (git'e eklenmeli).
 *   Bir sonraki sürümde bu dosyalar sayesinde @AutoMigration kullanılabilir ve migration test edilebilir.
 * Veritabanı DatabaseModule'de SQLCipher ile şifreli olarak açılır.
 */
package com.example.logio.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [JournalEntryEntity::class],
    version = 14,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LogioDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
}
