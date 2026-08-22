package com.folio.read.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

/** 主题设置持久化(DataStore Preferences):跟随系统开关 + 手动深浅 */
class SettingsRepository(context: Context) {

    private val store = PrefsStore(context.themeDataStore)

    val followSystemTheme: Flow<Boolean> = store.flowOf(KEY_FOLLOW_SYSTEM, true)
    val manualDark: Flow<Boolean> = store.flowOf(KEY_MANUAL_DARK, false)

    suspend fun setFollowSystemTheme(value: Boolean) {
        store.set(KEY_FOLLOW_SYSTEM, value)
    }

    suspend fun setManualDark(value: Boolean) {
        store.set(KEY_MANUAL_DARK, value)
    }

    private companion object {
        val KEY_FOLLOW_SYSTEM = booleanPreferencesKey("follow_system_theme")
        val KEY_MANUAL_DARK = booleanPreferencesKey("manual_dark")
    }
}
