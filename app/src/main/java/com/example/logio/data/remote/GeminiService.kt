package com.example.logio.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.example.logio.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class MealAnalysisResult(
    val mealName: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

class GeminiQuotaException(message: String) : Exception(message)

@Singleton
class GeminiService @Inject constructor() {

    companion object {
        private const val TAG = "LOGIO_AI"
        private const val MAX_IMAGE_DIMENSION = 800
    }

    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val candidateModels = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash",
        "gemini-3.6-flash",
        "gemini-3.7-flash",
        "gemini-3.8-flash"
    )

    suspend fun analyzeMealImage(bitmap: Bitmap): MealAnalysisResult? = withContext(Dispatchers.IO) {
        val scaledBitmap = scaleBitmapIfNeeded(bitmap, MAX_IMAGE_DIMENSION)

        // Yemek adı uygulamanın o anki dilinde gelsin (Türkçe, English, Deutsch, Español)
        val mealLanguage = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)
        val prompt = """
            Bu yemek fotoğrafını analiz et ve sadece JSON döndür.
            Format:
            {
              "mealName": "Yemek adı ($mealLanguage dilinde)",
              "calories": 450,
              "protein": 25.0,
              "carbs": 40.0,
              "fat": 15.0
            }
            Kurallar: Sadece JSON, açıklama veya markdown ekleme.
        """.trimIndent()

        for (modelName in candidateModels) {
            try {
                Log.d(TAG, "Yemek analizi deneniyor (Model: $modelName)...")

                val model = GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                    }
                )

                val response = model.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )

                val rawText = response.text?.trim()
                Log.d(TAG, "Gemini ($modelName) cevabı: $rawText")

                if (!rawText.isNullOrBlank()) {
                    parseMealJson(rawText)?.let {
                        Log.d(TAG, "Yemek analizi başarılı: $modelName")
                        return@withContext it
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: ""
                Log.w(TAG, "Hata ($modelName): $msg")

                if (isQuotaExhausted(e)) {
                    throw GeminiQuotaException("Günlük ücretsiz limit doldu")
                }

                if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
                    delay(3000L)
                }

                if (msg.contains("404") || msg.contains("NOT_FOUND")) {
                    Log.w(TAG, "$modelName bulunamadı, sonraki modele geçiliyor.")
                }
            }
        }

        Log.e(TAG, "Tüm modeller denendi.")
        null
    }

    private fun parseMealJson(answerText: String): MealAnalysisResult? {
        val start = answerText.indexOf('{')
        val end = answerText.lastIndexOf('}')
        if (start == -1 || end <= start) return null

        return try {
            val json = JSONObject(answerText.substring(start, end + 1))
            MealAnalysisResult(
                mealName = json.optString("mealName"),
                calories = json.optInt("calories", json.optDouble("calories", 0.0).toInt()),
                protein = json.optDouble("protein", 0.0),
                carbs = json.optDouble("carbs", 0.0),
                fat = json.optDouble("fat", 0.0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Yemek JSON ayrıştırma hatası: ${e.message}")
            null
        }
    }

    suspend fun transcribeAudio(audioFilePath: String): String? = withContext(Dispatchers.IO) {
        val file = File(audioFilePath)
        if (!file.exists() || file.length() == 0L) return@withContext null

        val audioBytes = file.readBytes()
        val prompt = "Bu ses kaydında konuşulanları, konuşulan dilde (çeviri yapmadan) metne dök. Sadece söylenen kelimeleri yaz, ek açıklama yapma."

        for (modelName in candidateModels) {
            try {
                Log.d(TAG, "STT deneniyor (Model: $modelName)...")

                val model = GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey
                )

                val response = model.generateContent(
                    content {
                        blob("audio/mp4", audioBytes)
                        text(prompt)
                    }
                )

                response.text?.trim()?.takeIf { it.isNotBlank() }?.let {
                    Log.d(TAG, "STT başarılı ($modelName): $it")
                    return@withContext it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: ""
                Log.w(TAG, "STT hatası ($modelName): $msg")

                if (isQuotaExhausted(e)) {
                    throw GeminiQuotaException("Günlük ücretsiz limit doldu")
                }

                if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
                    delay(3000L)
                }
            }
        }

        null
    }

    private fun isQuotaExhausted(e: Exception): Boolean {
        val m = e.message?.lowercase() ?: return false
        return m.contains("quota exceeded") || m.contains("daily limit") || m.contains("per_day")
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height
        val newWidth = if (width > height) maxDimension else (maxDimension * ratio).toInt()
        val newHeight = if (width > height) (maxDimension / ratio).toInt() else maxDimension

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}