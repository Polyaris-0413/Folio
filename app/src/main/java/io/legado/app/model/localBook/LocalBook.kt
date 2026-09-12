package io.legado.app.model.localBook

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.legado.app.PortingContext
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.utils.MD5Utils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * legado `io.legado.app.model.localBook.LocalBook` 的宿主替身：只提供 `TextFile.kt`
 * 用到的 [getBookInputStream]。
 *
 * 为什么必须落成真实文件而不是直接返回 `ContentResolver.openInputStream` 的流：
 * `TextFile.getContent(chapter)` 靠 `bis.skip(chapter.start)` 定位、靠 `bis.available()`
 * 估算缓冲区（legado 本地书的输入流就是 `FileInputStream`，这两个操作都可靠）。SAF 的
 * `content://` 流并不保证 `available()` 返回剩余长度，会静默算出 0 长度缓冲区而读到空正文。
 * 因此按源文件指纹缓存到应用私有目录，再以 `FileInputStream` 提供——与 Folio 现有的
 * `EpubParser`（先复制到 cacheDir 再解析）做法一致。
 */
object LocalBook {

    private const val CACHE_DIR = "ported_txt"

    fun getBookInputStream(book: Book): InputStream {
        book.openStream?.let { return BufferedInputStream(it()) }
        return BufferedInputStream(FileInputStream(cachedFile(PortingContext.required, book.bookUrl)))
    }

    /**
     * 取缓存文件；首次或源文件已变（指纹不同）时重新复制。
     * 同一 URI 的旧指纹副本会被清掉，避免长期堆积。
     */
    private fun cachedFile(context: Context, uriString: String): File {
        val dir = File(context.filesDir, CACHE_DIR)
        dir.mkdirs()
        val prefix = MD5Utils.md5Encode16(uriString)
        val target = File(dir, "$prefix.${fingerprintOf(context, uriString)}")
        if (target.exists() && target.length() > 0) return target

        val temp = File(dir, "$prefix.tmp")
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: run {
            AppLog.put("TXT 源文件打不开: $uriString")
            error("无法打开文件: $uriString")
        }
        // 先改名再清理同前缀旧副本：中途失败也不会留下半截的正式缓存
        dir.listFiles { f -> f.name.startsWith("$prefix.") && f != temp }
            ?.forEach { it.delete() }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return target
    }

    /**
     * 源文件指纹（大小-修改时间），用于判定缓存文件是否还要重做。
     *
     * 两个列**必须分开查**：并非所有 ContentProvider 都支持 `last_modified`
     * （MediaStore 只有 `date_modified`），合并成一次查询时提供方会直接抛
     * `IllegalArgumentException: Invalid column last_modified`，导致整本书打不开。
     * 分开查还能保住大小这一半信息，缓存仍可复用。取不到就回落 "unknown"。
     */
    private fun fingerprintOf(context: Context, uriString: String): String {
        val uri = Uri.parse(uriString)
        val size = queryLongColumn(context, uri, OpenableColumns.SIZE)
        val modified = queryLongColumn(context, uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        return "$size-$modified"
    }

    private fun queryLongColumn(context: Context, uri: Uri, column: String): Long =
        runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(column)
                if (cursor.moveToFirst() && index >= 0) cursor.getLong(index) else null
            }
        }.getOrNull() ?: -1L
}
