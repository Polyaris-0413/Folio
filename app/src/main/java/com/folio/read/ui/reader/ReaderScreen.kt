package com.folio.read.ui.reader

/*
 * 阅读排版参数(字号/行距)与章节标题加粗规则移植自 legado
 * (https://github.com/gedoor/legado),经 legado-with-MD3 参考
 * SPDX-License-Identifier: GPL-3.0-only
 */

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folio.read.R
import com.folio.read.data.Book
import com.folio.read.data.BookRepository
import com.folio.read.data.PageTurnMode
import com.folio.read.data.PageTurnSettings
import com.folio.read.data.PageTurnSettingsRepository
import com.folio.read.ui.components.FolioAlertDialog
import com.folio.read.ui.components.FolioTopBar
import com.folio.read.ui.components.menuShape
import com.folio.read.ui.theme.AnimationTokens
import com.folio.read.ui.theme.FolioSeedColor
import com.materialkolor.hct.Hct
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 阅读页目的地(单 Activity):翻页式基础浏览 + 进度记忆。
 * 正文按 TextMeasurer 精确分页(HorizontalPager 翻页),进度按「章节号 + 章内字符偏移」
 * 存入 Book(currentChapterIndex/chapterPosition),重新进入时恢复到对应页。
 * 主题/窗口由宿主统一管理;onClose 语义=离开这本书(宿主负责 markRead 置顶 + 弹回书架)。
 */
@Composable
fun ReaderScreen(
    bookId: Long,
    darkTheme: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { BookRepository(context.applicationContext) }
    // 翻页手势设置:点击/滑动;由设置页持久化,阅读页按模式决定是否启用滑动与点击层
    val pageTurnRepo = remember { PageTurnSettingsRepository(context.applicationContext) }
    val pageTurnSettings by pageTurnRepo.pageTurn.collectAsState(
        initial = PageTurnSettings(PageTurnMode.SWIPE),
    )
    val pageTurnMode = pageTurnSettings.mode
    var book by remember { mutableStateOf<Book?>(null) }
    var text by remember { mutableStateOf<String?>(null) }
    var sourceFp by remember { mutableStateOf<String?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    // 保存协程挂到独立作用域:离开目的地后进度写入不被取消
    val saveScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // 加载书籍与正文:进程内存缓存 → 磁盘缓存(IO 线程)→ 整本读取(IO 线程)
    LaunchedEffect(bookId) {
        val loaded = repo.getBook(bookId)
        if (loaded == null) {
            loadFailed = true
            return@LaunchedEffect
        }
        book = loaded
        // 文件可能被外部删除/替换:SAF 查询会抛 SecurityException,须保护(否则整页闪退);
        // 查不到指纹时跳过缓存,正文读取失败走 loadFailed 提示而非崩溃
        // SAF 指纹查询走 binder,主线程可能卡几十到几百 ms(点书瞬间 315ms 掉帧根因),挪到 IO
        val fp = withContext(Dispatchers.IO) {
            runCatching { querySourceFingerprint(context, loaded.filePath) }.getOrNull()
        }
        sourceFp = fp
        // 1) 进程内存缓存:同一进程内重开零 IO,秒出
        ReaderCache.memoryLoadText(loaded.id, fp)?.let {
            text = it
            return@LaunchedEffect
        }
        // 2) 磁盘缓存:仅跨进程(杀进程后)回退,读一次几 MB
        val cached = withContext(Dispatchers.IO) {
            fp?.let { ReaderCache.loadText(context, loaded.id, it) }
        }
        if (cached != null) {
            text = cached
            ReaderCache.memoryStoreText(loaded.id, fp, cached)
            return@LaunchedEffect
        }
        // 3) 整本读取 + 解码 + 段落缩进处理,回写两层缓存
        val content = withContext(Dispatchers.IO) {
            runCatching { readText(context, loaded.filePath) }.getOrNull()
        }
        if (content == null) {
            loadFailed = true
        } else {
            val processed = withContext(Dispatchers.Default) { processParagraphs(content) }
            text = processed
            ReaderCache.memoryStoreText(loaded.id, fp, processed)
            if (fp != null) saveScope.launch { ReaderCache.saveText(context, loaded.id, fp, processed) }
        }
    }

    // 根 Box 必须带背景色:加载/失败期间整页透明会露出窗口背景(深色下=黑闪),
    // 背景 = 主题背景色,加载空白与内容背景一致,过渡期不闪
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            // 加载中/失败都空白(失败由对话框提示,不打字面提示页)
            targetState = if (book == null || text == null || loadFailed) 0 else 1,
            animationSpec = tween(AnimationTokens.Large),
            label = "readerLoad",
        ) { state ->
            when (state) {
                0 -> Unit // 加载期间/失败:空白背景,就绪后内容淡入
                else -> ReaderPager(
                    book = book!!,
                    text = text!!,
                    bookId = bookId,
                    sourceFp = sourceFp,
                    repo = repo,
                    saveScope = saveScope,
                    darkTheme = darkTheme,
                    pageTurnMode = pageTurnMode,
                    onClose = onClose,
                )
            }
        }
    }

    // 打开失败:说明情况/原因/解法,关掉直接返回书架(不走 handleBack,避免用空进度覆盖原阅读位置)
    if (loadFailed) {
        FolioAlertDialog(
            onDismissRequest = onClose,
            title = { Text(text = stringResource(R.string.book_open_failed_title)) },
            text = { Text(text = stringResource(R.string.book_open_failed_message)) },
            confirmButton = {
                TextButton(onClick = onClose) {
                    Text(text = stringResource(R.string.book_open_failed_ok))
                }
            },
        )
    }
}

/**
 * 阅读排版参数(基础版固定,字号/行距后续做成设置项)。
 * 字号照搬 Legado 默认 20sp;行距 49sp 时每页仅 13 行、页底余白偏大,
 * 收紧为 44sp(≈字号×2.2)后每页 14 行,页底更满。
 */
// 书架预读复用(同模块 internal)
internal val ReaderStyle = TextStyle(fontSize = 20.sp, lineHeight = 44.sp)

/** 分页缓存键中的排版签名:字号/行距/章节规则/文本处理变化会使分页边界失效 */
internal val ReaderStyleKey: String =
    "${ReaderStyle.fontSize.value}x${ReaderStyle.lineHeight.value}|$ChapterRuleVersion|$TextProcessVersion"

/** 正文左右/上下留白(分页测量与渲染共用,必须一致)。
 * 水平 20dp:正文宽 = 屏宽-2×20dp,对 20sp(80px)字宽正好 16 字/行整除,右缘无剩余半字 */
internal val ReaderHPadding = 20.dp
internal val ReaderVPadding = 16.dp

/** 单章分页:章内起始字符偏移列表(绝对下标),末项 = 章末哨兵;每章十几页,毫秒级 */
internal fun chapterPagesOf(
    annotated: AnnotatedString,
    chapter: Chapter,
    measurer: TextMeasurer,
    style: TextStyle,
    maxWidth: Int,
    maxHeight: Int,
    linesPerPage: Int,
): List<Int> {
    val pages = ArrayList<Int>()
    var cur = chapter.start
    pages.add(cur)
    while (cur < chapter.end) {
        val end = nextPageEnd(
            annotated, cur, measurer, style,
            maxWidth, maxHeight, linesPerPage, linesPerPage * 120, chapter.end,
        )
        pages.add(end)
        if (end >= chapter.end) break
        cur = end
    }
    // 空章兜底:至少一页(渲染空白页)
    if (pages.size == 1) pages.add(chapter.end)
    return pages
}

/** 分页 + 翻页 + 章节模型;顶栏返回时先保存当前阅读位置 */
@Composable
private fun ReaderPager(
    book: Book,
    text: String,
    bookId: Long,
    sourceFp: String?,
    repo: BookRepository,
    saveScope: CoroutineScope,
    darkTheme: Boolean,
    pageTurnMode: PageTurnMode,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    // 章节块首识别(内存/磁盘缓存,整本扫描一次);章节模型与目录都依赖
    var chapterStarts by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(text) {
        val fp = sourceFp
        val cached = if (fp != null) {
            ReaderCache.memoryLoadChapterStarts(bookId, fp, ChapterCacheKey)
                ?: withContext(Dispatchers.IO) {
                    ReaderCache.loadChapterStarts(context, bookId, fp, ChapterCacheKey)
                }
        } else {
            null
        }
        if (cached != null) {
            chapterStarts = cached
        } else {
            val detected = withContext(Dispatchers.Default) { ChapterDetector.detectChapterStarts(text) }
            chapterStarts = detected
            if (fp != null) {
                ReaderCache.memoryStoreChapterStarts(bookId, fp, ChapterCacheKey, detected)
                saveScope.launch {
                    ReaderCache.saveChapterStarts(context, bookId, fp, ChapterCacheKey, detected)
                }
            }
        }
    }
    // 章节模型(内存切片):块 i 到块 i+1 之间为第 i 章
    val chapters = remember(chapterStarts, text) { buildChapters(text, chapterStarts ?: emptyList()) }
    // 当前章(从 Book 恢复,章节列表就绪后校正)
    var curChapter by remember(chapters) {
        mutableIntStateOf(book.currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
    }
    // 当前阅读位置(章号, 章内偏移),供顶栏返回立即保存
    var currentPosition by remember { mutableStateOf(book.currentChapterIndex to book.chapterPosition) }
    // 目录跳转的待跳章节索引;-1 = 无
    var pendingJump by remember { mutableIntStateOf(-1) }
    // 目录跳转序列号:每次跳转 +1,驱动正文淡入(key 重建 + Animatable),翻页跨章不触发
    var jumpSeq by remember { mutableIntStateOf(0) }
    // 目录覆盖层:阅读页组合保持存活(分页状态/朗读绑定不丢),行为与旧「目录 Activity 盖在阅读页上」一致
    var showToc by remember { mutableStateOf(false) }

    fun saveCurrentPage() {
        val (chapterIdx, pos) = currentPosition
        savePosition(repo, saveScope, book, chapterIdx, pos)
    }

    // 朗读:由前台服务执行(锁屏/切后台/退出阅读页都继续),阅读页只负责启动/停止与状态展示。
    // 绑定服务拿朗读状态(高亮/激活);退出阅读页只解绑,不停朗读。
    var ttsService by remember { mutableStateOf<ReaderTtsService?>(null) }
    val ttsConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                ttsService = (service as? ReaderTtsService.TtsBinder)?.service()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                ttsService = null
            }
        }
    }
    DisposableEffect(Unit) {
        context.bindService(
            Intent(context, ReaderTtsService::class.java),
            ttsConnection,
            Context.BIND_AUTO_CREATE,
        )
        onDispose {
            // 服务可能已自行停止,解绑时兜底忽略异常
            runCatching { context.unbindService(ttsConnection) }
        }
    }
    // 当前朗读段在整本正文的绝对范围(用于渲染高亮);State 收集自服务
    val ttsHighlight = ttsService?.highlightRange?.collectAsState()?.value
    // 是否正在朗读;驱动停止淡出与 toggle 图标
    val ttsActive = ttsService?.active?.collectAsState()?.value ?: false
    // 是否暂停中(滑页/跳章会自动暂停);驱动菜单按钮「继续」逻辑
    val ttsPaused = ttsService?.pausedState?.collectAsState()?.value ?: false
    // 返回阅读页=离开这本书:保存进度并停止朗读。
    // 锁屏/切后台时阅读页不退出,服务独立运行不受影响(听书场景保留);
    // 仅「退出阅读页回到主页」停止,符合「离开这本书就不该继续播」的语义
    val handleBack: () -> Unit = {
        saveCurrentPage()
        if (ttsActive) ReaderTtsService.stop(context)
        onClose()
    }

    // 系统返回键与顶栏返回走同一逻辑;目录覆盖层打开时返回键先关目录
    BackHandler(enabled = !showToc) { handleBack() }
    // 服务当前朗读章:朗读跨章时 UI 跟随切章(-1 = 未朗读/已停止)
    val ttsReadingChapter by ttsService?.readingChapter?.collectAsState() ?: remember { mutableIntStateOf(-1) }
    // 上一次看到的朗读章(外层 remember,跨 key(curChapter) 组合存活):
    // 跨章跟随只响应「朗读章真的变化」(朗读自动跨章);用户跳章/组合重建时朗读章没变,
    // 若按当前值判断会把用户跳的章拉回朗读章(「朗读中目录跳章失效」的根因)
    var prevReadingChapter by remember { mutableIntStateOf(-1) }
    // 用户手动离开朗读页(翻页/跳章):置位后朗读自动翻页不再把页面拉回朗读页
    var userLeftTts by remember { mutableStateOf(false) }
    // 朗读错误(无引擎/读不了书):弹出提示后清空,避免重复弹
    LaunchedEffect(ttsService) {
        ttsService?.errorMsg?.collect { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                ttsService?.errorMsg?.value = null
            }
        }
    }
    // Android 13+ 通知权限:首次朗读请求(拒绝也能读,只是通知不显示);授权与否都继续启动
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val (chapterIdx, pos) = currentPosition
        ReaderTtsService.start(context, bookId, chapterIdx, pos)
    }
    // 切换朗读:播放中→停止;暂停中(滑页后)→继续;未播放→从当前阅读位置开始
    val ttsToggle: () -> Unit = {
        if (ttsActive) {
            if (ttsPaused) ReaderTtsService.pauseResume(context) else ReaderTtsService.stop(context)
        } else {
            // 重新开始朗读:解除「用户已离开」,恢复自动翻页跟随
            userLeftTts = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val (chapterIdx, pos) = currentPosition
                ReaderTtsService.start(context, bookId, chapterIdx, pos)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                FolioTopBar(
                    titleRes = 0,
                    title = book.title,
                    onBack = {
                        handleBack()
                    },
                    actions = {
                        IconButton(onClick = { showToc = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_toc),
                                contentDescription = stringResource(R.string.toc),
                            )
                        }
                        // 额外功能:overflow 菜单(朗读等),容器色/圆角与书架菜单一致
                        var moreMenuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { moreMenuExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.shelf_more),
                            )
                        }
                        DropdownMenu(
                            expanded = moreMenuExpanded,
                            onDismissRequest = { moreMenuExpanded = false },
                            // 容器色用 M3 默认 surfaceContainer,圆角 = M3 菜单档(small 8dp)
                            shape = menuShape,
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.reader_tts)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_tts),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    moreMenuExpanded = false
                                    ttsToggle()
                                },
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                val textWidth = with(density) {
                    (constraints.maxWidth - ReaderHPadding.toPx() * 2).toInt()
                }
                val textHeight = with(density) {
                    (constraints.maxHeight - ReaderVPadding.toPx() * 2).toInt()
                }
                val linesPerPage = with(density) {
                    (textHeight / ReaderStyle.lineHeight.toPx()).toInt().coerceAtLeast(1)
                }
                // 行距拉宽填满页面:行高 = 区域高度 ÷ 行数,末行正好落在正文区底边,消除页底余白
                val readerStyle = remember(textHeight, linesPerPage) {
                    ReaderStyle.copy(
                        lineHeight = (textHeight.toFloat() / linesPerPage / density.density / density.fontScale).sp,
                    )
                }
                // 章节标题加粗:测量与渲染共用同一份 AnnotatedString(保证分页边界一致)。
                // 1MB 级正文拼加粗有几十 ms 主线程开销(正文到达那帧 40-60ms 掉帧根因),放后台算;
                // 章节未检测完成不拼(门禁关着不渲染,省一次无用拼装);key 变化先置 null 失效,
                // 避免分页用到旧版——分支守卫含 null,就绪前不渲染
                var annotatedState by remember { mutableStateOf<AnnotatedString?>(null) }
                LaunchedEffect(text, chapterStarts) {
                    val starts = chapterStarts ?: return@LaunchedEffect
                    annotatedState = null
                    annotatedState = withContext(Dispatchers.Default) {
                        buildAnnotatedText(text, starts)
                    }
                }
                val annotated = annotatedState
                // 每章页表缓存(内存,宽高/样式变化时整表作废);磁盘缓存按章键控
                val chapterPages = remember(textWidth, textHeight, readerStyle) { mutableStateMapOf<Int, List<Int>>() }
                // 目录跳转/切章后的待滚页(章内序号);-1 = 无,Int.MAX_VALUE = 章末
                var pendingPage by remember { mutableIntStateOf(-1) }
                // 后台补算:当前章 + 前后各 2 章页表(门禁 + 边界翻页无缝)。
                // 预计算窗口必须覆盖切章目标的后一哨兵章,否则布局渐进变化会让翻页器页码错位/冻住。
                // key 含 chapterStarts:检测完成(null→[]/实际列表)后 chapters 值可能不变(无章节兜底一章),
                // 仅依赖 chapters 会漏重启,页表永不生成导致门禁永久空白
                LaunchedEffect(curChapter, textWidth, textHeight, chapters, chapterStarts, annotated) {
                    if (chapterStarts == null || chapters.isEmpty()) return@LaunchedEffect
                    val annotatedText = annotated ?: return@LaunchedEffect
                    val need = listOf(curChapter - 2, curChapter - 1, curChapter, curChapter + 1, curChapter + 2)
                        .filter { it in chapters.indices && chapterPages[it] == null }
                    for (idx in need) {
                        val fp = sourceFp
                        val cached = fp?.let {
                            withContext(Dispatchers.IO) {
                                ReaderCache.loadPages(context, bookId, it, idx, textWidth, textHeight, ReaderStyleKey)
                            }
                        }
                        // 缓存自校验:首边界按当前度量重测(字体度量可能变化),不一致则作废重算
                        val valid = cached != null && cached.size >= 2 &&
                            cached.last() <= chapters[idx].end &&
                            withContext(Dispatchers.Default) {
                                val bgMeasurer = TextMeasurer(fontFamilyResolver, density, LayoutDirection.Ltr)
                                nextPageEnd(
                                    annotatedText, cached[0], bgMeasurer, readerStyle,
                                    textWidth, textHeight, linesPerPage, linesPerPage * 120, chapters[idx].end,
                                ) == cached[1]
                            }
                        val pages = if (valid) {
                            cached!!
                        } else {
                            val computed = withContext(Dispatchers.Default) {
                                val bgMeasurer = TextMeasurer(fontFamilyResolver, density, LayoutDirection.Ltr)
                                chapterPagesOf(annotatedText, chapters[idx], bgMeasurer, readerStyle, textWidth, textHeight, linesPerPage)
                            }
                            if (fp != null) {
                                saveScope.launch {
                                    ReaderCache.savePages(context, bookId, fp, idx, textWidth, textHeight, ReaderStyleKey, computed)
                                }
                            }
                            computed
                        }
                        chapterPages[idx] = pages
                    }
                }

                // 目录跳转:切章 + 定位章首(每章页表毫秒级,远跳瞬时)
                LaunchedEffect(pendingJump) {
                    if (pendingJump >= 0 && pendingJump in chapters.indices) {
                        curChapter = pendingJump
                        pendingPage = 0
                        pendingJump = -1
                    }
                }

                // 门禁:只等当前章页表;前后哨兵页固定占位(布局稳定,不会重现冻住),相邻章后台补完自动填真内容。
                // 外层 Box 强制居中(BoxWithConstraints 默认左上对齐,Crossfade 过渡期内容定位稳定);
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Crossfade(
                    targetState = chapterStarts == null || annotated == null || chapterPages[curChapter] == null,
                    animationSpec = tween(AnimationTokens.Large),
                    label = "readerGate",
                ) { loading ->
                    // Crossfade 过渡期会同时组合两分支:目标章页表未就绪时(跳转淡出阶段)也走加载分支,防 `!!` 空指针
                    val curPages = chapterPages[curChapter]
                    val curAnnotated = annotated
                    if (loading || curPages == null || curAnnotated == null) {
                        Unit // 加载期间不显示任何内容,就绪后淡入
                    } else {
                    // 目录跳转后正文淡入:key(jumpSeq) 每次跳转重建,Animatable 0→1;翻页跨章序号不变不触发,初始打开(序号 0)也不触发
                    key(jumpSeq) {
                        val fade = remember { Animatable(if (jumpSeq == 0) 1f else 0f) }
                        LaunchedEffect(Unit) {
                            if (fade.value < 1f) fade.animateTo(1f, tween(AnimationTokens.Large))
                        }
                    // 按章重建翻页器:切章/跳转后从目标页全新创建,旧页码不残留,杜绝哨兵误判与卡顿
                    key(curChapter) {
                        val ch = curChapter // 本组合的章节:effect 只认自己组合时的章,防跨章组合错位
                        val hasPrev = ch > 0
                        val hasNext = ch < chapters.lastIndex
                        val prevPages = if (hasPrev) chapterPages[ch - 1] else null
                        val nextPages = if (hasNext) chapterPages[ch + 1] else null
                        val baseIndex = if (hasPrev) 1 else 0
                        val realPages = curPages.size - 1 // 末项为章末哨兵
                        val pageCount = realPages + (if (hasPrev) 1 else 0) + (if (hasNext) 1 else 0)
                        val chapterStart = chapters[ch].start

                        // 初始页:目录跳转/切章用待滚页,否则恢复章内位置
                        val initialPage = remember(curPages, pendingPage) {
                            val pageInChapter = if (pendingPage >= 0) {
                                if (pendingPage == Int.MAX_VALUE) realPages - 1 else pendingPage.coerceIn(0, realPages - 1)
                            } else {
                                curPages.indexOfLast { it - chapterStart <= book.chapterPosition }
                                    .coerceIn(0, realPages - 1)
                            }
                            baseIndex + pageInChapter
                        }
                        val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

                        // 页 → (章号, 章内页序号) 稳定键;哨兵页占位时用独立键
                        fun keyOf(pagerIndex: Int): String = when {
                            hasPrev && pagerIndex == 0 ->
                                if (prevPages != null) "c${ch - 1}-${prevPages.size - 2}" else "ph-prev"
                            hasNext && pagerIndex == pageCount - 1 ->
                                if (nextPages != null) "c${ch + 1}-0" else "ph-next"
                            else -> "c$ch-${pagerIndex - baseIndex}"
                        }

                        // 哨兵页内容:前哨 = 前一章末页,后哨 = 下一章首页;相邻章未就绪时为占位(null → 空白页)
                        fun pageRangeOf(pagerIndex: Int): Pair<Int, Int>? = when {
                            hasPrev && pagerIndex == 0 ->
                                prevPages?.let { it[it.size - 2] to it[it.size - 1] }
                            hasNext && pagerIndex == pageCount - 1 ->
                                nextPages?.let { it[0] to it[1] }
                            else -> {
                                val p = pagerIndex - baseIndex
                                curPages[p] to curPages[p + 1]
                            }
                        }

                        // 哨兵页切章(仅手动翻页,等滑动停止 + 相邻章就绪;由 key 重建翻页器定位,无缝不打断动画)。
                        // effect 与自身组合的章节(ch)绑定:条件由本组合推导,且 curChapter 已被其他路径
                        // (目录跳转/翻页切章)改掉时立即停手——旧组合在淡出期间仍存活,不绑定的读法会把
                        // 自己的 page 0 误判成新章的上一章末页,曾致 curChapter 越界 -1 / 跳章被削成 N-1
                        LaunchedEffect(curChapter, chapterPages[ch - 1], chapterPages[ch + 1]) {
                            var switched = false
                            val ownPrev = if (ch > 0) chapterPages[ch - 1] else null
                            val ownNext = if (ch < chapters.lastIndex) chapterPages[ch + 1] else null
                            if (ownPrev == null && ownNext == null) return@LaunchedEffect
                            val ownPages = chapterPages[ch] ?: return@LaunchedEffect
                            val ownCount = ownPages.size - 1 + (if (ownPrev != null) 1 else 0) + (if (ownNext != null) 1 else 0)
                            snapshotFlow {
                                Triple(
                                    pagerState.currentPage,
                                    pagerState.isScrollInProgress,
                                    pagerState.currentPageOffsetFraction,
                                )
                            }.collect { (page, scrolling, offset) ->
                                if (switched || scrolling) return@collect
                                if (curChapter != ch) return@collect
                                // 等翻页动画完全落定(offset≈0)再切章:松手时 isScrollInProgress 已为 false,
                                // 但页面回弹的补间动画还在跑,此时切章重建翻页器会把动画掐断,
                                // 表现为翻到一半直接跳到新章节第一页(高强度滑动的"卡住"假象)
                                if (kotlin.math.abs(offset) > 0.001f) return@collect
                                val back = ownPrev != null && page == 0
                                val forward = ownNext != null && page == ownCount - 1
                                android.util.Log.d(
                                    "FolioPos",
                                    "edge ch=$ch cur=$curChapter page=$page count=$ownCount back=$back fwd=$forward",
                                )
                                if (back || forward) {
                                    switched = true
                                    pendingPage = if (back) Int.MAX_VALUE else 0 // 目标:章末 / 章首
                                    curChapter = if (back) ch - 1 else ch + 1
                                }
                            }
                        }

                        // 程序化滚动标记(朗读自动翻页/跨章定位):拖拽检测排除它,
                        // 否则朗读自动翻页的滚动会被误判为用户滑动而暂停朗读
                        var ttsScrolling by remember { mutableStateOf(false) }

                        // 跳转/切章定位(如跳到当前章):等目标章页表就绪,滚动到待滚页;只处理本组合的章
                        LaunchedEffect(pendingPage, chapterPages[ch]) {
                            if (curChapter != ch) return@LaunchedEffect
                            if (pendingPage < 0) return@LaunchedEffect
                            val pages = chapterPages[ch] ?: return@LaunchedEffect
                            val realLast = pages.size - 2
                            val target = if (pendingPage == Int.MAX_VALUE) realLast else pendingPage.coerceIn(0, realLast)
                            ttsScrolling = true
                            try {
                                pagerState.scrollToPage(baseIndex + target)
                            } finally {
                                ttsScrolling = false
                            }
                            pendingPage = -1
                        }

                        // 用户开始拖拽翻页:立即同步停止朗读(用户拍板:优先解决闪跳,蓝字消失时机无所谓)。
                        // 必须同步调用(不走 intent):异步有延迟,延迟期间朗读推进让播放页高亮跳动=闪跳
                        val currentTtsActive by rememberUpdatedState(ttsActive)
                        val currentCh by rememberUpdatedState(ch)
                        val currentPos by rememberUpdatedState(currentPosition)
                        LaunchedEffect(Unit) {
                            snapshotFlow { pagerState.isScrollInProgress to ttsScrolling }.collect { (scrolling, ttsScroll) ->
                                if (scrolling && !ttsScroll && currentTtsActive) {
                                    android.util.Log.d("FolioPos", "dragStart stop tts")
                                    userLeftTts = true
                                    ttsService?.stopReadingAt(currentCh, currentPos.second)
                                }
                            }
                        }

                        // 朗读自动翻页:高亮段落在当前章内时,滚动到其所在页(跟随朗读)
                        LaunchedEffect(ttsHighlight, curPages, ch) {
                            val hl = ttsHighlight ?: return@LaunchedEffect
                            if (curChapter != ch) return@LaunchedEffect
                            // 用户手动离开朗读页后不再拉回(否则翻页被吞、朗读被迫继续)
                            if (userLeftTts) return@LaunchedEffect
                            // 滚动进行中(用户滑动/动画)不插队:否则用户滑到别的页的瞬间,
                            // 朗读推进触发的自动翻页会把滑动动画掐断,出现一帧跳跃
                            if (pagerState.isScrollInProgress) return@LaunchedEffect
                            // 高亮段必须在当前章范围内才翻页(续章前旧高亮不触发)
                            if (hl.first < chapterStart || hl.first >= curPages.last()) return@LaunchedEffect
                            val pageInChapter = curPages.indexOfLast { it <= hl.first }
                                .coerceIn(0, realPages - 1)
                            val target = baseIndex + pageInChapter
                            android.util.Log.d(
                                "FolioPos",
                                "autoScroll ch=$ch target=$target cur=${pagerState.currentPage} hl=${hl.first}",
                            )
                            if (target != pagerState.currentPage) {
                                // 瞬间跳转(用户实测平滑滚动效果不理想,已回滚;定位准确、无动画干扰)
                                ttsScrolling = true
                                try {
                                    pagerState.scrollToPage(target)
                                } finally {
                                    ttsScrolling = false
                                }
                            }
                        }

                        // 朗读服务跨章:UI 跟随切章并定位到朗读当前段所在页(朗读在播,
                        // 不能停在旧章末页;定位到高亮页避免误判为用户滑页而停止朗读)。
                        // 用户切章(翻页跨章/目录跳章)会伴随停止(readingChapter 归 -1)后组合重建,
                        // rc<0 直接 return,不会误拉回朗读章
                        LaunchedEffect(ttsReadingChapter, chapterPages[ttsReadingChapter.coerceAtLeast(0)]) {
                            val rc = ttsReadingChapter
                            android.util.Log.d("FolioPos", "follow rc=$rc cur=$curChapter active=$ttsActive")
                            if (rc < 0 || rc !in chapters.indices) return@LaunchedEffect
                            // 只在朗读章「变化」时跟随:用户跳章/组合重建时 rc 未变,不误拉回
                            if (rc == prevReadingChapter) return@LaunchedEffect
                            prevReadingChapter = rc
                            if (rc == curChapter) return@LaunchedEffect // 朗读启动(rc==cur):记录即可,不跟随
                            // 不加 ttsActive 守卫:服务切章瞬间 active 短暂抖动/状态转发有延迟,
                            // 守卫会把跟随挡掉导致 UI 停在旧章
                            jumpSeq++ // 触发正文淡入(与目录跳章一致),跨章不是瞬间跳变
                            curChapter = rc
                            val pages = chapterPages[rc]
                            val hl = ttsHighlight
                            pendingPage = if (pages != null && hl != null &&
                                hl.first >= chapters[rc].start && hl.first < pages.last()
                            ) {
                                pages.indexOfLast { it <= hl.first }.coerceIn(0, pages.size - 2)
                            } else {
                                0
                            }
                        }

                        // 同步当前阅读位置(立即,供顶栏返回保存);只处理本组合的章
                        // key 含 chapterPages[ch]:跳章后目标章页表就绪时也要重新同步,
                        // 否则 currentPosition 停在旧章,恢复朗读会从旧章读
                        LaunchedEffect(pagerState.currentPage, chapterPages[ch], ch) {
                            if (curChapter != ch) return@LaunchedEffect
                            val pages = chapterPages[ch] ?: return@LaunchedEffect
                            val pageInPager = pagerState.currentPage
                            // 哨兵页(前/后导 = 相邻章边缘占位):用户滑到这里=离开当前章,
                            // 停止朗读(否则朗读仍在播,边界切章会与跨章跟随打架,页面被拉回朗读章)
                            if (pageInPager < baseIndex || pageInPager >= baseIndex + realPages) {
                                if (ttsActive) {
                                    android.util.Log.d(
                                        "FolioPos",
                                        "sentinel ch=$ch page=$pageInPager base=$baseIndex real=$realPages",
                                    )
                                    userLeftTts = true
                                    if (hasPrev && pageInPager == 0) {
                                        val prev = chapterPages[ch - 1]
                                        if (prev != null) {
                                            ttsService?.stopReadingAt(
                                                ch - 1,
                                                (prev[prev.size - 2] - chapters[ch - 1].start).coerceAtLeast(0),
                                            )
                                        } else {
                                            ttsService?.stopReadingAt(ch, 0)
                                        }
                                    } else {
                                        ttsService?.stopReadingAt(ch, 0)
                                    }
                                }
                                return@LaunchedEffect
                            }
                            // 切章瞬间页码还是旧章的,不在本章翻页器范围内:跳过,等滚动落定再同步
                            // (曾用 coerceIn 硬夹,切章瞬间会算出错误章内位置污染 currentPosition,
                            // 恢复朗读时从错误末尾读、立刻跳章)
                            val abs = curPages[pageInPager - baseIndex]
                            currentPosition = ch to (abs - chapters[ch].start)
                            // 用户移动阅读位置(翻页/跳章)时,朗读停止:避免「看 A 页、听 B 页」的脱节。
                            // 朗读自动滚动落点=高亮段所在页,当前页含高亮段起点时视为朗读引起,不停。
                            val hl = ttsHighlight
                            val pageEnd = curPages.getOrElse(pageInPager - baseIndex + 1) { chapters[ch].end }
                            val highlightOnPage = hl != null && hl.first >= abs && hl.first < pageEnd
                            if (!highlightOnPage && ttsActive) {
                                // 滑页/跳章:标记用户已离开并同步停朗读(不走 intent,避免异步延迟
                                // 期间朗读自动翻页把页面拉回);恢复播放从当前页读(不重复已看内容)
                                android.util.Log.d(
                                    "FolioPos",
                                    "userLeft ch=$ch page=$pageInPager hl=$hl abs=$abs",
                                )
                                userLeftTts = true
                                ttsService?.stopReadingAt(ch, abs - chapters[ch].start)
                            }
                        }
                        // 翻页/切章后延迟保存位置,避免快速连翻频繁写库;只处理本组合的章
                        LaunchedEffect(pagerState.currentPage, chapterPages[ch], ch) {
                            delay(600)
                            if (curChapter != ch) return@LaunchedEffect
                            val pages = chapterPages[ch] ?: return@LaunchedEffect
                            // 与位置同步一致:页码不在本章范围内(切章瞬间)不保存,防止写错进度
                            val pageInPager = pagerState.currentPage
                            if (pageInPager < baseIndex || pageInPager >= baseIndex + realPages) return@LaunchedEffect
                            val abs = curPages[pageInPager - baseIndex]
                            savePosition(repo, saveScope, book, ch, abs - chapters[ch].start)
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = fade.value },
                            // 按翻页手势设置:滑动模式启用手势滑动;点击模式禁用(点击走 animateScrollToPage
                            // 程序化滚动,自等区间就绪,不触发手势 overscroll)
                            userScrollEnabled = pageTurnMode == PageTurnMode.SWIPE,
                            key = { keyOf(it) },
                        ) { page ->
                            val range = pageRangeOf(page)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(ReaderHPadding, ReaderVPadding),
                            ) {
                                // 占位哨兵页(相邻章加载中):空白,很快被真实内容填上
                                if (range != null) {
                                    Text(
                                        text = withHighlight(curAnnotated, range, ttsHighlight, darkTheme),
                                        style = readerStyle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                        // 点击翻页(仅点击模式):屏幕左右各 50%,左侧=上一页,右侧=下一页;
                        // 程序化 animateScrollToPage 翻页;滑动模式下不挂此层,避免与滑动手势竞争
                        if (pageTurnMode == PageTurnMode.CLICK) {
                            val tapScope = rememberCoroutineScope()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures { offset ->
                                            tapScope.launch {
                                                val next = offset.x >= size.width / 2f
                                                val target = pagerState.currentPage + if (next) 1 else -1
                                                pagerState.animateScrollToPage(
                                                    target.coerceIn(0, pageCount - 1),
                                                )
                                            }
                                        }
                                    },
                            )
                        }
                    }
                    }
                    }
                    }
                }
            }
        }

        // 目录覆盖层:全屏盖在阅读页上(含顶栏),阅读页组合保持存活;
        // 点击章节跳转或返回关闭。渲染顺序在 Scaffold 之后,天然叠在最上层;
        // 开合动画与页面切换同款轻推(350ms,1/16 屏宽),观感一致
        AnimatedVisibility(
            visible = showToc,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(AnimationTokens.XL)) +
                slideInHorizontally(tween(AnimationTokens.XL)) { it / 16 },
            exit = fadeOut(tween(AnimationTokens.XL)) +
                slideOutHorizontally(tween(AnimationTokens.XL)) { -it / 16 },
        ) {
            TocOverlay(
                titles = chapters.map { it.title },
                currentChapter = curChapter,
                onSelect = { index ->
                    pendingJump = index
                    jumpSeq++
                    showToc = false
                },
                onDismiss = { showToc = false },
            )
        }
    }
}

private fun savePosition(
    repo: BookRepository,
    scope: CoroutineScope,
    book: Book,
    chapterIndex: Int,
    chapterPos: Int,
) {
    scope.launch { repo.updatePosition(book.id, chapterIndex, chapterPos) }
}

/** 构造分页/渲染共用的正文:章节标题行加粗(照搬 Legado 默认 textBold=0 的标题加粗),其余与正文一致 */
internal fun buildAnnotatedText(text: String, chapterStarts: List<Int>): AnnotatedString {
    if (chapterStarts.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for (s in chapterStarts) {
            if (s >= text.length) continue
            val e = text.indexOf('\n', s).let { if (it == -1) text.length else it }
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), s, e)
        }
    }
}

/**
 * 渲染期叠加朗读高亮:页面文本是整本正文的 [range.first, range.second) 切片,
 * 朗读高亮 [highlight] 是整本正文的绝对范围;两者有交集时,在切片上按
 * 「切片内相对偏移」把当前朗读段文字染成主题色(primary)。高亮只作用于
 * 渲染层,不进分页测量用的 annotated(保证分页边界不受朗读状态影响)。
 */
@Composable
private fun withHighlight(
    annotated: AnnotatedString,
    range: Pair<Int, Int>,
    highlight: Pair<Int, Int>?,
    darkTheme: Boolean,
): AnnotatedString {
    if (highlight == null) return annotated.subSequence(range.first, range.second)
    val pageStart = range.first
    val pageEnd = range.second
    val hs = highlight.first.coerceIn(pageStart, pageEnd)
    val he = highlight.second.coerceIn(pageStart, pageEnd)
    if (hs >= he) return annotated.subSequence(pageStart, pageEnd)
    // 朗读高亮色:HCT 用种子色的互补色相(hue+180,黄→蓝紫),与暖黄正文形成强对比,
    // chroma 中等、两主题 tone 适中,明显且不刺眼
    val hlColor = remember(darkTheme) {
        val seed = Hct.fromInt(FolioSeedColor.toArgb())
        Color(Hct.from((seed.hue + 180.0) % 360.0, 50.0, if (darkTheme) 50.0 else 55.0).toInt())
    }
    return buildAnnotatedString {
        append(annotated.subSequence(pageStart, pageEnd))
        addStyle(
            SpanStyle(color = hlColor),
            hs - pageStart,
            he - pageStart,
        )
    }
}

/** 计算从 start 开始下一页的边界(字符下标),供单章分页逐页推进;limit 钳制不越过章末 */
internal fun nextPageEnd(
    text: AnnotatedString,
    start: Int,
    measurer: TextMeasurer,
    style: TextStyle,
    maxWidth: Int,
    maxHeight: Int,
    linesPerPage: Int,
    sliceCap: Int,
    limit: Int,
): Int {
    val sliceEnd = min(min(start + sliceCap, limit), text.length)
    val layout = measurer.measure(
        text = text.subSequence(start, sliceEnd),
        style = style,
        constraints = Constraints(maxWidth = maxWidth, maxHeight = maxHeight),
        maxLines = linesPerPage,
    )
    // sliceEnd 已是绝对下标;getLineEnd 返回切片内相对下标,需单独加 start 统一为绝对下标
    var end = if (layout.lineCount < linesPerPage) {
        sliceEnd
    } else {
        start + layout.getLineEnd(layout.lineCount - 1, visibleEnd = true).coerceAtLeast(1)
    }
    // 跳过行首换行,避免每页开头空一行
    if (end < text.length && text[end] == '\n') end++
    return end.coerceAtMost(limit)
}
