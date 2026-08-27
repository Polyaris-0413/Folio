package com.folio.read.ui.reader

/*
 * HTML → 纯文本:epub/azw3 的正文是 HTML,阅读器需要纯文本。
 * 用 jsoup 解析取 <body>(丢弃 xml 声明/DOCTYPE/head),再删 script/style,
 * 之后逐字对齐 Legado HtmlFormatter.format 的块级标签→换行与 \s*\n+\s* 折叠逻辑。
 * 关键:不沿用旧版"用 [ \t\r\f\v] 压空白"——其中 \v 是垂直空白类、会匹配换行 \n,
 * 导致块级标签生成的换行被压成空格(这是 epub「换行极少/整章一坨」的根因,朝闻道实测 753→0)。
 * Legado 全程用 \s*\n+\s*(明确含 \n)折叠换行并保留,因此段落正常;此处依样对齐。
 * 其余实体(&amp;&lt;&gt;&quot;&#39;&apos;)补齐解码(对齐 Legado LocalBook.getContent 的 unescapeHtml4)。
 */

import org.jsoup.Jsoup

object HtmlToText {
    private val nbspRegex = Regex("(&nbsp;)+")
    private val espRegex = Regex("(&ensp;|&emsp;)")
    private val noPrintRegex = Regex("(&thinsp;|&zwnj;|&zwj;|\u2009|\u200C|\u200D)")
    private val wrapHtmlRegex = Regex("</?(?:div|p|br|hr|h\\d|article|dd|dl)[^>]*>")
    private val commentRegex = Regex("<!--[^>]*-->")
    private val otherHtmlRegex = Regex("</?[a-zA-Z]+(?=[ >])[^<>]*>")
    private val indent1Regex = Regex("\\s*\\n+\\s*")
    private val indent2Regex = Regex("^[\\n\\s]+")
    private val lastRegex = Regex("[\\n\\s]+$")
    private val ampRegex = Regex("&amp;")
    private val ltRegex = Regex("&lt;")
    private val gtRegex = Regex("&gt;")
    private val quotRegex = Regex("&quot;")
    private val aposRegex = Regex("&#39;|&apos;")

    fun convert(html: String): String {
        val body = Jsoup.parse(html).body()
        body.select("script, style").remove()
        return body.outerHtml()
            .replace(nbspRegex, " ")
            .replace(espRegex, " ")
            .replace(noPrintRegex, "")
            .replace(wrapHtmlRegex, "\n")
            .replace(commentRegex, "")
            .replace(otherHtmlRegex, "")
            .replace(ampRegex, "&")
            .replace(ltRegex, "<")
            .replace(gtRegex, ">")
            .replace(quotRegex, "\"")
            .replace(aposRegex, "'")
            .replace(indent1Regex, "\n　　")
            .replace(indent2Regex, "　　")
            .replace(lastRegex, "")
    }
}
