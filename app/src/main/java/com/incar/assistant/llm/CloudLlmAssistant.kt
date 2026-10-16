package com.incar.assistant.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 云端大模型百科与闲聊辅助中枢
 * 对接 10Router 网关 (Claude / DeepSeek / GPT)
 * 仅在本地控制指令未命中时作为“车机外脑”兜底
 */
class CloudLlmAssistant(private val configManager: com.incar.assistant.config.AppConfigManager) {

    private val TAG = "CloudLlmAssistant"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // 车载严苛提示词：禁止废话、禁止长篇大论、1~2句话直接给出结论
    private val SYSTEM_PROMPT = """
        你是运行在车载系统上的智能副驾语音外脑。
        【车载原则】：
        1. 回复必须极其简练（严格限制在 1 到 2 句话以内，控制在 40 字以内）。
        2. 适合语音直接播报，禁止任何 Markdown 格式、列表、特殊符号。
        3. 直接给答案，严禁客套话（如“好的”、“很高兴为您解答”）。
    """.trimIndent()

    /**
     * 云端知识问答与闲聊
     */
    suspend fun askCloudLlm(question: String): String = withContext(Dispatchers.IO) {
        val apiKey = configManager.llmApiKey
        val baseUrl = configManager.llmBaseUrl
        val model = configManager.llmModel

        if (apiKey.isEmpty()) {
            return@withContext "未配置大模型 API Key，请在设置界面手动授权"
        }

        try {
            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", question)
                })
            }

            val payload = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("temperature", 0.5)
                put("max_tokens", 100)
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext "暂时无法连接到云端大脑"

            val json = JSONObject(responseBody)
            val reply = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            reply
        } catch (e: Exception) {
            Log.e(TAG, "Cloud LLM request failed: ${e.message}")
            "网络信号弱，无法连接云端大脑"
        }
    }
}
