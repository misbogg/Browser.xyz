package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// --- DATA CLASSES FOR MOSHI PARSING ---
data class GeminiPart(val text: String)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiRequest(val contents: List<GeminiContent>)

data class GeminiCandidate(val content: GeminiContent?)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

object GeminiApiManager {
    private const val TAG = "GeminiApiManager"
    private const val MODEL_NAME = "gemini-3.5-flash"
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Checks if the Gemini API is configured by inspecting the API key.
     */
    fun isConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * General method to send a prompt to the Gemini API and return the response.
     */
    suspend fun generateContent(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "AI assistance is not fully configured. Please setup your GEMINI_API_KEY in the Secrets panel in AI Studio."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"
        
        val requestJson = moshi.adapter(GeminiRequest::class.java).toJson(
            GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                )
            )
        )

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    Log.e(TAG, "Request failed: ${response.code}, Body: $bodyStr")
                    return@withContext "Google Gemini API error: ${response.code}. Please ensure your API key is correct and valid."
                }
                
                val bodyString = response.body?.string() ?: ""
                val geminiResponse = moshi.adapter(GeminiResponse::class.java).fromJson(bodyString)
                val responseText = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (responseText != null) {
                    responseText
                } else {
                    Log.e(TAG, "Parsing failed. Raw body: $bodyString")
                    "The model did not return any readable insights."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            "Network error: Unable to contact Gemini AI. Details: ${e.localizedMessage}"
        }
    }

    suspend fun summarizeWebpage(title: String, url: String, contentText: String): String {
        val trimmedContent = if (contentText.length > 3500) contentText.take(3500) + "..." else contentText
        val prompt = """
            You are browser.xyz's transparent AI companion.
            Summarize the following webpage concisely in a simple, readable manner. Keep it under 150 words!
            Highlight key bullet points, any potentially trackable actions, and safety indicators.
            
            Webpage Title: $title
            Webpage URL: $url
            Content excerpt:
            $trimmedContent
        """.trimIndent()
        return generateContent(prompt)
    }

    suspend fun explainText(selectedText: String, contextUrl: String): String {
        val prompt = """
            You are browser.xyz's built-in text inspector.
            Explain the following term or text selection in plain English. Keep it clean and direct (max 100 words).
            
            Selected Text: "$selectedText"
            Context (website URL where this was selected): $contextUrl
        """.trimIndent()
        return generateContent(prompt)
    }

    suspend fun analyzePrivacyPractices(domain: String, stats: String): String {
        val prompt = """
            You are browser.xyz's live background privacy analyzer.
            Analyze the privacy practices of the website domain '$domain'.
            Based on this block activity summary of the current page:
            $stats
            
            Identify potential data collection patterns, third-party trackers, scripts, and provide actionable tips for the user. Keep the tone technical, objective, and highly transparent (max 150 words). Break it down into clean bullet points.
        """.trimIndent()
        return generateContent(prompt)
    }

    suspend fun explainBlockRules(blockedTarget: String, resourceType: String): String {
        val prompt = """
            You are browser.xyz's security logic visualizer.
            We blocked the following resource:
            Resource URL: $blockedTarget
            Resource Type: $resourceType
            
            Briefly explain why this type of asset (scripts, cookies, fingerprint attempts, tracking pixels or servers) is blocked by browser.xyz, how trackers use these files to build device profiles, and what risks this pose to the user's data rights. Clear, direct explanation under 110 words.
        """.trimIndent()
        return generateContent(prompt)
    }
}
