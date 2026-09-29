package com.example.logio.data.remote

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

    // Mentörün bahsettiği gibi uygulamanın içinde hiçbir ham API anahtarı barındırmıyoruz.
    // Firebase Vertex AI SDK, Firebase altyapısı ve tünelleriyle güvenli bağlantı sağlar.
    private val generativeModel = Firebase.vertexAI.generativeModel(
        modelName = "gemini-1.5-flash",
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    suspend fun analyzeMealImage(bitmap: Bitmap): MealAnalysisResult? = withContext(Dispatchers.IO) {
        val prompt = """
            Analyze the meal image and return raw JSON only, matching this structure exactly:
            {"mealName":"Yemek Adı","calories":450,"protein":25.0,"carbs":40.0,"fat":15.0}
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )

            val answerText = response.text ?: return@withContext null
            val cleanJson = answerText.replace("```json", "").replace("```", "").trim()
            val json = JSONObject(cleanJson)

            MealAnalysisResult(
                mealName = json.optString("mealName", "Öğün"),
                calories = json.optInt("calories", 0),
                protein = json.optDouble("protein", 0.0),
                carbs = json.optDouble("carbs", 0.0),
                fat = json.optDouble("fat", 0.0)
            )
        } catch (e: Exception) {
            Timber.e(e, "Firebase Vertex AI analizi sırasında hata oluştu")
            null
        }
    }
}
