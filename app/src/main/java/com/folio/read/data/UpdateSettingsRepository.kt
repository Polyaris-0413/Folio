package com.folio.read.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore(name = "update_settings")

/** 检查更新相关设置:用户点「不再提示」的版本号(等于该版本时不弹更新提示) */
class UpdateSettingsRepository(context: Context) {

    private val store = PrefsStore(context.updateDataStore)

    val ignoredVersion: Flow<String?> = store.flowOf(KEY_IGNORED_VERSION)

    suspend fun setIgnoredVersion(version: String) {
        store.set(KEY_IGNORED_VERSION, version)
    }

    private companion object {
        val KEY_IGNORED_VERSION = stringPreferencesKey("ignored_version")
    }
}
