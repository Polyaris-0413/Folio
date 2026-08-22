package com.folio.read.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.shelfDataStore by preferencesDataStore(name = "shelf_settings")

/** 书架排版模式 */
enum class ShelfLayoutMode { ONE, ADAPTIVE }

/** 书架排版配置 */
data class ShelfLayout(
    val mode: ShelfLayoutMode,
)

/** 书架页设置持久化:网格排版 */
class ShelfSettingsRepository(context: Context) {

    private val store = PrefsStore(context.shelfDataStore)

    val shelfLayout: Flow<ShelfLayout> = store.flowOf(KEY_MODE).map { name ->
        // 历史数据里的 CUSTOM 已下线,valueOf 失败时回落单列
        val mode = name?.let { runCatching { ShelfLayoutMode.valueOf(it) }.getOrNull() }
            ?: ShelfLayoutMode.ONE
        ShelfLayout(mode = mode)
    }

    suspend fun setShelfLayout(layout: ShelfLayout) {
        store.set(KEY_MODE, layout.mode.name)
    }

    private companion object {
        val KEY_MODE = stringPreferencesKey("layout_mode")
    }
}
