package com.folio.read.ui.reader

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.folio.read.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书架后台预读:把「最近读的书」的正文/章节/当前章分页算进 ReaderCache,
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
    // 1) 正文:内存 → 磁盘 → 整本读,回写两层缓存
    val fp = withContext(Dispatchers.IO) {
        runCatching { querySourceFingerprint(context, book.filePath) }.getOrNull()
    } ?: return
    val text = ReaderCache.memoryLoadText(book.id, fp) ?: withContext(Dispatchers.IO) {
        ReaderCache.loadText(context, book.id, fp)
    } ?: run {
        val content = withContext(Dispatchers.IO) {
            runCatching { readText(context, book.filePath) }.getOrNull()
        } ?: return
        val processed = withContext(Dispatchers.Default) { processParagraphs(content) }
        ReaderCache.memoryStoreText(book.id, fp, processed)
        withContext(Dispatchers.IO) { ReaderCache.saveText(context, book.id, fp, processed) }
        processed
    }
    ReaderCache.memoryStoreText(book.id, fp, text)

    // 2) 章节块首:内存 → 磁盘 → 检测,回写
    val chapterStarts = ReaderCache.memoryLoadChapterStarts(book.id, fp, ChapterCacheKey)
        ?: withContext(Dispatchers.IO) {
            ReaderCache.loadChapterStarts(context, book.id, fp, ChapterCacheKey)
        }
        ?: run {
            val detected = withContext(Dispatchers.Default) { ChapterDetector.detectChapterStarts(text) }
            ReaderCache.memoryStoreChapterStarts(book.id, fp, ChapterCacheKey, detected)
            withContext(Dispatchers.IO) {
                ReaderCache.saveChapterStarts(context, book.id, fp, ChapterCacheKey, detected)
            }
            detected
        }
    ReaderCache.memoryStoreChapterStarts(book.id, fp, ChapterCacheKey, chapterStarts)

    // 3) 当前章分页:与阅读页同款测量,只填空(已有缓存不重算,避免与阅读页并发写)
    val chapters = buildChapters(text, chapterStarts)
    if (chapters.isEmpty()) return
    val idx = book.currentChapterIndex.coerceIn(0, chapters.lastIndex)
    if (ReaderCache.loadPages(context, book.id, fp, idx, textWidth, textHeight, ReaderStyleKey) != null) return
    val annotated = withContext(Dispatchers.Default) { buildAnnotatedText(text, chapterStarts) }
    val linesPerPage = with(density) {
        (textHeight / ReaderStyle.lineHeight.toPx()).toInt().coerceAtLeast(1)
    }
    val style = ReaderStyle.copy(
        lineHeight = (textHeight.toFloat() / linesPerPage / density.density / density.fontScale).sp,
    )
    withContext(Dispatchers.Default) {
        val pages = chapterPagesOf(
            annotated, chapters[idx], measurerFactory(), style, textWidth, textHeight, linesPerPage,
        )
        ReaderCache.savePages(context, book.id, fp, idx, textWidth, textHeight, ReaderStyleKey, pages)
    }
}
