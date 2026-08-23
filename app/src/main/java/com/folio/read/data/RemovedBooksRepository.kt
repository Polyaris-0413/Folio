package com.folio.read.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.removedBooksDataStore by preferencesDataStore(name = "removed_books")

/**
 * 「已移除书籍」清单:用户手动从书架移除的书,自动同步不再加回。
 * 存储 dedupKey(文件稳定标识)列表,用 `|` 拼接为单个 String。
 * 墓碑永久保留(Set 去重不增长):手动添加加回的书已在书架,同步本就跳过,
 * 清除墓碑无行为意义,故不提供——想加回就手动添加,墓碑不影响手动添加。
 */
class RemovedBooksRepository(context: Context) {

    private val store = PrefsStore(context.removedBooksDataStore)

    /** 已移除的 dedupKey 集合(读取时解析) */
    val removedKeys: Flow<Set<String>> = store.flowOf(KEY_REMOVED).map { raw ->
        raw?.split(SEPARATOR)?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    suspend fun removedSet(): Set<String> = removedKeys.first()

    /** 记录一本书已移除(幂等) */
    suspend fun addRemoved(dedupKey: String) {
        val current = removedSet()
        if (dedupKey in current) return
        store.set(KEY_REMOVED, (current + dedupKey).joinToString(SEPARATOR))
    }

    private companion object {
        val KEY_REMOVED = stringPreferencesKey("removed")
        const val SEPARATOR = "|"
    }
}
