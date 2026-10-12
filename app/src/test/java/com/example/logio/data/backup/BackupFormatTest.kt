/*
 * BackupFormatTest: Yedek JSON biçiminin ve dosya yolu taşımanın (BackupFormat.kt) birim testleri.
 *
 * En önemli senaryo: Paket adı değişince (com.example.logio → com.nurdanpehlivan.logio) uygulama
 * klasörünün yolu değişir. Fotoğraf, ses ve video yolları yeni klasöre göre yeniden kurulmalı,
 * internet adresleri ise dokunulmadan kalmalı.
 */
package com.example.logio.data.backup

import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupFormatTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `kayit json uzerinden tum alanlariyla geri gelir`() {
        val original = JournalEntryEntity(
            id = 42, title = "Latte", content = "2 fincan", createdAt = 1_760_000_000_000,
            entryType = EntryType.COFFEE, coffeeCups = 2, moodRating = null, expenseAmount = 12.5,
            expenseCategory = "food", sleepHours = 7.5, alcoholUnits = "1,5", latitude = 41.0, longitude = 29.0,
            calories = 450, protein = 25.0, carbs = 40.0, fat = 15.0, waterAmountMl = 250
        )
        val restored = original.toBackupJson { value, _ -> value }.toJournalEntry { it }
        // id yedeğe yazılmaz; geri yüklenince veritabanı yeni id verir
        assertEquals(original.copy(id = 0), restored)
    }

    @Test
    fun `bos alanlar bos olarak geri gelir`() {
        val restored = JournalEntryEntity(title = "Not", content = "").toBackupJson { v, _ -> v }.toJournalEntry { it }
        assertNull(restored.mediaUri)
        assertNull(restored.coffeeCups)
        assertNull(restored.imagePaths)
        assertEquals(EntryType.TEXT, restored.entryType)
    }

    @Test
    fun `paket adi degisince uygulama dosyalarinin yolu yeni klasore tasinir`() {
        val oldDir = temp.newFolder("com.example.logio", "files")
        val newDir = temp.newFolder("com.nurdanpehlivan.logio", "files")
        val exported = mutableListOf<String>()
        val oldPaths = BackupPaths(oldDir)

        val entry = JournalEntryEntity(
            title = "Gezi", content = "", entryType = EntryType.JOURNEY_VIDEO,
            imagePaths = "${File(oldDir, "img_1.jpg").path},https://site.com/kapak.jpg",
            audioPath = File(oldDir, "audio_notes/REC_1.m4a").path,
            mediaUri = "file://" + File(oldDir, "journey_1.mp4").path
        )
        val json = entry.toBackupJson { value, asUri -> oldPaths.toPortable(value, asUri) { exported += it } }

        assertEquals(listOf("img_1.jpg", "audio_notes/REC_1.m4a", "journey_1.mp4").sorted(), exported.sorted())

        val restored = json.toJournalEntry(BackupPaths(newDir)::fromPortable)
        assertEquals("${File(newDir, "img_1.jpg").path},https://site.com/kapak.jpg", restored.imagePaths)
        assertEquals(File(newDir, "audio_notes/REC_1.m4a").path, restored.audioPath)
        assertEquals("file://" + File(newDir, "journey_1.mp4").path, restored.mediaUri)
    }

    @Test
    fun `uygulama klasoru disindaki degerlere dokunulmaz`() {
        val paths = BackupPaths(temp.newFolder("files"))
        val nothing = mutableListOf<String>()
        listOf("https://youtube.com/watch?v=1", "content://media/video/5", "/sdcard/DCIM/a.jpg").forEach {
            assertEquals(it, paths.toPortable(it, asUri = false) { f -> nothing += f })
        }
        assertEquals(emptyList<String>(), nothing)
    }

    @Test
    fun `yedekteki yol uygulama klasorunun disina cikamaz`() {
        val paths = BackupPaths(temp.newFolder("files"))
        listOf("../../shared_prefs/db_security.xml", "/etc/passwd", "a/../../x", "", "a//b", "..\\x").forEach {
            assertNull("Reddedilmeli: $it", paths.fileFor(it))
        }
        assertEquals("$FILE_MARKER../x", paths.fromPortable("$FILE_MARKER../x"))
    }

    @Test
    fun `ayni kayit ayni kimligi tasir`() {
        val a = JournalEntryEntity(id = 1, title = "Su", content = "250 ml", createdAt = 5, entryType = EntryType.WATER)
        val b = a.copy(id = 99, content = "farklı içerik")
        assertEquals(a.backupIdentity(), b.backupIdentity())
    }
}
