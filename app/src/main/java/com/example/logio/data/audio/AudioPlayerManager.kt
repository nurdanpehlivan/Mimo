/*
 * AudioPlayerManager: Sesli notları çalan sınıf (MediaPlayer sarmalayıcısı).
 *
 * Tek örnek (Singleton) olduğu için aynı anda yalnızca bir ses çalar ve
 * "şu an hangi dosya çalıyor" bilgisi (playingFilePath) tüm ekranlarda aynıdır.
 */
package com.example.logio.data.audio

import android.content.Context
import com.example.logio.R
import com.example.logio.util.AppLocale
import android.media.MediaPlayer
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    private val _playingFilePath = MutableStateFlow<String?>(null)
    val playingFilePath = _playingFilePath.asStateFlow()

    fun play(
        filePath: String,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        stop()
        val file = File(filePath)
        if (!file.exists()) {
            onError(AppLocale.wrap(context).getString(R.string.err_audio_not_found))
            return false
        }

        return try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, file.toUri())
                prepare()
                start()
                _playingFilePath.value = filePath
                setOnCompletionListener {
                    _playingFilePath.value = null
                    onComplete()
                }
            }
            true
        } catch (e: Exception) {
            stop()
            onError(AppLocale.wrap(context).getString(R.string.err_audio_play))
            false
        }
    }

    /** Bu dosya çalıyorsa durdurur, çalmıyorsa başlatır (kartlardaki oynat/durdur düğmesi). */
    fun toggle(filePath: String, onError: (String) -> Unit) {
        if (_playingFilePath.value == filePath) stop() else play(filePath, onError = onError)
    }

    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        _playingFilePath.value = null
    }
}