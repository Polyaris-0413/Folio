package com.folio.read.ui.reader

import android.content.Context
import java.io.File

/**
 * 阅读缓存:分页边界 + 进程内章节列表。
 * 正文数据(章节列表)只做进程内缓存(重新进入秒开);分页依赖阅读区宽高,按源文件指纹+宽高键控。
 * 源文件指纹取 SAF 可查询的 size|lastModified,文件变更后自动重新读取/分页。
 * 章节正文不上磁盘(本地书重解析开销低),磁盘只存每章页表(章内本地偏移)。
 */
object ReaderCache {

    /** 进程内章节列表缓存:同一进程重开直接命中,避免每次重解析整本 */
    private var memChapters: Triple<Long, String?, List<Chapter>>? = null // bookId, sourceFp, chapters

    fun memoryLoadChapters(bookId: Long, sourceFp: String?): List<Chapter>? {
        val c = memChapters ?: return null
        return if (c.first == bookId && c.second == sourceFp) c.third else null
    }

    fun memoryStoreChapters(bookId: Long, sourceFp: String?, chapters: List<Chapter>) {
        memChapters = Triple(bookId, sourceFp, chapters)
    }

    private fun file(context: Context, bookId: Long, suffix: String): File {
        val dir = File(context.filesDir, "reader")
        dir.mkdirs()
        return File(dir, "$bookId.$suffix")
    }

    fun sourceFingerprint(size: Long, lastModified: Long): String = "$size|$lastModified"

    /** 读单章分页缓存(章内起始字符下标列表,含章末哨兵);指纹/章号/尺寸/排版签名不匹配返回 null */
    fun loadPages(
        context: Context,
        bookId: Long,
        sourceFp: String,
        chapterIdx: Int,
        width: Int,
        height: Int,
        styleKey: String,
    ): List<Int>? {
        val key = "$sourceFp|$chapterIdx|$width|$height|$styleKey"
        val meta = file(context, bookId, "c$chapterIdx.pmeta").takeIf { it.exists() }?.readText() ?: return null
        if (meta != key) return null
        return file(context, bookId, "c$chapterIdx.pages").takeIf { it.exists() }
            ?.readLines()
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
    }

    fun savePages(
        context: Context,
        bookId: Long,
        sourceFp: String,
        chapterIdx: Int,
        width: Int,
        height: Int,
        styleKey: String,
        pages: List<Int>,
    ) {
        file(context, bookId, "c$chapterIdx.pmeta").writeText("$sourceFp|$chapterIdx|$width|$height|$styleKey")
        file(context, bookId, "c$chapterIdx.pages").writeText(pages.joinToString("\n"))
    }
}
