package com.example.logio.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase
import timber.log.Timber
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Veritabanı şifrelemesi (SQLCipher).
 *
 * - Rastgele bir parola üretilir; parola Android Keystore'daki bir AES anahtarıyla şifrelenip SharedPreferences'ta saklanır.
 *   Keystore anahtarı cihazdan dışarı çıkarılamaz, bu yüzden parola düz metin olarak hiçbir yerde durmaz.
 * - Eski sürümlerden kalan şifresiz veritabanı, ilk açılışta kayıtlar kaybolmadan şifreli hale getirilir.
 */
object DatabaseSecurity {
    private const val PREFS = "db_security"
    private const val KEY_PASSPHRASE = "passphrase"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "logio_db_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12

    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** Room'a verilecek parola (SQLCipher için UTF-8 baytları). Okunamazsa null döner. */
    fun passphrase(context: Context): ByteArray? = try {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_PASSPHRASE, null)
        val hex = if (stored != null) {
            decrypt(stored)
        } else {
            val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
            random.joinToString("") { "%02x".format(it) }.also {
                prefs.edit().putString(KEY_PASSPHRASE, encrypt(it)).commit()
            }
        }
        hex.toByteArray(Charsets.UTF_8)
    } catch (e: Exception) {
        Timber.e(e, "Veritabanı parolası okunamadı")
        null
    }

    /**
     * Veritabanı dosyası hâlâ şifresizse şifreli bir kopyasını oluşturur, kayıt sayılarını karşılaştırır
     * ve ancak her şey doğruysa eski dosyanın yerine koyar. Hata olursa eski dosyaya dokunulmaz.
     * @return veritabanı şifreli durumdaysa (ya da henüz hiç yoksa) true.
     */
    fun encryptIfPlaintext(context: Context, dbName: String, passphrase: ByteArray): Boolean {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists() || !isPlaintext(dbFile)) return true

        System.loadLibrary("sqlcipher")
        val tmp = File(dbFile.parentFile, "$dbName.encrypting")
        tmp.delete()
        return try {
            val key = String(passphrase, Charsets.UTF_8)
            val plain = SQLiteDatabase.openDatabase(dbFile.path, "", null, SQLiteDatabase.OPEN_READWRITE, null, null)
            val version: Int
            val rowCount: Long
            try {
                version = plain.version
                rowCount = countRows(plain)
                plain.rawExecSQL("ATTACH DATABASE '${tmp.path}' AS encrypted KEY '$key'")
                plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
                plain.rawExecSQL("PRAGMA encrypted.user_version = $version")
                plain.rawExecSQL("DETACH DATABASE encrypted")
            } finally {
                plain.close()
            }

            // Şifreli kopyayı doğrula: aynı parola ile açılmalı ve aynı sayıda kayıt içermeli
            val check = SQLiteDatabase.openDatabase(tmp.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null)
            val ok = try { check.version == version && countRows(check) == rowCount } finally { check.close() }
            check(ok) { "Şifreli kopya doğrulanamadı" }

            // WAL içeriği dışa aktarmaya dahil edildi; eski yan dosyalar yeni veritabanına karışmasın
            listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            check(dbFile.delete() && tmp.renameTo(dbFile)) { "Şifreli dosya yerine konamadı" }
            Timber.i("Veritabanı şifrelendi ($rowCount kayıt)")
            true
        } catch (e: Exception) {
            Timber.e(e, "Veritabanı şifrelenemedi, şifresiz devam ediliyor")
            tmp.delete()
            false
        }
    }

    /**
     * Parola çözülemiyorsa (ör. veriler yedekten başka bir telefona geri yüklendi ve Keystore anahtarı yok)
     * şifreli dosya zaten açılamaz. Uygulama çökmesin diye dosya kenara alınır ve yeni bir parola üretilir.
     */
    fun resetUnrecoverable(context: Context, dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.renameTo(File(dbFile.parentFile, "$dbName.unrecoverable"))
        listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PASSPHRASE).commit()
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS) }
        Timber.w("Veritabanı parolası kurtarılamadı; eski dosya kenara alındı")
    }

    private fun countRows(db: SQLiteDatabase): Long {
        val hasTable = db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='journal_entries'", null)
            .use { it.moveToFirst() && it.getLong(0) > 0 }
        if (!hasTable) return 0
        return db.rawQuery("SELECT count(*) FROM journal_entries", null).use { if (it.moveToFirst()) it.getLong(0) else 0 }
    }

    private fun isPlaintext(file: File): Boolean {
        val header = ByteArray(SQLITE_HEADER.size)
        val read = file.inputStream().use { it.read(header) }
        return read == header.size && header.contentEquals(SQLITE_HEADER)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val bytes = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        }
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
    }
}
