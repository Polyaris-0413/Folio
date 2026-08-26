package com.folio.read.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

/** 主题设置持久化(DataStore Preferences):跟随系统开关 + 手动深浅 + Material You 动态取色 */
class SettingsRepository(context: Context) {

    private val store = PrefsStore(context.themeDataStore)

    val followSystemTheme: Flow<Boolean> = store.flowOf(KEY_FOLLOW_SYSTEM, true)
    val manualDark: Flow<Boolean> = store.flowOf(KEY_MANUAL_DARK, false)
    val dynamicColor: Flow<Boolean> = store.flowOf(KEY_DYNAMIC_COLOR, false)

    suspend fun setFollowSystemTheme(value: Boolean) {
        store.set(KEY_FOLLOW_SYSTEM, value)
    }

    suspend fun setManualDark(value: Boolean) {
        store.set(KEY_MANUAL_DARK, value)
    }

    suspend fun setDynamicColor(value: Boolean) {
        store.set(KEY_DYNAMIC_COLOR, value)
    }

    private companion object {
        val KEY_FOLLOW_SYSTEM = booleanPreferencesKey("follow_system_theme")
        val KEY_MANUAL_DARK = booleanPreferencesKey("manual_dark")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
