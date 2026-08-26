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

/** 章节:处理后正文的字符区间 + 标题 */
data class Chapter(val start: Int, val end: Int, val title: String)

/** 由章节块首推出章节列表;无块首时全书视为一章(无标题的文本) */
fun buildChapters(text: String, chapterStarts: List<Int>): List<Chapter> {
    if (chapterStarts.isEmpty()) return listOf(Chapter(0, text.length, ""))
    return chapterStarts.mapIndexed { i, s ->
        val e = if (i + 1 < chapterStarts.size) chapterStarts[i + 1] else text.length
        val lineEnd = text.indexOf('\n', s).let { if (it == -1) text.length else it }
        Chapter(s, e, text.substring(s, lineEnd).trim())
    }
}

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

/** 解析结果:整本纯文本 + 章节块首(txt 由后续 detectChapterStarts 算,epub/azw3 由解析器直接给) */
data class ParsedBook(val text: String, val chapterStarts: List<Int>)

/** 按扩展名分派读书:.txt 走现有管线(readText+processParagraphs);.epub/.azw3 用解析器转「整本+章节块首」 */
fun readBook(context: Context, filePath: String): ParsedBook {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "epub" -> {
            val (text, starts) = EpubParser.parse(context, filePath)
            ParsedBook(text, starts)
        }
        "azw3", "mobi" -> {
            val (text, starts) = MobiParser.parse(context, filePath)
            ParsedBook(text, starts)
        }
        else -> ParsedBook(processParagraphs(readText(context, filePath)), emptyList())
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
