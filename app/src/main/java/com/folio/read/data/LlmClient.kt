package com.folio.read.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容 Chat Completions 客户端(DeepSeek 等换 baseUrl 即用)。
 * 后续 AI 功能(书名分析/内容问答等)统一走这里。
 */
class LlmClient(
    private val config: AiConfig,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 单轮对话,返回模型回复文本;失败抛 IOException */
    suspend fun chat(systemPrompt: String, userMessage: String): String = withContext(Dispatchers.IO) {
        require(config.apiKey.isNotBlank() && config.baseUrl.isNotBlank() && config.model.isNotBlank()) {
            "请先在 设置 → AI 中填写 API Key、API 地址与模型"
        }
        val payload = JSONObject()
            .put("model", config.model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userMessage)))
            .toString()

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $body")
            }
            val json = JSONObject(body)
            val content = json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            content.trim()
        }
    }
}
