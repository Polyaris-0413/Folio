package com.folio.read.data

/*
 * 书名解析规则移植自 legado(https://github.com/gedoor/legado)
 * 经 legado-with-MD3(https://github.com/HapeLee/legado-with-MD3)参考
 * SPDX-License-Identifier: GPL-3.0-only
 */

/**
 * 从本地文件名解析书名,规则分两层:
 * 第一层照搬 Legado(LocalBook.analyzeNameAuthor + AppPattern.nameRegex):
 * 去后缀 → 《》书名优先 → " 作者：/ by" 后缀 → 兜底正则砍 " 作者/著"。
 * 第二层是本地文件名噪声清洗(用户样本实测校准,见 BookTitleParserTest):
 * 作者段(无空格也匹配)→ 序号前缀(分隔符后须非数字,防 2.5)→ 版本括号白名单词(防小雅集/综漫)
 * → 尾部 1-2 位编号(防年份)。只取书名,作者暂不存储(Book 实体无作者字段)。
 */
object BookTitleParser {

    private val nameAuthorPatterns = listOf(
        Regex("(.*?)《([^《》]+)》.*?作者：(.*)"),
        Regex("(.*?)《([^《》]+)》(.*)"),
        Regex("(^)(.+) 作者：(.+)$"),
        Regex("(^)(.+) by (.+)$"),
    )

    private val nameRegex = Regex("\\s+作\\s*者.*|\\s+\\S+\\s+著")

    /** 作者段:半角/全角冒号均可,无需前置空格(作者段后的下载编号一并带掉) */
    private val authorSuffixRegex = Regex("作\\s*者[:：].*$")

    /** 序号前缀:数字 + 分隔符,分隔符后必须非数字(挡 "2.5次元" 类书名) */
    private val prefixNumRegex = Regex("^\\d+[_\\-\\s.、]+(?=\\D)")

    /** 版本括号:白名单词匹配(全集/全X册/精校/典藏…),防误伤书名正文括号 */
    private val versionBracketRegex =
        Regex("[（(](?=[^（()）]*(?:全集|全[一二三四五六七八九十百千\\d]+册|全本|完整版|精校|精排|校对|修订|典藏|合集|整理|完结|\\d+回本)[^（()）]*)[^（()）]*[）)]")

    /** 尾部编号:1-2 位数字(下载重复标记),4 位年份不剥 */
    private val trailingNumRegex = Regex("[（(]\\d{1,2}[）)]$")

    /**
     * 营销/版本裸词:书名尾部独立出现即删(「完整系列」「全集」等资源站打包词)。
     * 只匹配尾部且要求词前有书名主体(字母/数字),防误伤书名正文:
     * 如「哈利波特系列」的「系列」不在表内(是合法书名部分),「完整系列」才删;
     * 循环剥(「凡人修仙传 完结版 免费阅读」逐个去),剥空则回退保留原结果。
     */
    private val marketingWords = listOf(
        "完整系列", "全集", "全本", "合集", "完整版", "完结版",
        "精校版", "精排版", "校对版", "修订版", "典藏版",
        "未删节版", "无删减", "免费阅读", "全文阅读",
    )
    private val marketingWordRegex = Regex("(${marketingWords.joinToString("|")})$")

    fun parse(fileName: String): String {
        val name = fileName.substringBeforeLast(".")
        nameAuthorPatterns.forEach { pattern ->
            pattern.find(name)?.let { match ->
                // 《》提取结果同样过噪声清洗(作者段/序号/版本括号/营销词表)
                return cleanNoise(match.groupValues[2].trim())
            }
        }
        // 兜底:砍掉 " 作者XXX" / " XX 著",再过噪声清洗
        return cleanNoise(name.replace(nameRegex, "").trim { it <= ' ' })
    }

    /** 文件名噪声清洗:作者段 → 序号前缀 → 版本括号 → 尾部编号 → 营销裸词 */
    private fun cleanNoise(input: String): String {
        var result = input
            .replace(authorSuffixRegex, "")
            .replace(prefixNumRegex, "")
            .replace(versionBracketRegex, "")
            .replace(trailingNumRegex, "")
            .trim()
        // 营销词循环剥(尾部匹配,删完 trim 再试下一个;剥空则回退保留)
        while (true) {
            val next = marketingWordRegex.replace(result, "").trim()
            if (next.isEmpty() || next == result) break
            result = next
        }
        return result
    }
}
