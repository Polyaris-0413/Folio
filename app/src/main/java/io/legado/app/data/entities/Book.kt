package io.legado.app.data.entities

import io.legado.app.utils.StringUtils
import java.io.InputStream
import java.nio.charset.Charset

/**
 * 目录解析的书籍载体，对应 legado `io.legado.app.data.entities.Book` 中被本地 TXT
 * 目录链路实际触及的那部分。
 *
 * 为什么单独造一个类而不是直接复用 Folio 的 `com.folio.read.data.Book`：搬过来的
 * `TextFile.kt` 是以 `class TextFile(private var book: Book)` 及 `book.xxx` 的形式书写的，
 * 本类以同名同包提供这些成员，使 `TextFile.kt` 可以逐字节保持与 legado 源文件一致
 * （零改动即零语义漂移，见 `docs/superpowers/plans/2026-09-12-legado-toc-verbatim-port.md`）。
 * 字段是「TextFile 用到什么就有什么」的最小子集，不含书架展示用的 name/author/kind/
 * latestChapterTime 等——那些属于书架，不属于目录。
 *
 * 与 legado 的对应关系：
 *  - `bookUrl`：本地书存完整文件路径，legado 同义（`Book.bookUrl` 注释「详情页Url(本地书源存储完整文件路径)」）；
 *  - `tocUrl`：legado 用它记住本书选中的 TXT 目录正则（`TextFile.getChapterList` 的
 *    `book.tocUrl = getTocRule(...)?.pattern() ?: ""`）；Folio 侧持久化到 `Book.tocRule`；
 *  - `splitLongChapter`：legado 存于 `Book.config.splitLongChapter`（`ReadBookConfig`，随书持久化）
 *    并由 `getSplitLongChapter()` 读出；Folio 由「超长章节自动拆分」设置项注入；
 *  - `fileCharset()`：legado 实现为 `charset(charset ?: "UTF-8")`，其中 `charset(String)`
 *    并非 legado 源码中的函数（全仓无定义，来自其依赖库）。此处按等价语义用 JDK 原生
 *    `Charset.forName` 实现，并把 legado `AppConst.charsets` 中 Java 不认识的 "Unicode"
 *    映射为 UTF-16，无法解析的名称回落 UTF-8（legado 的助手同样是「取不到就退回 UTF-8」）。
 */
class Book(
    var bookUrl: String = "",
    var originName: String = "",
    /** 序章内容 ≤500 字符时会被 `TextFile.analyze` 灌入，legado 用作书籍简介 */
    var intro: String? = null,
    var charset: String? = null,
    var tocUrl: String = "",
    var wordCount: String? = null,
    /**
     * 是否拆分超长章节。字段名不叫 splitLongChapter 是为了避开 JVM 签名冲突——
     * 属性 `splitLongChapter` 生成的 getter 与 `TextFile` 调用的 [getSplitLongChapter]
     * 同为 `getSplitLongChapter()Z`，二者不能共存。
     */
    var splitLongChapterEnabled: Boolean = true,
    /**
     * 内容流的注入点，仅供 JVM 单测使用（assets/ContentResolver 在纯 JVM 测试不可用）。
     * 为空时 [io.legado.app.model.localBook.LocalBook.getBookInputStream] 走 SAF 缓存。
     */
    val openStream: (() -> InputStream)? = null,
) {

    fun fileCharset(): Charset {
        return charsetOf(charset ?: "UTF-8")
    }

    fun getSplitLongChapter(): Boolean = splitLongChapterEnabled

    private fun charsetOf(name: String): Charset {
        val normalized = if (name.equals("Unicode", ignoreCase = true)) "UTF-16" else name
        return runCatching { Charset.forName(normalized) }.getOrDefault(Charsets.UTF_8)
    }

    /** 全书字数（`TextFile` 解析后写入，格式如「12.3万字」），便于调用方读取 */
    fun wordCountText(): String = StringUtils.wordCountFormat(wordCount)
}
