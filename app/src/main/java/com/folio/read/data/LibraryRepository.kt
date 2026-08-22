package com.folio.read.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow

private val Context.libraryDataStore by preferencesDataStore(name = "library_settings")

/** 书库目录管理:登记要扫描的文件夹(SAF tree URI),供"书库添加"扫描使用 */
class LibraryRepository(context: Context) {

    private val store = PrefsStore(context.libraryDataStore)
    private val contentResolver = context.contentResolver

    /** 当前登记的书库目录 URI(可为 null) */
    val libraryDir: Flow<String?> = store.flowOf(KEY_LIBRARY_DIR)

    /** 登记书库目录;同时持久化 SAF 读权限,保证重启后仍可访问 */
    suspend fun setLibraryDir(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        store.set(KEY_LIBRARY_DIR, uri.toString())
    }

    private companion object {
        val KEY_LIBRARY_DIR = stringPreferencesKey("library_dir")
    }
}
