package com.folio.read.ui.reader

/*
 * HTML → 纯文本:epub/mobi 的正文是 HTML,阅读器需要纯文本。
 * 用正则近似去标签(参考 legado HtmlFormatter 思路,不引 Jsoup):
 * 块级标签→换行、去 script/style/注释/head、实体解码、去多余空白并规范段首缩进。
 * 近似即可,不规范 xhtml 后续质量问题再补 Jsoup(见阅读器支持 epub/azw3 的 plan)。
 */

object HtmlToText {
    private val scriptRegex = Regex("""(?is)<script.*?</script>""")
    private val styleRegex = Regex("""(?is)<style.*?</style>""")
    private val headRegex = Regex("""(?is)<head.*?</head>""")
    private val commentRegex = Regex("""(?is)<!--.*?-->""")
    private val blockOpenRegex = Regex("""(?i)<br\s*/?>|</?(p|div|h[1-6]|li|tr|blockquote|article|section|hr|ul|ol|table)[^>]*>""")
    private val tagRegex = Regex("""<[^>]+>""")
    private val spaceRegex = Regex("""[ \t\r\f\v]+""")
    private val leadingSpaceRegex = Regex("""\n[ \t]+""")
    private val manyNewlineRegex = Regex("""\n{3,}""")
    private val nbspRegex = Regex("\u00a0")

    fun convert(html: String): String {
        var s = html
        s = scriptRegex.replace(s, "")
        s = styleRegex.replace(s, "")
        s = headRegex.replace(s, "")
        s = commentRegex.replace(s, "")
        // 块级标签 → 换行(段落分隔)
        s = blockOpenRegex.replace(s, "\n")
        // 其余标签去掉,保留内容
        s = tagRegex.replace(s, "")
        s = decodeEntities(s)
        // 空白整理:字内连续空格压成一个;换行后去行首空格;连续空行压成一段间隔
        s = spaceRegex.replace(s, " ")
        s = leadingSpaceRegex.replace(s, "\n")
        s = manyNewlineRegex.replace(s, "\n\n")
        s = nbspRegex.replace(s, " ")
        return s.trim()
    }

    private fun decodeEntities(s: String): String {
        var r = s
        r = r.replace("&nbsp;", " ").replace("&ensp;", " ").replace("&emsp;", " ")
        r = r.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        r = r.replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        return r
    }
}
