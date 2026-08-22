package com.folio.read.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.titleCleanDataStore by preferencesDataStore(name = "title_clean_settings")

/** 书名净化开关配置 */
data class TitleCleanSettings(
    val enabled: Boolean = false,
)

/** 书名净化开关持久化(DataStore Preferences) */
class TitleCleanSettingsRepository(context: Context) {

    private val store = PrefsStore(context.titleCleanDataStore)

    val titleClean: Flow<TitleCleanSettings> = store.flowOf(KEY_ENABLED, default = false).map { enabled ->
        TitleCleanSettings(enabled = enabled)
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.set(KEY_ENABLED, enabled)
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
    }
}
