package com.folio.read.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.shelfSyncDataStore by preferencesDataStore(name = "shelf_sync_settings")

/** 自动同步书架开关配置 */
data class ShelfSyncSettings(
    val enabled: Boolean = false,
)

/** 自动同步书架开关持久化(DataStore Preferences) */
class ShelfSyncSettingsRepository(context: Context) {

    private val store = PrefsStore(context.shelfSyncDataStore)

    val shelfSync: Flow<ShelfSyncSettings> = store.flowOf(KEY_ENABLED, default = false).map { enabled ->
        ShelfSyncSettings(enabled = enabled)
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.set(KEY_ENABLED, enabled)
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
    }
}
