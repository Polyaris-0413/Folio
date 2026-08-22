package com.folio.read.ui.reader

import android.content.Context
import java.io.File

/**
 * 阅读缓存:解码后的正文 + 分页边界,按源文件指纹失效。
 * 正文与阅读区尺寸无关,只按源文件指纹键控;分页依赖阅读区宽高,键控含宽高。
 * 源文件指纹取 SAF 可查询的 size|lastModified,文件变更后自动重新读取/分页。
 */
object ReaderCache {

    /** 进程内缓存:同一进程重开直接命中,避免每次从磁盘重读整本正文 */
    private var memText: Triple<Long, String?, String>? = null // bookId, sourceFp, text
    private var memChapter: Triple<Long, String, List<Int>>? = null // bookId, chapterKey, chapterStarts

    fun memoryLoadText(bookId: Long, sourceFp: String?): String? {
        val c = memText ?: return null
        return if (c.first == bookId && c.second == sourceFp) c.third else null
    }

    fun memoryStoreText(bookId: Long, sourceFp: String?, text: String) {
        memText = Triple(bookId, sourceFp, text)
    }

    fun memoryLoadChapterStarts(bookId: Long, sourceFp: String, ruleVersion: String): List<Int>? {
        val c = memChapter ?: return null
        return if (c.first == bookId && c.second == "$sourceFp|$ruleVersion") c.third else null
    }

    fun memoryStoreChapterStarts(bookId: Long, sourceFp: String, ruleVersion: String, starts: List<Int>) {
        memChapter = Triple(bookId, "$sourceFp|$ruleVersion", starts)
    }

    private fun file(context: Context, bookId: Long, suffix: String): File {
        val dir = File(context.filesDir, "reader")
        dir.mkdirs()
        return File(dir, "$bookId.$suffix")
    }

    fun sourceFingerprint(size: Long, lastModified: Long): String = "$size|$lastModified"

    /** 读正文缓存;指纹或文本处理版本不匹配、文件缺失返回 null */
    fun loadText(context: Context, bookId: Long, sourceFp: String): String? {
        val meta = file(context, bookId, "meta").takeIf { it.exists() }?.readText() ?: return null
        if (meta != "$sourceFp|$TextProcessVersion") return null
        return file(context, bookId, "txt").takeIf { it.exists() }?.readText()
    }

    fun saveText(context: Context, bookId: Long, sourceFp: String, text: String) {
        file(context, bookId, "meta").writeText("$sourceFp|$TextProcessVersion")
        file(context, bookId, "txt").writeText(text)
    }

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

    /** 章节块首缓存:键 = 源文件指纹 + 章节规则版本,规则变化自动失效 */
    fun loadChapterStarts(context: Context, bookId: Long, sourceFp: String, ruleVersion: String): List<Int>? {
        val key = "$sourceFp|$ruleVersion"
        val meta = file(context, bookId, "cmeta").takeIf { it.exists() }?.readText() ?: return null
        if (meta != key) return null
        return file(context, bookId, "chapters").takeIf { it.exists() }
            ?.readLines()
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
    }

    fun saveChapterStarts(context: Context, bookId: Long, sourceFp: String, ruleVersion: String, starts: List<Int>) {
        file(context, bookId, "cmeta").writeText("$sourceFp|$ruleVersion")
        file(context, bookId, "chapters").writeText(starts.joinToString("\n"))
    }
}
