package com.incar.assistant.config

import android.content.Context
import android.content.SharedPreferences

/**
 * 车机配置与授权凭据持久化管理 (支持手动输入与试用分发)
 */
class AppConfigManager(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("zero_auto_config", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LLM_BASE_URL = "llm_base_url"
        private const val KEY_LLM_API_KEY = "llm_api_key"
        private const val KEY_LLM_MODEL = "llm_model"

        private const val KEY_SENIVERSE_UID = "seniverse_uid"
        private const val KEY_SENIVERSE_KEY = "seniverse_key"

        // 默认内置的 10Router 网关地址
        const val DEFAULT_LLM_URL = "http://192.168.10.88:20127/v1/chat/completions"
        const val DEFAULT_LLM_MODEL = "cbcn/deepseek-v4-pro"
    }

    var llmBaseUrl: String
        get() = sp.getString(KEY_LLM_BASE_URL, DEFAULT_LLM_URL) ?: DEFAULT_LLM_URL
        set(value) = sp.edit().putString(KEY_LLM_BASE_URL, value.trim()).apply()

    var llmApiKey: String
        get() = sp.getString(KEY_LLM_API_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_LLM_API_KEY, value.trim()).apply()

    var llmModel: String
        get() = sp.getString(KEY_LLM_MODEL, DEFAULT_LLM_MODEL) ?: DEFAULT_LLM_MODEL
        set(value) = sp.edit().putString(KEY_LLM_MODEL, value.trim()).apply()

    var seniverseUid: String
        get() = sp.getString(KEY_SENIVERSE_UID, "") ?: ""
        set(value) = sp.edit().putString(KEY_SENIVERSE_UID, value.trim()).apply()

    var seniverseKey: String
        get() = sp.getString(KEY_SENIVERSE_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_SENIVERSE_KEY, value.trim()).apply()

    // 检查云端大模型是否已授权
    fun isLlmAuthorized(): Boolean = llmApiKey.isNotEmpty()

    // 检查心知天气是否已授权
    fun isSeniverseAuthorized(): Boolean = seniverseUid.isNotEmpty() && seniverseKey.isNotEmpty()
}
