package com.folio.read.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow

private val Context.splitChapterDataStore by preferencesDataStore(name = "split_chapter_settings")

/**
 * 超长章节自动拆分设置。
 *
 * 语义对应 legado 的 `ReadConfig.splitLongChapter`:开启时单章超过 100KB 会按字数
 * 再拆成「原标题(1)(2)…」；关闭时保持长章整章。**默认值与 legado 一致为 true。**
 * 该值由 [com.folio.read.ui.reader.TxtTocEngine] 在解析时读取（`getSplitLongChapter()`）。
 */
class SplitChapterSettingsRepository(context: Context) {

    private val store = PrefsStore(context.splitChapterDataStore)

    val splitLongChapter: Flow<Boolean> = store.flowOf(KEY_ENABLED, default = true)

    suspend fun setEnabled(enabled: Boolean) {
        store.set(KEY_ENABLED, enabled)
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("split_long_chapter")
    }
}
