package com.folio.read.ui.reader

/*
 * 段落缩进与句读切片移植自 legado(https://github.com/gedoor/legado)
 * 经 legado-with-MD3(https://github.com/HapeLee/legado-with-MD3)参考
 * SPDX-License-Identifier: GPL-3.0-only
 */

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.folio.read.data.Book

import kotlinx.coroutines.flow.first

/** 章节:每章独立正文,标题与正文分离(标题不进 content,渲染时独立显示) */
data class Chapter(val title: String, val content: String)

/**
 * 解析结果:章节列表 + 本次实际生效的 TXT 目录规则。
 * tocRule 为空串表示两种情况:引擎走了「无规则→按字数分章」的兜底,或本书不是 TXT
 * (epub/mobi 的章节来自各自解析器,与目录规则无关)。
 */
data class BookContent(val chapters: List<Chapter>, val tocRule: String = "")

/** 源文件指纹:SAF 可查询的大小与最后修改时间,文件变更后缓存自动失效 */
fun querySourceFingerprint(context: Context, filePath: String): String? =
    context.contentResolver.query(
        Uri.parse(filePath),
        arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val size = cursor.getColumnIndex(OpenableColumns.SIZE)
                .takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: -1L
            val lastModified = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                .takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: -1L
            ReaderCache.sourceFingerprint(size, lastModified)
        } else {
            null
        }
    }

/**
 * 按扩展名分派读书。
 * epub/azw3/mobi 用各自的解析器逐章产出;其余(含 .txt)交给移植自 legado 的目录引擎
 * ([TxtTocEngine]):内置规则库打分择优 → 按正则切章(字节偏移) → 无规则时按 10KB 字数分章。
 * 相比此前的「单条硬编码正则 + 整本一章兜底」,规则可增删改,且任何书都有可跳转的目录。
 */
suspend fun readBook(context: Context, book: Book): BookContent {
    val ext = book.filePath.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "epub" -> BookContent(EpubParser.parse(context, book.filePath))
        "azw3", "mobi" -> BookContent(MobiParser.parse(context, book.filePath))
        else -> TxtTocEngine.parse(context, book)
    }
}

/** 段落缩进(epub/azw3 html→纯文本后,以及目录引擎取出的每章正文):每段前加两个全角空格,空行丢弃。
 * 照搬 Legado ContentProcessor.getContent(读者路径 includeTitle=false)末段对每段前置 paragraphIndent。
 * HtmlToText 已对齐 Legado HtmlFormatter(块级标签→换行、\s*\n+\s* 折叠),有块级标签的书此处按 \n 分段。
 * 无 <p>/<div>/<br> 块级标签的 epub(如罗杰疑案)HtmlToText 产出一整行、段落间以句末标点后空格分隔;
 * 此种零换行时把「句末标点+空格」转成换行作段落分隔——中文正文句末后本不空格,空格即段落区分;
 * 引号/括号内句子末尾后紧跟引号,空格前不是句末标点,不会误切对话。 */
internal fun indentContent(text: String): String {
    var t = text
    if (!t.contains('\n')) {
        t = t.replace(Regex("""(?<=[。？！!?])[ \t]+"""), "\n")
    }
    return t.split('\n')
        .map { it.trim { c -> c.code <= 0x20 || c == '　' } }
        .filter { it.isNotEmpty() }
        .joinToString("\n") { "　　$it" }
}

/**
 * 剥离正文开头与章节标题相同的标题段(epub/mobi 正文 html 常自带标题段,避免正文重复标题)。
 * 标题与正文可能同行(如「活着　我比现在年轻...」),也可能被正文拆成多行——章节标题
 * 「第一章 谢泼德医生在早餐桌上」在正文里是「第一章」「谢泼德医生在早餐桌上」两行(中间换行),
 * 故不能用 startsWith(标题空格≠正文换行)。对齐 Legado 去除重复标题:标题内空白用 [\s\u3000]+ 匹配
 * (覆盖空格/换行/全角缩进),标题前后允许空白/全角;标题即整段内容(如「卷首」元数据段)时正文判空返回 ""。
 * 仅剥一次,不误伤后续正文。
 */
internal fun stripLeadingTitle(text: String, title: String): String {
    val t = title.trim()
    if (t.isEmpty()) return text
    val re = buildString {
        append("^[\\s\\u3000]*")
        for (ch in t) {
            when {
                ch.isWhitespace() -> append("[\\s\\u3000]+")
                ch in """\.^$|?*+()[]{}""" -> append('\\').append(ch)
                else -> append(ch)
            }
        }
        append("[\\s\\u3000]*")
    }
    val m = Regex(re).find(text) ?: return text
    return text.removeRange(0, m.range.last + 1).trimStart()
}

/**
 * 按句切分(朗读单元/高亮粒度):句末标点 。？！?!(含中文省略号)后断句,
 * 标点保留在句尾;句尾紧跟的闭合引号/括号归属本句(如「他说。」→「他说。」)。
 * 英文句号 '.' 前后都是数字时不切(小数 3.14 不断开);连续省略号合并。
 */
fun splitSentences(text: String): List<String> {
    val result = mutableListOf<String>()
    var start = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val isEnd = when (c) {
            '。', '？', '！', '?', '!', '…' -> true
            // 英文句号:前后都是数字视为小数点,不断
            '.' -> !(i > start && i + 1 < text.length && text[i - 1].isDigit() && text[i + 1].isDigit())
            else -> false
        }
        if (isEnd) {
            // 吞掉句尾闭合引号/括号,整句一个单元
            var end = i + 1
            while (end < text.length && text[end] in "\"'”’」』)]）") end++
            result.add(text.substring(start, end))
            start = end
            i = end
            continue
        }
        i++
    }
    if (start < text.length) result.add(text.substring(start))
    return result.filter { it.isNotBlank() }
}
