package io.legado.app.data.entities

import android.annotation.SuppressLint
import io.legado.app.utils.MD5Utils

/**
 * 目录解析产出的章节载体，移植自 legado `io.legado.app.data.entities.BookChapter`。
 *
 * 保留成员与 legado 逐字节同源（字段、equals/hashCode、primaryStr、getFileName/getFontName）。
 * 删除的成员及其理由——这些成员会牵入与目录解析无关的子系统：
 *  - `@Entity` / `@Parcelize` 及 Room、Parcelable 接口：Folio 章节只在进程内存中存活
 *    （见 `ReaderCache`），不落 `chapters` 表，也不需要跨进程传递；
 *  - `RuleDataInterface` 与 `variableMap` / `putVariable` / `putBigVariable` / `getBigVariable`：
 *    在线书源规则引擎的变量机制，牵入 GSON 与 `RuleBigDataHelp`；
 *  - `getDisplayTitle()`：阅读界面标题净化，牵入 `AppConfig` / `ChineseUtils` / `ReplaceRule` /
 *    `RegexTimeoutException` / `toastOnUi` 与 `replaceRuleDao`，属「标题替换」而非「目录」；
 *  - `getAbsoluteURL()`：在线书源相对 URL 拼接，牵入 `AnalyzeUrl` / `NetworkUtils`。
 *
 * 除上述删除外，字段定义与 `start` / `end` 的字节偏移语义均未改动——`TextFile.analyze`
 * 正是靠 `start`/`end` 定位章节正文。
 */
data class BookChapter(
    var url: String = "",               // 章节地址
    var title: String = "",             // 章节标题
    var isVolume: Boolean = false,      // 是否是卷名
    var baseUrl: String = "",           // 用来拼接相对url
    var bookUrl: String = "",           // 书籍地址
    var index: Int = 0,                 // 章节序号
    var isVip: Boolean = false,         // 是否VIP
    var isPay: Boolean = false,         // 是否已购买
    var resourceUrl: String? = null,    // 音频真实URL
    var tag: String? = null,            // 更新时间或其他章节附加信息
    var wordCount: String? = null,      // 本章节字数
    var start: Long? = null,            // 章节起始位置
    var end: Long? = null,              // 章节终止位置
    var startFragmentId: String? = null,  //EPUB书籍当前章节的fragmentId
    var endFragmentId: String? = null,    //EPUB书籍下一章节的fragmentId
    var variable: String? = null,        //变量
    var reviewImg: String? = null        //段评图标
) {

    var titleMD5: String? = null

    override fun hashCode() = url.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is BookChapter) {
            return other.url == url
        }
        return false
    }

    fun primaryStr(): String {
        return bookUrl + url
    }

    private fun ensureTitleMD5Init() {
        if (titleMD5 == null) {
            titleMD5 = MD5Utils.md5Encode16(title)
        }
    }

    @SuppressLint("DefaultLocale")
    @Suppress("unused")
    fun getFileName(suffix: String = "nb"): String {
        ensureTitleMD5Init()
        return String.format("%05d-%s.%s", index, titleMD5, suffix)
    }

    @SuppressLint("DefaultLocale")
    @Suppress("unused")
    fun getFontName(): String {
        ensureTitleMD5Init()
        return String.format("%05d-%s.ttf", index, titleMD5)
    }
}
