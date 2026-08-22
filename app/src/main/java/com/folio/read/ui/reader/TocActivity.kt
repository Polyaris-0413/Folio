package com.folio.read.ui.reader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.folio.read.R
import com.folio.read.data.BookRepository
import com.folio.read.ui.components.FolioTopBar
import com.folio.read.ui.theme.AnimationTokens
import com.folio.read.ui.theme.FolioSeedColor
import com.folio.read.ui.theme.FolioTheme
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeNeutral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 章节目录页:独立 Activity + 系统过渡,复用 FolioTopBar/Scaffold。
 * 数据从阅读缓存读取(正文 + 章节块首),点击章节以 result 返回起始字符偏移,由阅读页跳页。
 */
class TocActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val bookId = intent?.getLongExtra(EXTRA_BOOK_ID, -1L) ?: -1L
        val currentChapter = intent?.getIntExtra(EXTRA_CURRENT_CHAPTER, -1) ?: -1
        val darkTheme = intent?.getBooleanExtra(EXTRA_DARK_THEME, false) ?: false
        // 阅读页直接传章节标题,目录零加载秒开(避免读盘等待);缺省时回退缓存加载
        val titles = intent?.getStringArrayExtra(EXTRA_TITLES)?.toList()
        // 首帧窗口背景与 Compose 主题一致(同 ReaderActivity,防深色白闪)
        val scheme = SchemeNeutral(Hct.fromInt(FolioSeedColor.toArgb()), darkTheme, contrastLevel = 0.0)
        window.decorView.setBackgroundColor(scheme.background.toInt())
        setContent {
            FolioTheme(darkTheme = darkTheme) {
                TocScreen(
                    bookId = bookId,
                    titles = titles,
                    currentChapter = currentChapter,
                    darkTheme = darkTheme,
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_CURRENT_CHAPTER = "current_chapter"
        const val EXTRA_TITLES = "titles"
        const val EXTRA_DARK_THEME = "dark_theme"
        const val EXTRA_CHAPTER_INDEX = "chapter_index"
    }
}

@Composable
private fun TocScreen(
    bookId: Long,
    titles: List<String>?,
    currentChapter: Int,
    darkTheme: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalContext.current as? Activity
    val repo = remember { BookRepository(context.applicationContext) }
    var loadedTitles by remember { mutableStateOf(titles) }
    var chapterStarts by remember { mutableStateOf<List<Int>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    // 系统栏图标明暗跟随主题
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // Intent 未带标题时回退:从阅读缓存读正文 + 章节块首(阅读页打开本书时必已写入)
    LaunchedEffect(bookId) {
        if (loadedTitles != null) return@LaunchedEffect
        val book = repo.getBook(bookId)
        val fp = book?.let { querySourceFingerprint(context, it.filePath) }
        if (book == null || fp == null) {
            loadFailed = true
            return@LaunchedEffect
        }
        val text = withContext(Dispatchers.IO) { ReaderCache.loadText(context, bookId, fp) }
        val starts = withContext(Dispatchers.IO) {
            ReaderCache.loadChapterStarts(context, bookId, fp, ChapterCacheKey)
        }
        if (text == null || starts == null || starts.isEmpty()) {
            loadFailed = true
            return@LaunchedEffect
        }
        chapterStarts = starts
        loadedTitles = starts.map { s ->
            val e = text.indexOf('\n', s).let { if (it == -1) text.length else it }
            text.substring(s, e).trim()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            FolioTopBar(
                titleRes = R.string.toc,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = if (loadFailed) 0 else if (loadedTitles == null) 1 else 2,
                animationSpec = tween(AnimationTokens.Large),
                label = "tocLoad",
            ) { state ->
                when (state) {
                    0 -> Text(
                        text = stringResource(R.string.toc_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    1 -> Unit // 加载期间不显示任何内容,就绪后淡入
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 打开即定位到当前章,免手动下滑
                        state = rememberLazyListState(initialFirstVisibleItemIndex = currentChapter.coerceAtLeast(0)),
                    ) {
                        itemsIndexed(loadedTitles!!) { index, title ->
                            val isCurrent = index == currentChapter
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = title,
                                        fontWeight = if (isCurrent) FontWeight.Bold else null,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    activity?.setResult(
                                        Activity.RESULT_OK,
                                        Intent().putExtra(TocActivity.EXTRA_CHAPTER_INDEX, index),
                                    )
                                    activity?.finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
