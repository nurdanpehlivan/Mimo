package com.example.logio.data.audio

import android.content.Context
import com.example.logio.R
import com.example.logio.util.AppLocale
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startTimeMs: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    fun startRecording(onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            val audioDir = File(context.filesDir, "audio_notes").apply { if (!exists()) mkdirs() }
            currentOutputFile = File(audioDir, "REC_${System.currentTimeMillis()}.m4a")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(currentOutputFile?.absolutePath)
                prepare()
                start()
            }

            startTimeMs = System.currentTimeMillis()
            _isRecording.value = true
            onSuccess()
        } catch (e: Exception) {
            _isRecording.value = false
            onError(AppLocale.wrap(context).getString(R.string.err_audio_record))
        }
    }

    fun stopRecording(): AudioRecordResult? {
        if (!_isRecording.value) return null

        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }

            val durationMs = System.currentTimeMillis() - startTimeMs
            val file = currentOutputFile

            _isRecording.value = false
            mediaRecorder = null

            if (file != null && file.exists()) {
                AudioRecordResult(
                    filePath = file.absolutePath,
                    durationMs = durationMs
                )
            } else {
                null
            }
        } catch (e: Exception) {
            _isRecording.value = false
            mediaRecorder = null
            null
        }
    }
}

data class AudioRecordResult(
    val filePath: String,
    val durationMs: Long
)