package com.folio.read.ui.reader

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.folio.read.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书架后台预读:把「最近读的书」的章节列表缓存进内存、当前章页表算进 ReaderCache,
 * 用户点开时缓存命中 → 冷开也秒开(对齐 legado 秒开体感,但不动阅读器整本分页架构)。
 * 预读是尽力而为:全部后台线程,失败静默;缓存已存在则跳过(只填空,不与阅读页并发写冲突)。
 * measurerFactory 由组合侧提供(在组合作用域内创建 TextMeasurer,拿到内部字体解析器);
 * textWidth/textHeight 由调用方按阅读页测量口径计算(与 ReaderPager 的 BoxWithConstraints 一致)。
 */
suspend fun preWarmBook(
    context: Context,
    book: Book,
    measurerFactory: () -> TextMeasurer,
    density: Density,
    textWidth: Int,
    textHeight: Int,
) {
    // 1) 章节列表:内存缓存 → 整本读取/解析,回写内存缓存
    val fp = withContext(Dispatchers.IO) {
        runCatching { querySourceFingerprint(context, book.filePath) }.getOrNull()
    } ?: return
    val chapters = ReaderCache.memoryLoadChapters(book.id, fp) ?: withContext(Dispatchers.IO) {
        runCatching { readBook(context, book.filePath) }.getOrNull()
    } ?: return
    ReaderCache.memoryStoreChapters(book.id, fp, chapters)
    if (chapters.isEmpty()) return

    // 2) 当前章分页:与阅读页同款测量,只填空(已有缓存不重算,避免与阅读页并发写)
    val idx = book.currentChapterIndex.coerceIn(0, chapters.lastIndex)
    if (ReaderCache.loadPages(context, book.id, fp, idx, textWidth, textHeight, ReaderStyleKey) != null) return
    val annotated = withContext(Dispatchers.Default) { buildChapterAnnotated(chapters[idx]) }
    val linesPerPage = with(density) {
        (textHeight / ReaderStyle.lineHeight.toPx()).toInt().coerceAtLeast(1)
    }
    val style = ReaderStyle.copy(
        lineHeight = (textHeight.toFloat() / linesPerPage / density.density / density.fontScale).sp,
    )
    withContext(Dispatchers.Default) {
        val pages = chapterPagesOf(
            annotated, annotated.length, measurerFactory(), style, textWidth, textHeight, linesPerPage,
        )
        ReaderCache.savePages(context, book.id, fp, idx, textWidth, textHeight, ReaderStyleKey, pages)
    }
}
