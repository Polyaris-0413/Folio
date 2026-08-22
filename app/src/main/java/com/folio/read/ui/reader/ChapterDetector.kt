package com.folio.read.ui.reader

/**
 * 章节标题识别:正则照搬 Legado 3.26 默认「目录」规则(assets/defaultData/txtTocRule.json
 * serialNumber=1)。返回每个章节标题块的「块首字符偏移」;连续标题行(中间无空行,
 * 如「第一章」+「第一章 标题」这类重复标题)合并为一个块,只记块首——分页时块首必须是某页的第一行。
 */
object ChapterDetector {

    // Legado 默认「目录」规则:行首 0-4 个半/全角空格或制表;匹配序章/楔子/正文(非完结)/终章/
    // 后记/尾声/番外,或「第X章/节/卷/集/部/篇」(中文+阿拉伯数字,数字间允许空格);标题尾限 30 字;
    // 负向断言排除「春节」「课件」「集散」「部分」「篇章」等误杀。
    // Folio 扩展①:行首允许装饰符前缀(如「☆、第一章」,网文常见),不改变规则主体;
    // 扩展②:「前言」为 Folio 追加:文本处理时会在有前置内容的书开头插入「前言」标题行,需被识别为标题
    private val TitleRegex = Regex(
        "^[ 　\t]{0,4}(?:[☆★※✿＊*·•\\-—_]+[、]?)?(?:前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|" +
            "第\\s{0,4}[\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\\s{0,4}" +
            "(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|篇(?!张))).{0,30}$",
        setOf(RegexOption.MULTILINE),
    )

    fun detectChapterStarts(text: String): List<Int> {
        val starts = ArrayList<Int>()
        var prevTitleEnd = -1 // 上一个标题行的行尾字符偏移(不含换行符)
        for (m in TitleRegex.findAll(text)) {
            val s = m.range.first
            val contiguous = prevTitleEnd >= 0 &&
                s - prevTitleEnd in 1..2 && isSingleLineBreak(text, prevTitleEnd, s)
            if (!contiguous) starts.add(s)
            prevTitleEnd = m.range.last + 1
        }
        // 无任何章节标题的书(纯段落/日期分隔的纪实文):整本当作一章,保证可读
        return starts.ifEmpty { listOf(0) }
    }

    /** 判断单行文本是否为章节标题行(段落缩进处理时标题行不缩进,保持顶格) */
    fun isTitleLine(line: String): Boolean = TitleRegex.matches(line)

    /** [from, to) 区间只含一个换行(即 \n 或 \r\n),无空行、无正文 */
    private fun isSingleLineBreak(text: String, from: Int, to: Int): Boolean {
        var i = from
        var newlineCount = 0
        while (i < to) {
            when (text[i]) {
                '\n' -> newlineCount++
                '\r' -> {}
                else -> return false
            }
            if (newlineCount > 1) return false
            i++
        }
        return i > from
    }
}
