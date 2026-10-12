/*
 * BackupFormat: Kayıtların yedek dosyasındaki JSON biçimi ve dosya yollarının taşınması.
 *
 * Kayıtlar fotoğraf/ses/video dosyalarına telefona özgü tam yolla bağlıdır
 * (ör. /data/user/0/com.example.logio/files/img_1.jpg). Paket adı ya da telefon değişince bu yol değişir.
 * Bu yüzden yedekte uygulama klasöründeki yollar göreli yazılır ("logio-file:img_1.jpg") ve
 * geri yüklenirken yeni klasöre göre yeniden kurulur. Uygulama klasörü dışındaki değerler
 * (internet adresleri, galeri bağlantıları) olduğu gibi kalır.
 * Android'e bağımlı değildir; birim testleri BackupFormatTest.kt içindedir.
 */
package com.example.logio.data.backup

import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import org.json.JSONObject
import java.io.File

internal const val BACKUP_FORMAT_VERSION = 1

/** Uygulama klasöründeki bir dosya (tam yol olarak geri yüklenir). */
internal const val FILE_MARKER = "logio-file:"

/** Uygulama klasöründeki bir dosya (file:// adresi olarak geri yüklenir; videolar böyle saklanır). */
internal const val FILE_URI_MARKER = "logio-file-uri:"

/** Telefona özgü yollarla taşınabilir yollar arasında çeviri yapar. */
internal class BackupPaths(filesDir: File) {
    private val root: File = filesDir.absoluteFile
    private val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator

    /** Uygulama klasöründeki bir dosyanın göreli yolu; klasör dışındaysa null. */
    fun relativeOf(value: String): String? {
        val path = value.removePrefix("file://")
        if (!path.startsWith(rootPrefix)) return null
        return path.removePrefix(rootPrefix).replace(File.separatorChar, '/').takeIf { isSafeRelative(it) }
    }

    /** Yedekteki göreli yolun bu telefondaki dosyası; klasör dışına çıkmaya çalışan yollar (../) reddedilir. */
    fun fileFor(relative: String): File? {
        if (!isSafeRelative(relative)) return null
        val file = File(root, relative)
        return file.takeIf { it.canonicalPath.startsWith(root.canonicalPath + File.separator) }
    }

    private fun isSafeRelative(relative: String) =
        relative.isNotBlank() && !relative.startsWith("/") && !relative.contains('\\') &&
            relative.split('/').none { it == ".." || it == "." || it.isEmpty() }

    /** Dışa aktarırken: yolu taşınabilir hale getirir, yedeğe girecek dosyayı [onFile] ile bildirir. */
    fun toPortable(value: String, asUri: Boolean, onFile: (String) -> Unit): String {
        val relative = relativeOf(value) ?: return value
        onFile(relative)
        return (if (asUri) FILE_URI_MARKER else FILE_MARKER) + relative
    }

    /** Geri yüklerken: taşınabilir yolu bu telefondaki yola çevirir. */
    fun fromPortable(value: String): String = when {
        value.startsWith(FILE_URI_MARKER) ->
            fileFor(value.removePrefix(FILE_URI_MARKER))?.let { "file://" + it.path } ?: value
        value.startsWith(FILE_MARKER) ->
            fileFor(value.removePrefix(FILE_MARKER))?.path ?: value
        else -> value
    }
}

/** Kaydı yedek JSON'una çevirir. id yazılmaz: geri yüklenince veritabanı yeni id verir. */
internal fun JournalEntryEntity.toBackupJson(convertPath: (value: String, asUri: Boolean) -> String): JSONObject =
    JSONObject().apply {
        put("title", title)
        put("content", content)
        put("createdAt", createdAt)
        put("entryType", entryType.name)
        put("moodRating", moodRating)
        put("mediaUri", mediaUri?.let { convertPath(it, true) })
        put("expenseAmount", expenseAmount)
        put("expenseCategory", expenseCategory)
        put("waterAmountMl", waterAmountMl)
        put("isEncrypted", isEncrypted)
        put("imagePaths", imagePaths?.split(",")?.joinToString(",") { if (it.isBlank()) it else convertPath(it, false) })
        put("audioPath", audioPath?.let { convertPath(it, false) })
        put("transcriptionText", transcriptionText)
        put("calories", calories)
        put("protein", protein)
        put("carbs", carbs)
        put("fat", fat)
        put("weightKg", weightKg)
        put("sleepHours", sleepHours)
        put("alcoholUnits", alcoholUnits)
        put("coffeeCups", coffeeCups)
        put("latitude", latitude)
        put("longitude", longitude)
    }

/** Yedek JSON'unu kayda çevirir. Tanınmayan tür "not" (TEXT) olur, veritabanındaki kuralla aynı. */
internal fun JSONObject.toJournalEntry(convertPath: (String) -> String): JournalEntryEntity {
    fun str(key: String) = if (isNull(key)) null else getString(key)
    fun int(key: String) = if (isNull(key)) null else getInt(key)
    fun dbl(key: String) = if (isNull(key)) null else getDouble(key)
    return JournalEntryEntity(
        title = getString("title"),
        content = getString("content"),
        createdAt = getLong("createdAt"),
        entryType = runCatching { EntryType.valueOf(getString("entryType")) }.getOrDefault(EntryType.TEXT),
        moodRating = int("moodRating"),
        mediaUri = str("mediaUri")?.let(convertPath),
        expenseAmount = dbl("expenseAmount"),
        expenseCategory = str("expenseCategory"),
        waterAmountMl = int("waterAmountMl"),
        isEncrypted = optBoolean("isEncrypted", false),
        imagePaths = str("imagePaths")?.split(",")?.joinToString(",") { if (it.isBlank()) it else convertPath(it) },
        audioPath = str("audioPath")?.let(convertPath),
        transcriptionText = str("transcriptionText"),
        calories = int("calories"),
        protein = dbl("protein"),
        carbs = dbl("carbs"),
        fat = dbl("fat"),
        weightKg = dbl("weightKg"),
        sleepHours = dbl("sleepHours"),
        alcoholUnits = str("alcoholUnits"),
        coffeeCups = int("coffeeCups"),
        latitude = dbl("latitude"),
        longitude = dbl("longitude")
    )
}

/** Aynı kaydı iki kez eklememek için: zaman + tür + başlık aynıysa aynı kayıt sayılır. */
internal fun JournalEntryEntity.backupIdentity(): String = "$createdAt|${entryType.name}|$title"
