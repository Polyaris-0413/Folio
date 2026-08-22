package com.folio.read.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow

private val Context.aiDataStore by preferencesDataStore(name = "ai_settings")

/** LLM 服务配置;baseUrl/model 由用户填写,无默认值(避免锁死服务商) */
data class AiConfig(
    val apiKey: String = "",
    /** OpenAI 兼容 API 基地址,如 https://api.openai.com/v1 或 https://api.deepseek.com/v1 */
    val baseUrl: String = "",
    val model: String = "",
) {
    /** 三项配置齐全才可发起请求 */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

/** AI 配置持久化(DataStore Preferences) */
class AiSettingsRepository(context: Context) {

    private val store = PrefsStore(context.aiDataStore)

    val config: Flow<AiConfig> = kotlinx.coroutines.flow.combine(
        store.flowOf(KEY_API_KEY, ""),
        store.flowOf(KEY_BASE_URL, ""),
        store.flowOf(KEY_MODEL, ""),
    ) { key, baseUrl, model ->
        AiConfig(
            apiKey = key ?: "",
            baseUrl = baseUrl ?: "",
            model = model ?: "",
        )
    }

    suspend fun save(config: AiConfig) {
        store.set(KEY_API_KEY, config.apiKey)
        store.set(KEY_BASE_URL, config.baseUrl)
        store.set(KEY_MODEL, config.model)
    }

    private companion object {
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_MODEL = stringPreferencesKey("model")
    }
}
