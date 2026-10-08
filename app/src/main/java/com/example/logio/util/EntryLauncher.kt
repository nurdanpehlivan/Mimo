/*
 * EntryLauncher: Bir kaydın videosunu, linkini ya da konumunu telefonun başka bir uygulamasında açar.
 *
 * Bunlar veri işi değil, ekran işidir (bir Activity açmak için ekranın Context'i gerekir);
 * bu yüzden ViewModel'de değil burada durur. Günlük ve Takvim ekranları kart tıklamalarında çağırır.
 */
package com.example.logio.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.logio.data.local.JournalEntryEntity
import timber.log.Timber
import java.io.File

object EntryLauncher {

    /** Video ya da Journey videosunu telefonun video oynatıcısında açar. */
    fun openVideo(context: Context, entry: JournalEntryEntity) {
        val uriStr = entry.mediaUri
        if (uriStr.isNullOrBlank()) {
            Timber.e("Video açılamadı: mediaUri boş")
            return
        }
        try {
            val uri = if (uriStr.startsWith("content://")) {
                Uri.parse(uriStr)
            } else {
                // Uygulama klasöründeki dosyalar başka uygulamalara FileProvider üzerinden verilir
                val file = File(uriStr.removePrefix("file://"))
                if (!file.exists()) {
                    Timber.e("Video dosyası bulunamadı: ${file.path}")
                    return
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Video oynatıcı başlatılamadı")
        }
    }

    /** Link kaydını tarayıcıda açar; başında http(s) yoksa https eklenir. */
    fun openLink(context: Context, entry: JournalEntryEntity) {
        var url = entry.content
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Timber.e(e, "Bağlantı açılamadı")
        }
    }

    /** Konum kaydını harita uygulamasında açar. */
    fun openMap(context: Context, entry: JournalEntryEntity) {
        val lat = entry.latitude ?: return
        val lng = entry.longitude ?: return
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${entry.title})")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            // Telefonda harita uygulaması yoksa uygulama çökmesin
            Timber.e(e, "Harita açılamadı")
        }
    }
}
