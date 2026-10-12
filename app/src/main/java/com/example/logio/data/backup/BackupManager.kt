/*
 * BackupManager: Günlüğün tamamını şifreli tek bir dosyaya aktarır ve geri yükler.
 *
 * Dışa aktar: bütün kayıtlar + profil (isim, hedefler) + kayıtların kullandığı fotoğraf/ses/çizim/video
 * dosyaları bir ZIP'e konur, ZIP kullanıcının şifresiyle şifrelenir (BackupCrypto) ve kullanıcının
 * seçtiği yere (İndirilenler, Drive...) yazılır.
 * İçe aktar: dosya çözülür, medya uygulama klasörüne kopyalanır, kayıtlar eklenir. Zaten var olan
 * kayıtlar (aynı zaman + tür + başlık) tekrar eklenmez; aynı yedek iki kez yüklense de çiftlenmez.
 *
 * Neden gerekli: Veritabanı telefona bağlı bir anahtarla şifreli ve Android yedeklemesi kapalı.
 * Telefon ya da paket adı değişince kayıtları taşımanın tek yolu bu dosya.
 */
package com.example.logio.data.backup

import android.content.Context
import android.net.Uri
import com.example.logio.BuildConfig
import com.example.logio.data.journal.JournalRepository
import com.example.logio.data.profile.ProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val added: Int, val skipped: Int)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journalRepository: JournalRepository,
    private val profileRepository: ProfileRepository
) {
    private companion object {
        const val MANIFEST = "manifest.json"
        const val ENTRIES = "entries.json"
        const val PROFILE = "profile.json"
        const val FILES_PREFIX = "files/"
    }

    /** Bütün günlüğü [target] dosyasına şifreli yazar; yazılan kayıt sayısını döndürür. */
    suspend fun export(target: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val entries = journalRepository.entries.first()
        val paths = BackupPaths(context.filesDir)
        val localFiles = linkedSetOf<String>()
        // Galeriden eklenen videolar yalnızca galeri bağlantısı olarak saklanıyor; okunabiliyorsa yedeğe kopyalanır
        val galleryVideos = linkedMapOf<String, Uri>()

        val entriesJson = JSONArray()
        entries.forEach { entry ->
            val json = entry.toBackupJson { value, asUri ->
                if (asUri && value.startsWith("content://") && isReadable(Uri.parse(value))) {
                    val relative = "restored_videos/video_${entry.createdAt}_${entry.id}.mp4"
                    galleryVideos[relative] = Uri.parse(value)
                    FILE_URI_MARKER + relative
                } else {
                    paths.toPortable(value, asUri) { localFiles += it }
                }
            }
            entriesJson.put(json)
        }

        val profile = profileRepository.profile.value
        val profileJson = JSONObject()
            .put("name", profile.name)
            .put("waterGoalMl", profile.waterGoalMl)
            .put("stepGoal", profile.stepGoal)
        val manifest = JSONObject()
            .put("format", BACKUP_FORMAT_VERSION)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("exportedAt", System.currentTimeMillis())
            .put("entryCount", entries.size)

        val output = context.contentResolver.openOutputStream(target, "wt")
            ?: throw IOException("Yedek dosyası oluşturulamadı")
        ZipOutputStream(BackupCrypto.encryptingStream(BufferedOutputStream(output), password)).use { zip ->
            // Fotoğraf ve videolar zaten sıkıştırılmış; en hızlı sıkıştırma yeterli
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putText(MANIFEST, manifest.toString())
            zip.putText(ENTRIES, entriesJson.toString())
            zip.putText(PROFILE, profileJson.toString())
            localFiles.forEach { relative ->
                val file = paths.fileFor(relative)?.takeIf { it.isFile } ?: return@forEach
                zip.putNextEntry(ZipEntry(FILES_PREFIX + relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            galleryVideos.forEach { (relative, uri) ->
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        zip.putNextEntry(ZipEntry(FILES_PREFIX + relative))
                        input.copyTo(zip)
                        zip.closeEntry()
                    }
                }.onFailure { Timber.w(it, "Galeri videosu yedeğe eklenemedi") }
            }
        }
        entries.size
    }

    /** [source] yedeğini çözüp geri yükler. Şifre yanlışsa [WrongPasswordException] fırlatır. */
    suspend fun import(source: Uri, password: CharArray): ImportResult = withContext(Dispatchers.IO) {
        val paths = BackupPaths(context.filesDir)
        var entriesText: String? = null
        var profileText: String? = null

        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Yedek dosyası açılamadı")
        input.use { raw ->
            val decrypted = BackupCrypto.decryptingStream(BufferedInputStream(raw), password)
            val zip = ZipInputStream(decrypted)
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.isDirectory -> Unit
                    entry.name == ENTRIES -> entriesText = zip.readBytes().toString(Charsets.UTF_8)
                    entry.name == PROFILE -> profileText = zip.readBytes().toString(Charsets.UTF_8)
                    entry.name.startsWith(FILES_PREFIX) -> {
                        // Yedekteki yol uygulama klasörünün dışına çıkamaz; var olan dosyanın üzerine yazılmaz
                        val target = paths.fileFor(entry.name.removePrefix(FILES_PREFIX))
                        if (target != null && !target.exists()) {
                            target.parentFile?.mkdirs()
                            val temp = File(target.parentFile, target.name + ".part")
                            temp.outputStream().use { zip.copyTo(it) }
                            if (!temp.renameTo(target)) temp.delete()
                        }
                    }
                }
                zip.closeEntry()
            }
            // Dosyanın sonuna kadar doğrula: yarıda kesilmiş bir yedek sessizce kabul edilmesin
            BackupCrypto.verifyToEnd(decrypted)
        }

        val entriesArray = JSONArray(entriesText ?: throw NotABackupException())
        val known = journalRepository.entries.first().mapTo(HashSet()) { it.backupIdentity() }
        var added = 0
        var skipped = 0
        for (i in 0 until entriesArray.length()) {
            val entry = entriesArray.getJSONObject(i).toJournalEntry(paths::fromPortable)
            if (known.add(entry.backupIdentity())) {
                journalRepository.insert(entry)
                added++
            } else {
                skipped++
            }
        }

        profileText?.let { text ->
            val json = JSONObject(text)
            val current = profileRepository.profile.value
            profileRepository.update(
                current.copy(
                    name = json.optString("name", current.name),
                    waterGoalMl = json.optInt("waterGoalMl", current.waterGoalMl),
                    stepGoal = json.optInt("stepGoal", current.stepGoal)
                )
            )
        }
        ImportResult(added, skipped)
    }

    private fun isReadable(uri: Uri): Boolean =
        runCatching { context.contentResolver.openInputStream(uri)?.use { true } ?: false }.getOrDefault(false)

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
