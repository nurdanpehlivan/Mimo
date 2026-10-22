/*
 * GeminiService: Yapay zeka işleri (yemek fotoğrafından kalori analizi, sesli notu yazıya çevirme).
 *
 * İstekler Firebase AI Logic üzerinden Gemini'ye gider: API anahtarı uygulamanın içinde DURMAZ,
 * Firebase projesinde saklanır. Firebase App Check (LogioApp → installAppCheck) her isteğe
 * "bu istek gerçekten Logio'dan geliyor" kanıtı ekler; Play Store sürümünde bu kanıt Play Integrity'dir.
 * Bir model yoğunsa ya da bulunamazsa listedeki bir sonraki model denenir.
 */
package com.example.logio.data.remote

import android.graphics.Bitmap
import android.util.Log
import timber.log.Timber
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
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
        private const val MAX_IMAGE_DIMENSION = 800
        private const val TAG = "MimoAI"
    }

    private val candidateModels = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash",
        "gemini-3.6-flash",
        "gemini-3.7-flash",
        "gemini-3.8-flash"
    )

    // Gemini Developer API (Firebase konsolunda AI Logic → "Gemini Developer API" seçili olmalı)
    private fun model(name: String, config: GenerationConfig? = null): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(modelName = name, generationConfig = config)

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
                Timber.d("Yemek analizi deneniyor (Model: $modelName)...")

                val response = model(modelName, generationConfig { responseMimeType = "application/json" })
                    .generateContent(
                        content {
                            image(scaledBitmap)
                            text(prompt)
                        }
                    )

                val rawText = response.text?.trim()
                Timber.d("Gemini ($modelName) yanıt verdi")

                if (!rawText.isNullOrBlank()) {
                    parseMealJson(rawText)?.let {
                        Timber.d("Yemek analizi başarılı: $modelName")
                        return@withContext it
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleModelError(modelName, e)
            }
        }

        Timber.e("Tüm modeller denendi.")
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
            Timber.e(e, "Yemek JSON ayrıştırma hatası")
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
                Timber.d("STT deneniyor (Model: $modelName)...")

                val response = model(modelName).generateContent(
                    content {
                        inlineData(audioBytes, "audio/mp4")
                        text(prompt)
                    }
                )

                response.text?.trim()?.takeIf { it.isNotBlank() }?.let {
                    Timber.d("Yazıya çevirme başarılı ($modelName)")
                    return@withContext it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleModelError(modelName, e)
            }
        }

        null
    }

    /**
     * Bir modelin hatasını değerlendirir: kota dolduysa kullanıcıya söylenmek üzere GeminiQuotaException fırlatır,
     * model yoğunsa kısa bir süre bekler; diğer durumlarda bir sonraki model denenir.
     */
    private suspend fun handleModelError(modelName: String, e: Exception) {
        val msg = e.message ?: ""
        // Timber release'te kapalı; Play sürümündeki sorunlar logcat'te görülebilsin diye hata türü her zaman yazılır
        Log.w(TAG, "Hata ($modelName): ${e.javaClass.simpleName}: $msg", e)

        if (isQuotaExhausted(e)) {
            throw GeminiQuotaException("Günlük ücretsiz limit doldu")
        }
        // App Check / yetki / Firebase ayarı hatası modele bağlı değil: diğer modelleri denemek boşuna,
        // üstelik her deneme App Check'e yeniden gider ve "Too many attempts" sınırına takılır
        if (isConfigurationError(msg)) {
            throw e
        }
        if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
            delay(3000L)
        }
        if (msg.contains("404") || msg.contains("NOT_FOUND")) {
            Timber.w("$modelName bulunamadı, sonraki modele geçiliyor.")
        }
    }

    private fun isConfigurationError(message: String): Boolean {
        val m = message.lowercase()
        return "app check" in m || "permission_denied" in m || "api has not been used" in m ||
            "service_disabled" in m || "api key" in m
    }

    private fun isQuotaExhausted(e: Exception): Boolean {
        if (e is QuotaExceededException) return true
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
