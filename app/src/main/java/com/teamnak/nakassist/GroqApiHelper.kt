package com.teamnak.nakassist

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GroqApiHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val API_KEY = "YOUR_GROQ_API_KEY_HERE"
    private const val MODEL = "llama-3.3-70b-versatile"

    fun ask(
        systemPrompt: String,
        userContent: String,
        maxTokens: Int = 200,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", maxTokens)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            })
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    onError("API error ${response.code}: ${responseBody?.take(100)}")
                    return
                }
                try {
                    val answer = JSONObject(responseBody)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()
                    onResult(answer)
                } catch (e: Exception) {
                    onError("Parse error: ${e.message}")
                }
            }
        })
    }
}
