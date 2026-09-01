package com.folio.read.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import com.folio.read.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.libraryDataStore by preferencesDataStore(name = "library_settings")

/** 书库候选文件:SAF document URI + 文件名 + 大小(字节),供书架页条目与批量添加使用 */
data class LibraryFile(val uri: Uri, val name: String, val size: Long)

/** 书库目录管理:登记要扫描的文件夹(SAF tree URI),供"书架添加"与自动同步使用 */
class LibraryRepository(context: Context) {

    private val store = PrefsStore(context.libraryDataStore)
    private val appContext = context.applicationContext
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

    /** 扫描书架目录下的 txt 文件(一层);书库添加页与自动同步共用。
     * @param dir 显式指定目录 URI(选目录后立即同步用,不依赖 DataStore 异步写入竞态);null 时读持久化目录 */
    suspend fun scanLibrary(dir: String? = null): List<LibraryFile> {
        val target = dir ?: libraryDir.first() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            DocumentFile.fromTreeUri(appContext, Uri.parse(target))?.listFiles()
                ?.filter { it.isFile && it.name?.let { n ->
                    n.endsWith(".txt", true) || n.endsWith(".epub", true) || n.endsWith(".azw3", true) ||
                        n.endsWith(".mobi", true)
                } == true }
                ?.map {
                    LibraryFile(
                        uri = it.uri,
                        name = it.name?.ifBlank { appContext.getString(R.string.unnamed) } ?: "",
                        size = it.length(),
                    )
                }
                ?: emptyList()
        }
    }

    private companion object {
        val KEY_LIBRARY_DIR = stringPreferencesKey("library_dir")
    }
}
