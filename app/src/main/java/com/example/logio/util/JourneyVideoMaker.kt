/*
 * JourneyVideoMaker: Seçilen fotoğraflardan müzikli "Journey" videosu üretir (Media3 Transformer).
 *
 * Her fotoğraf birkaç saniyelik bir sahneye dönüştürülür, sahneler sırayla birleştirilir ve
 * seçilen temanın müziği (res/raw/theme_*.m4a) eklenir. JourneyTemplate hazır şablonları tanımlar.
 */
package com.example.logio.util

import android.content.Context
import android.net.Uri
import timber.log.Timber
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.logio.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Yolculuk videosu şablonları. Kullanıcı yalnızca şablonu seçer; müzik ve tarih aralığı
 * şablondan otomatik gelir. [daysBack] null ise tüm fotoğraflar kullanılır.
 */
enum class JourneyTemplate(
    @StringRes val label: Int,
    @StringRes val description: Int,
    val emoji: String,
    @RawRes val musicRes: Int,
    val daysBack: Int?
) {
    WEEKLY_SUMMARY(R.string.journey_weekly, R.string.journey_weekly_desc, "🗓️", R.raw.theme_cheerful, 7),
    TRAVEL_MOMENTS(R.string.journey_travel, R.string.journey_travel_desc, "✈️", R.raw.theme_cinematic, 30),
    BEST_MOMENTS(R.string.journey_best, R.string.journey_best_desc, "✨", R.raw.theme_nostalgic, null);

    companion object {
        fun fromName(name: String): JourneyTemplate =
            entries.firstOrNull { it.name == name.trim() } ?: WEEKLY_SUMMARY
    }
}

/** Videoya eklenebilecek geçerli fotoğraf olmadığında fırlatılır. */
class NoJourneyPhotosException : IllegalStateException("En az 1 fotoğraf olmalı")

/**
 * Zaman tünelindeki gerçek fotoğraf dosyalarından slayt gösterisi MP4'ü üretir.
 * Ara kare/dosya üretilmez: her fotoğraf doğrudan bir [EditedMediaItem] olur ve sabit süre
 * gösterilir. Şablonun müziği döngüye alınır ve fotoğraflar bitince kesilir.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class JourneyVideoMaker(private val context: Context) {

    companion object {
        private const val WIDTH = 1080
        private const val HEIGHT = 1920
        private const val FRAME_RATE = 30
        private const val PHOTO_DURATION_MS = 2_500L

        /** Yalnızca diskte gerçekten var olan, boş olmayan dosyaları bırakır. */
        fun existingPhotoFiles(paths: List<String>): List<File> =
            paths.asSequence()
                .map { it.trim().removePrefix("file://") }
                .filter { it.isNotBlank() && !it.startsWith("http") && !it.startsWith("content://") }
                .map(::File)
                .filter { it.exists() && it.isFile && it.length() > 0 }
                .distinctBy { it.absolutePath }
                .toList()
    }

    suspend fun generateVideo(
        imagePaths: List<String>,
        outputFile: File,
        templateName: String = JourneyTemplate.WEEKLY_SUMMARY.name
    ): File {
        val photos = withContext(Dispatchers.IO) { existingPhotoFiles(imagePaths) }
        if (photos.isEmpty()) throw NoJourneyPhotosException()

        val template = JourneyTemplate.fromName(templateName)
        Timber.d("Video başlıyor: ${photos.size} fotoğraf, şablon=${template.name}")

        withContext(Dispatchers.IO) { if (outputFile.exists()) outputFile.delete() }
        try {
            val composition = buildComposition(photos, template)
            // Transformer, oluşturulduğu thread'in Looper'ını kullanır: ana thread'de başlat
            withContext(Dispatchers.Main) { export(composition, outputFile) }
        } catch (e: Exception) {
            withContext(Dispatchers.IO) { outputFile.delete() }
            throw e
        }

        Timber.d("Video hazır: ${outputFile.absolutePath} (${outputFile.length()} bayt)")
        return outputFile
    }

    private fun buildComposition(photos: List<File>, template: JourneyTemplate): Composition {
        // Her fotoğraf 1080x1920 tuvale oranı korunarak sığdırılır
        val fitToCanvas = Effects(
            /* audioProcessors = */ emptyList(),
            /* videoEffects = */ listOf(Presentation.createForWidthAndHeight(WIDTH, HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT))
        )
        val photoItems = photos.map { file ->
            EditedMediaItem.Builder(
                MediaItem.Builder()
                    .setUri(Uri.fromFile(file))
                    .setMimeType(MimeTypes.IMAGE_JPEG)
                    // Media3 1.11: fotoğrafın ekranda kalma süresi MediaItem üzerinde verilir
                    // (EditedMediaItem.setDurationUs fotoğraflarda artık okunmuyor: "Could not retrieve required duration")
                    .setImageDurationMs(PHOTO_DURATION_MS)
                    .build()
            )
                .setFrameRate(FRAME_RATE)
                .setEffects(fitToCanvas)
                .build()
        }

        // Müzik döngüsel ayrı bir dizi: fotoğraf dizisi bitince otomatik kesilir
        val musicItem = EditedMediaItem.Builder(
            MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${template.musicRes}"))
        )
            .setRemoveVideo(true)
            .build()

        // Media3 1.11: her dizinin hangi izleri taşıdığı açıkça bildirilir.
        // Fotoğraf dizisi yalnızca görüntü, müzik dizisi yalnızca ses; videonun sesi müzik dizisinden gelir.
        val photoSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            .addItems(photoItems)
            .build()
        val musicSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            .addItem(musicItem)
            .setIsLooping(true)
            .build()

        return Composition.Builder(photoSequence, musicSequence).build()
    }

    private suspend fun export(composition: Composition, outputFile: File) =
        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Timber.e(exportException, "Dışa aktarma hatası (${exportException.errorCodeName})")
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                .build()

            continuation.invokeOnCancellation { transformer.cancel() }
            transformer.start(composition, outputFile.absolutePath)
        }
}
