package com.folio.read.ui.reader

import android.content.Context
import com.folio.read.data.AppDatabase
import com.folio.read.data.Book
import com.folio.read.util.AppLog
import io.legado.app.model.localBook.TextFile
import java.util.regex.PatternSyntaxException

/**
 * Folio 与「移植自 legado 的 TXT 目录引擎」之间的适配层。
 *
 * 引擎本体（`io.legado.app.model.localBook.TextFile`）与 legado 源文件逐字节相同，
 * 未作任何改写（回归由 `scripts/check-port-drift.sh` 把关）。因此切章的全部行为
 * ——512KB 流式分块、字节偏移、规则打分择优（相邻命中间隔 >1000 才计分）、
 * 序章/前言章、连续标题合并、超长章节按 100KB 拆分、无规则时按 10KB 字数分章——
 * 都由引擎原样决定。本文件只做四件宿主适配：
 *
 * 1. 把 Folio 的 [Book] 映射成引擎要的载体（`tocRule` ↔ legado 的 `book.tocUrl`）；
 * 2. 从引擎按字节偏移取出每章正文（引擎的 `getContent` 已去掉标题行）；
 * 3. 套用 Folio 的段落缩进（legado 把缩进放在阅读期 `ContentProcessor`，与此同层）；
 * 4. 把引擎在本次解析中选中的规则回传给调用方，供 `books.tocRule` 记忆。
 */
object TxtTocEngine {

    private const val TAG = "FolioToc"

    /**
     * 预览用：以指定规则对本书试切一次，只返回章节数。
     *
     * 与 [parse] 的区别是**不取正文、不落库**——规则预览要对每条规则各跑一次，
     * 取正文会把整本书转成字符串，代价与内存都不可接受。
     * 落库也刻意不做：试切不应改变本书实际使用的规则。
     *
     * 注意：空规则（legado 的兜底条目）不适用本函数——传空串会被引擎当作"自动择优"，
     * 而该条目的真实语义是"按字数分章"，调用方应直接按此语义展示，不必计算。
     */
    suspend fun countChaptersWithRule(book: Book, rule: String): Int {
        val carrier = io.legado.app.data.entities.Book(
            bookUrl = book.filePath,
            originName = book.filePath.substringAfterLast('/'),
            tocUrl = rule,
            // 试切时不拆分超长章:拆分会把章数放大,不利于横向比较各规则的切分粒度
            splitLongChapterEnabled = false,
        )
        TextFile.clear()
        return try {
            TextFile.getChapterList(carrier).size
        } finally {
            TextFile.clear()
        }
    }

    /**
     * 解析 TXT 目录与正文。
     *
     * 规则落库（`books.tocRule`）就在这里做，而不是交给三个调用方：生效规则只在本函数中
     * 确定，而章节缓存键用的正是它——若某个装载路径忘了回写，预读存下的章节会因键不一致
     * 被下一次打开丢弃、白解析一遍。集中在一处可保证三条路径的键永远一致。
     *
     * @param splitLongChapter 单章超过 100KB 时是否按字数再拆（legado 的
     *   `ReadConfig.splitLongChapter`，其默认值为 true）
     * @return [BookContent.tocRule] 为本次实际生效的目录正则；空串表示引擎走了
     *   「无规则→按字数分章」的兜底
     */
    suspend fun parse(context: Context, book: Book, splitLongChapter: Boolean = true): BookContent {
        val carrier = io.legado.app.data.entities.Book(
            bookUrl = book.filePath,
            originName = book.filePath.substringAfterLast('/'),
            // 书内记住的规则(空=自动择优),对应 legado 的 book.tocUrl
            tocUrl = book.tocRule,
            splitLongChapterEnabled = splitLongChapter,
        )
        // 引擎按 bookUrl 缓存单例(含 8MB 滑动缓冲与字符集)。这里每次解析都先重置:
        // 源文件被外部替换时指纹变了但 bookUrl 不变,复用旧缓冲会读出错位的正文。
        TextFile.clear()
        var chapters = try {
            TextFile.getChapterList(carrier)
        } catch (e: PatternSyntaxException) {
            // 记住的规则可能被删改坏了。legado 在原位不处理(会直接抛出),这里退回自动择优,
            // 避免一本书因一条坏规则彻底打不开
            AppLog.w(TAG, "记住的目录规则语法错误,退回自动择优: ${carrier.tocUrl}", e)
            carrier.tocUrl = ""
            TextFile.clear()
            TextFile.getChapterList(carrier)
        }
        // 记住的规则一条都匹配不到时,引擎会返回空列表——空目录等于书打不开。
        // 规则预览里这类规则显示为「0 章」,用户可以避开,但仍需兜底:退回自动择优
        if (chapters.isEmpty() && carrier.tocUrl.isNotBlank()) {
            AppLog.w(TAG, "记住的目录规则无任何匹配,退回自动择优: ${carrier.tocUrl}")
            carrier.tocUrl = ""
            TextFile.clear()
            chapters = TextFile.getChapterList(carrier)
        }
        return try {
            val parsed = chapters.map { chapter ->
                val content = runCatching { TextFile.getContent(carrier, chapter) }.getOrElse { e ->
                    AppLog.w(TAG, "章节正文读取失败(index=${chapter.index}): $e", e)
                    ""
                }
                // 正则的 ^[ 　\t]{0,4} 会把行首缩进吃进 group,标题需 trim 后展示
                Chapter(title = chapter.title.trim(), content = indentContent(content))
            }
            val effectiveRule = carrier.tocUrl
            if (book.tocRule.isBlank() && effectiveRule.isNotBlank()) {
                runCatching { AppDatabase.getInstance(context).bookDao().updateTocRule(book.id, effectiveRule) }
                    .onFailure { AppLog.w(TAG, "目录规则落库失败(不影响本次阅读): $it", it) }
            }
            BookContent(parsed, effectiveRule)
        } finally {
            // 释放引擎持有的滑动缓冲(整本正文已转成 Chapter 字符串)
            TextFile.clear()
        }
    }
}
