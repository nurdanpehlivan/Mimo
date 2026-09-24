package com.example.logio.data.remote

import android.graphics.Bitmap
import android.util.Base64
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

data class MealAnalysisResult(
    val mealName: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

@Singleton
class GeminiService @Inject constructor() {

    private val apiKey = "REMOVED"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Google'ın kendi güncellediği alias'lar - model isimleri değişse bile bunlar hep geçerli kalır
    private val models = listOf("gemini-flash-latest", "gemini-pro-latest")
    private val retryDelaysMs = listOf(1500L, 3000L, 5000L)

    suspend fun analyzeMealImage(bitmap: Bitmap): MealAnalysisResult? = withContext(Dispatchers.IO) {
        val base64Image = bitmapToBase64(scaleDownBitmap(bitmap))
        val requestBody = buildRequestJson(base64Image)

        for (model in models) {
            val result = tryModel(model, requestBody)
            if (result != null) return@withContext result
        }
        null
    }

    private suspend fun tryModel(model: String, requestJson: JSONObject): MealAnalysisResult? {
        repeat(retryDelaysMs.size + 1) { attempt ->
            try {
                val (code, body) = callGemini(model, requestJson)

                if (code in 200..299) return parseMealAnalysis(body)

                if (code != 503) {
                    Timber.e("GEMINI_DEBUG: $model -> HTTP $code: $body")
                    return null // 404 gibi kalıcı hata, sıradaki modele geç
                }

                if (attempt < retryDelaysMs.size) {
                    Timber.w("GEMINI_DEBUG: $model meşgul, ${retryDelaysMs[attempt]}ms sonra tekrar denenecek")
                    delay(retryDelaysMs[attempt])
                }
            } catch (e: IOException) {
                Timber.e(e, "GEMINI_DEBUG: $model ağ hatası")
                return null
            }
        }
        return null
    }

    private fun callGemini(model: String, requestJson: JSONObject): Pair<Int, String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            Timber.d("GEMINI_DEBUG: model=$model HTTP=${response.code}")
            return response.code to responseBody
        }
    }

    private fun buildRequestJson(base64Image: String): JSONObject {
        val prompt = """
            Analyze the meal image and return raw JSON only, no markdown, no commentary:
            {"mealName":"Yemek Adı","calories":450,"protein":25.0,"carbs":40.0,"fat":15.0}
        """.trimIndent()

        return JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                    put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", base64Image)
                        })
                    })
                })
            }))
            put("generationConfig", JSONObject().apply {
                put("thinkingConfig", JSONObject().apply { put("thinkingBudget", 0) })
            })
        }
    }

    private fun parseMealAnalysis(responseBody: String): MealAnalysisResult? = try {
        val parts = JSONObject(responseBody)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")

        val answerText = (0 until parts.length())
            .map { parts.getJSONObject(it) }
            .lastOrNull { !it.optBoolean("thought", false) && it.optString("text").isNotBlank() }
            ?.getString("text")
            ?: return null

        val json = JSONObject(answerText.replace("```json", "").replace("```", "").trim())

        MealAnalysisResult(
            mealName = json.optString("mealName", "Öğün"),
            calories = json.optInt("calories", 0),
            protein = json.optDouble("protein", 0.0),
            carbs = json.optDouble("carbs", 0.0),
            fat = json.optDouble("fat", 0.0)
        )
    } catch (e: Exception) {
        Timber.e(e, "GEMINI_DEBUG: parse hatası")
        null
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleDownBitmap(bitmap: Bitmap): Bitmap {
        val max = 512
        if (bitmap.width <= max && bitmap.height <= max) return bitmap
        val ratio = bitmap.width.toFloat() / bitmap.height
        val (w, h) = if (ratio > 1) max to (max / ratio).toInt() else (max * ratio).toInt() to max
        return bitmap.scale(w, h)
    }
}