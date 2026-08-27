package com.folio.read.ui.reader

/*
 * .azw3/.mobi 解析:用移植的 legado lib/mobi 引擎读 PalmDB/MOBI(KF6/KF8),按 TOC 切章、
 * 逐章提正文(HTML→HtmlToText 纯文本),拼接成 Folio 阅读器需要的「整本文本 + 章节块首」。
 * 章节顺序/url 格式(「${index}:${href}」)沿用 legado MobiFile,避免 KF6/KF8 section 定位偏差。
 */

import android.content.Context
import android.net.Uri
import io.legado.app.lib.mobi.KF6Book
import io.legado.app.lib.mobi.KF8Book
import io.legado.app.lib.mobi.MobiBook
import io.legado.app.lib.mobi.MobiReader
import io.legado.app.lib.mobi.entities.TOC

object MobiParser {

    private data class MChapter(
        val title: String,
        val startUrl: String,
        val nextUrl: String?,
        val isVolume: Boolean,
        val isSkip: Boolean,
    )

    fun parse(context: Context, filePath: String): List<Chapter> {
        context.contentResolver.openFileDescriptor(Uri.parse(filePath), "r").use { pfd ->
            val book = pfd?.let { MobiReader().readMobi(it) }
                ?: throw IllegalStateException("无法读取 mobi 文件")
            val chapters = buildChapters(book)
            val result = mutableListOf<Chapter>()
            var no = 0
            for (ch in chapters) {
                if (ch.isSkip) continue
                val html = extract(book, ch)
                if (html.isBlank()) continue
                val text = HtmlToText.convert(html).trim()
                if (text.isEmpty()) continue
                val title = ch.title.ifBlank { "第${++no}章" }
                // 章节正文 html 常自带标题段(如「活着」),剥掉与章节标题相同的首段,正文从内容开始(legado 风格)
                val body = indentContent(stripLeadingTitle(text, title))
                if (body.isEmpty()) continue
                result.add(Chapter(title, body))
            }
            return result
        }
    }

    private fun buildChapters(book: MobiBook): List<MChapter> {
        val result = mutableListOf<MChapter>()
        fun append(ref: TOC) {
            val url = "${result.size}:${ref.href}"
            val ch = MChapter(ref.label, url, null, ref.subitems != null, false)
            val last = result.lastOrNull()
            // 卷名与上一章同定位(卷点)：上一章标记 skip,卷名不重复渲染
            if (last != null && last.isVolume &&
                last.startUrl.substringAfter(":") == ch.startUrl.substringAfter(":")
            ) {
                result[result.size - 1] = last.copy(isSkip = true)
            }
            result.add(ch)
            ref.subitems?.forEach { append(it) }
        }
        when (book) {
            is KF6Book -> {
                if (book.sectionIdMap[0] == null) {
                    book.sections.firstOrNull()?.let {
                        result.add(MChapter("卷首", "0:" + it.href, null, false, false))
                    }
                }
                book.toc?.forEach { append(it) }
            }
            is KF8Book -> {
                if (book.sectionIdMap[0] == null) {
                    book.sections.firstOrNull { it.href.isNotEmpty() }?.let {
                        result.add(MChapter("卷首", "0:" + it.href, null, false, false))
                    }
                }
                book.toc?.forEach { append(it) }
            }
        }
        // 每章 nextUrl = 下一章的 startUrl
        for (i in result.indices) {
            if (result[i].nextUrl == null && i + 1 < result.size) {
                result[i] = result[i].copy(nextUrl = result[i + 1].startUrl)
            }
        }
        return result
    }

    private fun extract(book: MobiBook, ch: MChapter): String = when (book) {
        is KF6Book -> extractKF6(book, ch)
        is KF8Book -> extractKF8(book, ch)
        else -> ""
    }

    private fun extractKF6(k6: KF6Book, ch: MChapter): String {
        if (ch.isVolume && ch.isSkip) return ""
        var section = k6.getSectionByHref(ch.startUrl) ?: return ""
        val sb = StringBuilder()
        sb.append(k6.getSectionText(section))
        while (true) {
            section = section.next ?: break
            if (section.href == ch.nextUrl?.substringAfter(":")) break
            if (k6.sectionIdMap[section.index] != null) break
            sb.append(k6.getSectionText(section))
        }
        return sb.toString()
    }

    private fun extractKF8(k8: KF8Book, ch: MChapter): String {
        if (ch.isVolume && ch.isSkip) return ""
        var section = k8.getSectionByHref(ch.startUrl) ?: return ""
        val sb = StringBuilder()
        sb.append(k8.getTextByHref(ch.startUrl, ch.nextUrl.orEmpty()))
        while (true) {
            section = section.next ?: break
            if (!section.linear) continue
            if (section.href == ch.nextUrl?.substringAfter(":")) break
            if (k8.sectionIdMap[section.index] != null) break
            sb.append(k8.getSectionText(section))
        }
        return sb.toString()
    }
}
