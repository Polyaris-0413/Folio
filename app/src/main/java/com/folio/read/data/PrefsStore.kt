package com.folio.read.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore 类型化读写封装:各设置仓库共享,消除重复的 map/edit 样板。
 * 按 Key 类型重载,String 键配 String 值、Boolean 键配 Boolean 值。
 */
class PrefsStore(private val dataStore: DataStore<Preferences>) {

    fun flowOf(key: Preferences.Key<String>, default: String? = null): Flow<String?> =
        dataStore.data.map { it[key] ?: default }

    fun flowOf(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
        dataStore.data.map { it[key] ?: default }

    suspend fun set(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }
}
