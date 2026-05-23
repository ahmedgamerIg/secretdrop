package com.example.api

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

// --- Raw network models matching Gemini 1.5/3.5 API structure ---

data class Part(
    val text: String? = null
)

data class Content(
    val parts: List<Part>
)

data class ResponseFormatText(
    val mimeType: String,
    val schema: Map<String, Any>? = null
)

data class ResponseFormat(
    val text: ResponseFormatText? = null
)

data class GenerationConfig(
    val responseFormat: ResponseFormat? = null,
    val temperature: Float? = null
)

data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

// --- Structuring our specific analytical response ---
data class GeminiAnalysisResult(
    val isApproved: Boolean,
    val moderationReason: String,
    val censoredText: String,
    val riskScore: Any, // Can be Double/Long from Moshi
    val dramaticTitle: String,
    val suggestedCategory: String,
    val emotions: Map<String, Double>
) {
    // Helper to get normalized risk
    val parsedRisk: Double
        get() = when (val s = riskScore) {
            is Double -> s
            is Float -> s.toDouble()
            is Number -> s.toDouble()
            else -> 0.0
        }
}

object GeminiApiHelper {
    private const val TAG = "GeminiApiHelper"
    private const val MODEL_NAME = "gemini-3.5-flash"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Moderates and performs advanced analytics on a secret confession.
     * Guaranteed safe fallback if offline or API key is absent.
     */
    suspend fun analyzeConfession(text: String): GeminiAnalysisResult {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is not configured. Falling back to local heuristics!")
            return generateLocalFallback(text)
        }

        try {
            // Define structural schema in raw Map formats compatible with JSON
            val schemaProperties = mapOf(
                "isApproved" to mapOf("type" to "BOOLEAN", "description" to "True if safe, false if extremely hateful, abuse, gore, explicit language, or spam link."),
                "moderationReason" to mapOf("type" to "STRING", "description" to "Reasoning of action"),
                "censoredText" to mapOf("type" to "STRING", "description" to "Filtered version of text replacing any mild profanity or extreme slurs with ****"),
                "riskScore" to mapOf("type" to "NUMBER", "description" to "Toxicity score from 0.0 to 1.0"),
                "dramaticTitle" to mapOf("type" to "STRING", "description" to "A very emotional dramatic, cinematic short title (max 5 words). E.g. Midnight Tears, Echo of Regret."),
                "suggestedCategory" to mapOf("type" to "STRING", "description" to "One category from: Love, Guilt, Regret, Mystery, Work, Fear, Hope, Passion"),
                "emotions" to mapOf(
                    "type" to "OBJECT",
                    "properties" to mapOf(
                        "love" to mapOf("type" to "NUMBER", "description" to "emotional distribution in %"),
                        "sad" to mapOf("type" to "NUMBER"),
                        "dead" to mapOf("type" to "NUMBER"),
                        "fire" to mapOf("type" to "NUMBER"),
                        "shocked" to mapOf("type" to "NUMBER"),
                        "angry" to mapOf("type" to "NUMBER"),
                        "funny" to mapOf("type" to "NUMBER"),
                        "support" to mapOf("type" to "NUMBER")
                    ),
                    "required" to listOf("love", "sad", "dead", "fire", "shocked", "angry", "funny", "support")
                )
            )

            val schemaMap = mapOf(
                "type" to "OBJECT",
                "properties" to schemaProperties,
                "required" to listOf("isApproved", "moderationReason", "censoredText", "riskScore", "dramaticTitle", "suggestedCategory", "emotions")
            )

            // Construct the REST call JSON payload
            val prompt = "Analyze this anonymous secret confession. Perform safety moderation, censor toxic slurs/profanities with ****, write a dramatic viral title suitable for TikTok cards, suggest a category, and allocate emotional weights out of 100:\n\nConfession: \"$text\""
            
            val payloadMap = mapOf(
                "contents" to listOf(
                    mapOf("parts" to listOf(mapOf("text" to prompt)))
                ),
                "generationConfig" to mapOf(
                    "responseFormat" to mapOf(
                        "text" to mapOf(
                            "mimeType" to "application/json",
                            "schema" to schemaMap
                        )
                    ),
                    "temperature" to 0.4
                ),
                "systemInstruction" to mapOf(
                    "parts" to listOf(mapOf("text" to "You are WhisperAI, a highly secure, emotionally perceptive metadata assistant. You return strict parsable JSON according to the schema specified."))
                )
            )

            val payloadJson = moshi.adapter(Map::class.java).toJson(payloadMap)
            val mediaType = "application/json".toMediaType()
            val requestBody = payloadJson.toRequestBody(mediaType)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = response.body?.string() ?: ""
                    Log.e(TAG, "Request failed: Code ${response.code}, Msg: $errorMsg")
                    return generateLocalFallback(text)
                }

                val responseBody = response.body?.string() ?: return generateLocalFallback(text)
                Log.d(TAG, "Gemini Response: $responseBody")

                // Extract text response from Gemini candidates array
                val responseMap = moshi.adapter(Map::class.java).fromJson(responseBody)
                val candidates = responseMap?.get("candidates") as? List<*>
                val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
                val content = firstCandidate?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val firstPart = parts?.firstOrNull() as? Map<*, *>
                val resultText = firstPart?.get("text") as? String

                if (resultText != null) {
                    // Parse candidate result Text back into GeminiAnalysisResult
                    val adapter = moshi.adapter(Map::class.java)
                    val resultJson = adapter.fromJson(resultText)
                    if (resultJson != null) {
                        return parseJsonResult(resultJson)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network or parsing exception", e)
        }

        return generateLocalFallback(text)
    }

    private fun parseJsonResult(map: Map<*, *>): GeminiAnalysisResult {
        val isApproved = map["isApproved"] as? Boolean ?: true
        val moderationReason = map["moderationReason"] as? String ?: "Sufficient safety thresholds."
        val censoredText = map["censoredText"] as? String ?: ""
        val riskScore = map["riskScore"] ?: 0.0
        val dramaticTitle = map["dramaticTitle"] as? String ?: "Silent Whispers"
        val suggestedCategory = map["suggestedCategory"] as? String ?: "Mystery"
        
        val rawEmotions = map["emotions"] as? Map<*, *>
        val parsedEmotions = mutableMapOf<String, Double>()
        
        val keys = listOf("love", "sad", "dead", "fire", "shocked", "angry", "funny", "support")
        for (key in keys) {
            val v = rawEmotions?.get(key)
            val num = when (v) {
                is Double -> v
                is Float -> v.toDouble()
                is Number -> v.toDouble()
                else -> 12.5 // equal split
            }
            parsedEmotions[key] = num
        }

        return GeminiAnalysisResult(
            isApproved = isApproved,
            moderationReason = moderationReason,
            censoredText = censoredText,
            riskScore = riskScore,
            dramaticTitle = dramaticTitle,
            suggestedCategory = suggestedCategory,
            emotions = parsedEmotions
        )
    }

    /**
     * Local parser/heuristic generator when offline or API key is absent.
     * Incorporates local regex profanity filter and emotional allocation.
     */
    fun generateLocalFallback(text: String): GeminiAnalysisResult {
        // Simple local profanity check
        val profanities = listOf("fuck", "shit", "bitch", "asshole", "bastard", "cunt", "kill myself", "scam", "http")
        var censored = text
        var risk = 0.0
        var isApproved = true
        var reason = "Cleared locally"

        for (word in profanities) {
            if (censored.lowercase().contains(word)) {
                if (word == "scam" || word == "http") {
                    isApproved = false
                    reason = "Scan links / promotional content pattern matched."
                } else if (word == "kill myself") {
                    isApproved = false
                    reason = "Contains thoughts of extreme harm or self-injury."
                }

                // Censor mild profanities with ****
                val regex = "(?i)$word"
                censored = censored.replace(regex.toRegex(), "****")
                risk += 0.3
            }
        }

        if (risk > 1.0) risk = 1.0

        // Generate dynamic title based on length and keywords
        val trimmed = text.trim()
        val wordList = trimmed.split("\\s+".toRegex())
        val dramaticTitle = when {
            trimmed.lowercase().contains("love") || trimmed.lowercase().contains("heart") -> "Stolen Heartstrings"
            trimmed.lowercase().contains("sorry") || trimmed.lowercase().contains("regret") -> "Midnight Apology"
            trimmed.lowercase().contains("work") || trimmed.lowercase().contains("boss") -> "Office Confession"
            trimmed.lowercase().contains("scared") || trimmed.lowercase().contains("fear") -> "Timid Shadows"
            wordList.size >= 3 -> "${wordList[0].capitalize()} ${wordList[1].lowercase()} ${wordList[2].lowercase()}..."
            else -> "Unspoken Secret"
        }

        // Suggest a category locally based on content
        val lowerText = text.lowercase()
        val category = when {
            lowerText.contains("love") || lowerText.contains("crush") || lowerText.contains("marry") -> "Love"
            lowerText.contains("sorry") || lowerText.contains("forgive") || lowerText.contains("wrong") || lowerText.contains("guilt") -> "Guilt"
            lowerText.contains("wish") || lowerText.contains("regret") || lowerText.contains("should have") -> "Regret"
            lowerText.contains("work") || lowerText.contains("job") || lowerText.contains("office") || lowerText.contains("money") -> "Work"
            lowerText.contains("fear") || lowerText.contains("scared") || lowerText.contains("dark") || lowerText.contains("hide") -> "Fear"
            lowerText.contains("hope") || lowerText.contains("pray") || lowerText.contains("dream") || lowerText.contains("will be") -> "Hope"
            else -> "Mystery"
        }

        // Generate logical emotional weights (adds up to 100%)
        val scores = mutableMapOf<String, Double>()
        when (category) {
            "Love" -> {
                scores["love"] = 60.0
                scores["support"] = 20.0
                scores["fire"] = 10.0
                scores["sad"] = 5.0
                scores["funny"] = 5.0
            }
            "Guilt", "Regret" -> {
                scores["sad"] = 50.0
                scores["support"] = 30.0
                scores["love"] = 10.0
                scores["shocked"] = 10.0
            }
            "Fear" -> {
                scores["shocked"] = 40.0
                scores["sad"] = 30.0
                scores["support"] = 20.0
                scores["watching"] = 10.0
            }
            else -> {
                // Balanced split
                scores["love"] = 15.0
                scores["sad"] = 15.0
                scores["dead"] = 10.0
                scores["fire"] = 10.0
                scores["shocked"] = 10.0
                scores["angry"] = 10.0
                scores["funny"] = 15.0
                scores["support"] = 15.0
            }
        }

        // Ensure all 8 emotions are filled
        val keys = listOf("love", "sad", "dead", "fire", "shocked", "angry", "funny", "support")
        var sum = scores.values.sum()
        val remainingKeys = keys.filter { !scores.containsKey(it) }
        val remainingVal = (100.0 - sum) / remainingKeys.size.coerceAtLeast(1)
        
        for (key in keys) {
            if (!scores.containsKey(key)) {
                scores[key] = remainingVal
            }
        }

        return GeminiAnalysisResult(
            isApproved = isApproved,
            moderationReason = reason,
            censoredText = censored,
            riskScore = risk,
            dramaticTitle = dramaticTitle,
            suggestedCategory = category,
            emotions = scores
        )
    }
}
