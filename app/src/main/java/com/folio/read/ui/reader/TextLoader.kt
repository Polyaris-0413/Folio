package com.folio.read.ui.reader

/*
 * 段落处理与前言章节设计移植自 legado(https://github.com/gedoor/legado)
 * 经 legado-with-MD3(https://github.com/HapeLee/legado-with-MD3)参考
 * SPDX-License-Identifier: GPL-3.0-only
 */

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 章节:每章独立正文,标题与正文分离(标题不进 content,渲染时独立显示) */
data class Chapter(val title: String, val content: String)

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

/** 按扩展名分派读书:.txt 走 readText+processParagraphs+按标题块切章;.epub/.azw3 用解析器逐章产 Chapter */
fun readBook(context: Context, filePath: String): List<Chapter> {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "epub" -> EpubParser.parse(context, filePath)
        "azw3", "mobi" -> MobiParser.parse(context, filePath)
        else -> buildTxtChapters(readText(context, filePath))
    }
}

/** 读取 TXT:UTF-8 严格解码,失败回落 GBK(中文 txt 常见编码) */
fun readText(context: Context, filePath: String): String {
    val bytes = context.contentResolver.openInputStream(Uri.parse(filePath))?.use { it.readBytes() }
        ?: throw IllegalStateException("无法打开文件")
    if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    ) {
        return bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    }
    return runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrElse {
        Charset.forName("GBK").decode(ByteBuffer.wrap(bytes)).toString()
    }
}

/** 段落缩进 + 前言:照搬 Legado ContentProcessor.getContent(段落处理)与 TextFile.analyze(前言章节设计)。
 * 按行切分,行首尾空白(ASCII<=0x20 与全角空格)清掉,非空行前加两个全角空格,空行丢弃,
 * 章节标题行不缩进(与 Legado 一致);书内有章节标题且正文前有内容时,开头插入「前言」标题行,
 * 读者第一页即可见「前言」而非莫名杂项 */
fun processParagraphs(text: String): String {
    val lines = text.split('\n')
        .map { it.trim { c -> c.code <= 0x20 || c == '　' } }
        .filter { it.isNotEmpty() }
    val firstTitleIndex = lines.indexOfFirst { ChapterDetector.isTitleLine(it) }
    val body = lines.joinToString("\n") { line ->
        if (ChapterDetector.isTitleLine(line)) line else "　　$line"
    }
    return if (firstTitleIndex > 0) "前言\n$body" else body
}

/** TXT 章节切分:整本处理(缩进+前言)后,按章节标题块切成「每章独立 content」。
 * 标题 = 块首行(独立,不进 content);正文 = 块内剩余行(已缩进)。
 * 无任何章节标题的书:整本作一章、标题置空(避免把首段当标题加粗)。 */
fun buildTxtChapters(rawText: String): List<Chapter> {
    val processed = processParagraphs(rawText)
    val starts = ChapterDetector.detectChapterStarts(processed)
    // ChapterDetector 无标题时回退 [0];此时整本一章且首行并非标题 → 标题置空
    val firstLineEnd = processed.indexOf('\n').let { if (it == -1) processed.length else it }
    val singleNoTitleBlock = starts.size == 1 && starts[0] == 0 &&
        !ChapterDetector.isTitleLine(processed.substring(0, firstLineEnd).trim())
    if (singleNoTitleBlock) return listOf(Chapter("", processed))
    return starts.mapIndexed { i, s ->
        val e = if (i + 1 < starts.size) starts[i + 1] else processed.length
        val lineEnd = processed.indexOf('\n', s).let { if (it == -1 || it > e) e else it }
        val title = processed.substring(s, lineEnd).trim()
        val content = processed.substring(lineEnd + 1, e).trimStart('\n', '\r')
        Chapter(title, content)
    }
}

/** 段落缩进(epub/azw3 html→纯文本后):每段前加两个全角空格,空行丢弃。
 * 照搬 Legado ContentProcessor.getContent(读者路径 includeTitle=false)末段对每段前置 paragraphIndent。
 * HtmlToText 已对齐 Legado HtmlFormatter(块级标签→换行、\s*\n+\s* 折叠),有块级标签的书此处按 \n 分段。
 * 与 processParagraphs 不同:不做「前言」插入(epub/azw3 章节来自 NCX,无前言语义),
 * 也不按章节标题行判断是否缩进(epub/azw3 正文行不作标题识别,所有段落统一缩进)。
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
