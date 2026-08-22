package com.folio.read.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pageTurnDataStore by preferencesDataStore(name = "page_turn_settings")

/** 翻页手势模式 */
enum class PageTurnMode { CLICK, SWIPE }

/** 翻页手势配置 */
data class PageTurnSettings(
    val mode: PageTurnMode,
)

/** 阅读页翻页手势持久化 */
class PageTurnSettingsRepository(context: Context) {

    private val store = PrefsStore(context.pageTurnDataStore)

    val pageTurn: Flow<PageTurnSettings> = store.flowOf(KEY_MODE).map { name ->
        // 历史数据里不存在的值 valueOf 失败时回落滑动翻页
        val mode = name?.let { runCatching { PageTurnMode.valueOf(it) }.getOrNull() }
            ?: PageTurnMode.SWIPE
        PageTurnSettings(mode = mode)
    }

    suspend fun setPageTurnMode(mode: PageTurnMode) {
        store.set(KEY_MODE, mode.name)
    }

    private companion object {
        val KEY_MODE = stringPreferencesKey("page_turn_mode")
    }
}
