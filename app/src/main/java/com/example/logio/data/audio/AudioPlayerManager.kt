package com.example.logio.data.audio

import android.content.Context
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
            onError("Ses dosyasi bulunamadi.")
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
            onError(e.localizedMessage ?: "Ses oynatilamadi.")
            false
        }
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