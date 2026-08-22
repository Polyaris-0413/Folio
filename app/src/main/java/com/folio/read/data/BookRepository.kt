package com.folio.read.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.folio.read.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** 书籍仓库:书架数据入口,基于 Room */
class BookRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(context).bookDao()
    private val contentResolver = context.contentResolver

    val books: Flow<List<Book>> = dao.observeAll()

    suspend fun getBook(id: Long): Book? = dao.getBook(id)

    /** 保存阅读位置(章节号 + 章内字符偏移) */
    suspend fun updatePosition(id: Long, chapterIndex: Int, position: Int) =
        dao.updatePosition(id, chapterIndex, position)

    /**
     * 从 SAF 选中的文件创建书籍(仅元数据,正文解析由阅读页负责);
     * 重复文件返回 null。filePath 存原始 URI(保持可读),dedupKey 归一化去重。
     */
    suspend fun addBook(uri: Uri): Book? {
        val name = queryDisplayName(uri) ?: appContext.getString(R.string.unnamed)
        val title = BookTitleParser.parse(name)
            .ifBlank { name.substringBeforeLast('.').ifBlank { name } }
        val book = Book(
            title = title,
            filePath = uri.toString(),
            dedupKey = normalizeKey(uri),
        )
        val id = dao.insert(book)
        return if (id != -1L) book.copy(id = id) else null
    }

    /**
     * AI 二次净化书名并更新库中记录;失败静默保留本地解析结果。
     * 输入必须是本地解析结果而非原始文件名:AI 只删不增,防止把本地已滤掉的噪声"还原"回来。
     */
    suspend fun aiCleanBook(book: Book, cleaner: BookTitleCleaner) {
        val cleaned = cleaner.clean(book.title) ?: return
        if (cleaned != book.title) dao.updateTitle(book.id, cleaned)
    }

    /** 从书架移除书籍(仅移除记录,不删除源文件) */
    suspend fun delete(id: Long) {
        dao.delete(id)
    }

    /** 手动重命名书名 */
    suspend fun rename(id: Long, title: String) {
        dao.updateTitle(id, title)
    }

    /** 源文件当前是否可读(SAF 查询);外部删除/权限丢失/URI 失效时返回 false,不抛异常 */
    fun isReadable(book: Book): Boolean = runCatching {
        appContext.contentResolver.query(Uri.parse(book.filePath), null, null, null, null)
            ?.use { true } ?: false
    }.getOrDefault(false)

    /**
     * 修复历史数据:把书库(tree)来源的 document 形式 filePath 重建为可读的
     * tree 形式(书架目录权限覆盖 tree 前缀 URI,document 形式不被授权)。
     * 幂等:已修复的行重建结果不变,可每次启动执行。
     */
    suspend fun repairReadablePaths(treeUri: Uri?) {
        val tree = treeUri ?: return
        val treeDocId = tree.lastPathSegment ?: return
        dao.observeAll().first().forEach { book ->
            val docId = book.filePath.substringAfterLast("/document/")
            if (docId.startsWith("$treeDocId/")) {
                val readable = DocumentsContract.buildDocumentUriUsingTree(tree, docId).toString()
                if (readable != book.filePath) {
                    dao.updateFilePath(book.id, readable)
                }
            }
        }
    }

    /**
     * 归一化去重键:取 URI 路径中最后一个 /document/ 之后的部分(文档 ID)。
     * tree 前缀形式(content://…/tree/目录/document/ID)与直接形式(content://…/document/ID)
     * 归一后一致,保证同一文件跨入口去重。
     */
    private fun normalizeKey(uri: Uri): String {
        val path = uri.path ?: return uri.toString()
        val docIndex = path.lastIndexOf("/document/")
        return if (docIndex >= 0) path.substring(docIndex + "/document/".length) else uri.toString()
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
}
