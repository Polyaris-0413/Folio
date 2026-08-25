package com.folio.read

/*
 * Tab 内容切换 FadeThrough 动画移植自 Book's Story(https://github.com/Acclorite/book-story)
 * SPDX-License-Identifier: GPL-3.0-only
 */

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import com.folio.read.R
import com.folio.read.data.AiConfig
import com.folio.read.data.AiSettingsRepository
import com.folio.read.data.Book
import com.folio.read.data.BookRepository
import com.folio.read.data.BookTitleCleaner
import com.folio.read.data.LibraryRepository
import com.folio.read.data.SettingsRepository
import com.folio.read.data.PageTurnMode
import com.folio.read.data.PageTurnSettings
import com.folio.read.data.TitleCleanSettings
import com.folio.read.data.TitleCleanSettingsRepository
import com.folio.read.data.UpdateCheckResult
import com.folio.read.data.UpdateChecker
import com.folio.read.data.UpdateSettingsRepository
import com.folio.read.data.compareVersions
import com.folio.read.data.PageTurnSettingsRepository
import com.folio.read.data.ShelfLayout
import com.folio.read.data.ShelfLayoutMode
import com.folio.read.data.ShelfSyncSettings
import com.folio.read.data.ShelfSyncSettingsRepository
import com.folio.read.data.ShelfSettingsRepository
import com.folio.read.ui.components.AppNavBar
import com.folio.read.ui.components.FolioAlertDialog
import com.folio.read.ui.components.FolioTopBar
import com.folio.read.ui.components.UpdateDialog
import com.folio.read.ui.components.menuShape
import com.folio.read.ui.library.LibraryAddScreen
import com.folio.read.ui.licenses.LicensesScreen
import com.folio.read.ui.navigation.AppRoutes
import com.folio.read.ui.navigation.AppSections
import com.folio.read.ui.reader.ReaderScreen
import com.folio.read.ui.reader.ReaderHPadding
import com.folio.read.ui.reader.ReaderVPadding
import com.folio.read.ui.reader.preWarmBook
import com.folio.read.ui.screens.ShelfScreen
import com.folio.read.ui.settings.SettingsScreen
import com.folio.read.ui.settings.ThemeItemExpandState
import com.folio.read.ui.theme.AnimationTokens
import com.folio.read.ui.theme.FolioSeedColor
import com.folio.read.ui.theme.FolioTheme
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeNeutral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 进入书架页自动同步的节流窗口:避免书架↔设置高频切换时重复扫描
private const val AUTO_SYNC_THROTTLE_MS = 10_000L

class MainActivity : ComponentActivity() {

    // TTS 通知/媒体栏深链:携带书 id 打开阅读页;Compose 侧观察此状态导航(可观测,onNewIntent 后触发重组)
    var deepLinkBookId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 冷启动被通知拉起:onCreate 里就拿到书 id,导航到阅读页
        deepLinkBookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L).takeIf { it > 0 }
        setContent {
            AppRoot(
                deepLinkBookId = deepLinkBookId,
                onDeepLinkConsumed = { deepLinkBookId = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkBookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L).takeIf { it > 0 }
    }

    companion object {
        /** TTS 通知/媒体栏打开阅读页用的书 id extra(与 ReaderTtsService 共用) */
        const val EXTRA_BOOK_ID = "book_id"
    }
}

@Composable
private fun AppRoot(
    deepLinkBookId: Long?,
    onDeepLinkConsumed: () -> Unit,
) {
    // 主题状态持久化(DataStore):启动时恢复,变更时写入
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context.applicationContext) }
    var followSystemTheme by remember { mutableStateOf(true) }
    var manualDark by remember { mutableStateOf(false) }
    var selectedSection by rememberSaveable { mutableStateOf(AppSections.Shelf) }

    // 单 Activity 导航:全局唯一 NavController,提升到主题 Crossfade 之外(主题过渡期间新旧副本共用同一控制器)
    val navController = rememberNavController()

    val darkTheme = if (followSystemTheme) systemDark else manualDark

    // 系统主题项的展开状态提升到主题 Crossfade 之外:
    // 主题切换会重建页面副本,状态在外层持有才不会被重建、动画不会被打断
    val appScope = rememberCoroutineScope()
    val themeExpandState = remember {
        ThemeItemExpandState(
            initialVisible = !followSystemTheme,
            onFollowSystemThemeChange = { newValue ->
                if (!newValue) {
                    // 关闭跟随系统时,深浅默认选中系统当前主题对应项
                    manualDark = systemDark
                    appScope.launch { settingsRepo.setManualDark(systemDark) }
                }
                followSystemTheme = newValue
                appScope.launch { settingsRepo.setFollowSystemTheme(newValue) }
            },
            animationScope = appScope,
        )
    }

    // 启动时从持久化恢复主题设置,并同步展开状态
    LaunchedEffect(Unit) {
        val restoredFollow = settingsRepo.followSystemTheme.first()
        val restoredDark = settingsRepo.manualDark.first()
        followSystemTheme = restoredFollow
        manualDark = restoredDark
        themeExpandState.restore(expanded = !restoredFollow)
    }

    // 书架数据
    val bookRepo = remember { BookRepository(context.applicationContext) }
    // null=查询中(启动首帧不显示空态占位符,有书时避免闪几帧占位);empty=确实无书
    val books by bookRepo.books.collectAsState(initial = null)

    // 书架后台预读:书架按 lastReadAt 置顶,顶书=最可能继续读的,提前算好缓存 → 点开秒开。
    // 只填空不重算(与阅读页并发写由缓存校验兜底);尺寸按阅读页测量口径
    // (Scaffold 内容区 = 窗口 - 顶栏 64dp - 状态栏 - 导航栏,再减正文留白)。
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val windowSize = LocalWindowInfo.current.containerSize
    val statusBar = WindowInsets.statusBars.getTop(density)
    val navBar = WindowInsets.navigationBars.getBottom(density)
    val topBar = with(density) { 64.dp.toPx() }
    // 组合作用域内构造测量器工厂(拿内部字体解析器,后台分页用独立 TextMeasurer 与 UI 测量隔离)
    val measurerFactory: () -> TextMeasurer = {
        TextMeasurer(fontFamilyResolver, density, LayoutDirection.Ltr)
    }
    var preWarmedBookId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(books, fontFamilyResolver, windowSize) {
        val top = books?.firstOrNull() ?: return@LaunchedEffect
        if (top.id == preWarmedBookId) return@LaunchedEffect
        preWarmedBookId = top.id
        val textWidth = (windowSize.width - with(density) { ReaderHPadding.toPx() * 2 }).toInt()
        val textHeight = (windowSize.height - statusBar - topBar - navBar - with(density) { ReaderVPadding.toPx() * 2 }).toInt()
        appScope.launch {
            preWarmBook(context, top, measurerFactory, density, textWidth, textHeight)
        }
    }

    // 阅读页返回后置顶:点开书那一刻不改书架(避免点击瞬间列表跳动割裂),
    // 从阅读页返回书架时才刷新最近阅读时间,用户看到"刚读的书移到最前"
    var scrollToTopSignal by remember { mutableIntStateOf(0) }
    // 自动同步新增书后的滚顶:用动画滚动(新书平滑露出,位移动画自然播放);
    // 与读完书返回的即时滚顶分开,避免深滚位置时滑太久
    var scrollToTopAnimatedSignal by remember { mutableIntStateOf(0) }
    // 首次开启自动同步时的说明弹窗
    var showShelfSyncHint by remember { mutableStateOf(false) }
    // 阅读页覆盖层:在 main 目的地内渲染,main 保持存活 → 退出不重组(修复退出掉帧);
    // 覆盖层打开时 main 仍是当前目的地,返回键由 ReaderScreen 的 BackHandler 接管
    var readerBookId by remember { mutableStateOf<Long?>(null) }
    // 退出动画期间内容需保持组合:捕获最后非空 id 供覆盖层内容使用
    var lastReaderBookId by remember { mutableStateOf<Long?>(null) }
    // 许可页/添加页覆盖层:与阅读页同模式,main 保持存活,退出不重组
    var showLicenses by remember { mutableStateOf(false) }
    var showLibraryAdd by remember { mutableStateOf(false) }
    // 阅读页退出回调:书 id 由状态带入,退出即置顶 + 书架滚回顶部
    fun onReaderClose(bookId: Long) {
        appScope.launch { bookRepo.markRead(bookId) }
        scrollToTopSignal++
    }

    // TTS 通知/媒体栏深链:携带书 id 时打开阅读页覆盖层
    LaunchedEffect(deepLinkBookId) {
        deepLinkBookId?.let { id ->
            readerBookId = id
            onDeepLinkConsumed()
        }
    }

    // 冷启动自动检查更新:有新版本且未被「关闭」忽略 → 直接弹对话框
    val updateChecker = remember { UpdateChecker() }
    val updateSettingsRepo = remember { UpdateSettingsRepository(context.applicationContext) }
    var pendingUpdate by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val result = updateChecker.checkLatest()
        if (result is UpdateCheckResult.Latest) {
            val ignored = updateSettingsRepo.ignoredVersion.first()
            val current = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "0"
            // 版本更新于当前 且 不是被关闭过的版本(更新版仍会提示)
            if (compareVersions(result.release.version, current) > 0 && result.release.version != ignored) {
                pendingUpdate = result.release.version
            }
        }
    }

    // 书架选择模式:长按进入,顶栏显示删除;返回键退出
    var selectedBookIds by remember { mutableStateOf(emptySet<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    // 打开失败(源文件被外部删除/不可读):不进阅读页,书架层直接提示;记录失败的书供对话框操作
    var openFailedBook by remember { mutableStateOf<Book?>(null) }

    // 书库目录(扫描来源)
    val libraryRepo = remember { LibraryRepository(context.applicationContext) }
    val libraryDir by libraryRepo.libraryDir.collectAsState(initial = null)

    // 自动同步书架:开关开且已选书架目录 → 启动时扫描并加入(去重自动跳过,净化同手动添加)
    val shelfSyncRepo = remember { ShelfSyncSettingsRepository(context.applicationContext) }
    val shelfSync by shelfSyncRepo.shelfSync.collectAsState(initial = ShelfSyncSettings())

    // 书库目录名(设置页显示用)
    var libraryDirName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(libraryDir) {
        val dir = libraryDir
        // 修复历史坏 filePath(tree 来源的书此前存成不可读的 document 形式)
        bookRepo.repairReadablePaths(dir?.let { Uri.parse(it) })
        libraryDirName = if (dir == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(context, Uri.parse(dir))?.name
            }
        }
    }

    // 书架排版设置
    val shelfSettingsRepo = remember { ShelfSettingsRepository(context.applicationContext) }
    val shelfLayout by shelfSettingsRepo.shelfLayout.collectAsState(
        initial = ShelfLayout(ShelfLayoutMode.ONE),
    )

    // 阅读页翻页手势设置
    val pageTurnRepo = remember { PageTurnSettingsRepository(context.applicationContext) }
    val pageTurn by pageTurnRepo.pageTurn.collectAsState(
        initial = PageTurnSettings(PageTurnMode.SWIPE),
    )

    // AI 配置(API Key/地址/模型)
    val aiRepo = remember { AiSettingsRepository(context.applicationContext) }
    val aiConfig by aiRepo.config.collectAsState(initial = AiConfig())

    // 书名净化开关
    val titleCleanRepo = remember { TitleCleanSettingsRepository(context.applicationContext) }
    val titleCleanSettings by titleCleanRepo.titleClean.collectAsState(initial = TitleCleanSettings())
    val titleClean = titleCleanSettings.enabled

    // AI 书名净化:开关开启且配置齐全时启用,否则走本地解析
    val titleCleaner = remember(titleClean, aiConfig) {
        if (titleClean && aiConfig.isConfigured) BookTitleCleaner(aiConfig) else null
    }

    // 自动同步书架:扫描书架目录,把未加入的书加进来(去重自动跳过,净化同手动添加)。
    // 默认读 DataStore 判断开关/目录;调用方在「刚写入配置」的场景传 force 参数跳过读取,
    // 避免 DataStore 异步写入未完成时读到旧值(null/关)误判跳过
    fun runShelfSync(forceEnabled: Boolean? = null, forceDir: String? = null) {
        appScope.launch {
            val enabled = forceEnabled ?: shelfSyncRepo.shelfSync.first().enabled
            val dir = forceDir ?: libraryRepo.libraryDir.first()
            if (enabled && dir != null) {
                // 已移除的书(手动删除过)自动同步不再加回;手动添加会清除该记录
                // 传 dir 给 scanLibrary:forceDir 场景(选目录后立即同步)不依赖 DataStore 异步写入
                var added = 0
                libraryRepo.scanLibrary(dir).forEach { (uri, _) ->
                    if (bookRepo.isRemoved(uri)) return@forEach
                    val book = bookRepo.addBook(uri)
                    if (book != null) {
                        added++
                        if (titleCleaner != null) {
                            bookRepo.aiCleanBook(book, titleCleaner)
                        }
                    }
                }
                // 新增书按 lastReadAt 排到书架最前:滚回顶部让用户直接看到,不用手动上拉
                // (与「读完书返回书架滚顶」同一机制;books flow 更新后书架侧兜底再滚一次)
                if (added > 0) scrollToTopAnimatedSignal++
            }
        }
    }
    // 进入书架页时自动同步(不再只启动时扫一次):从设置/阅读页/许可页回来,目录里的新书实时出现。
    // 触发 = 书架从不可见变为可见(无覆盖层 + 书架 tab 选中);节流 10s,避免书架↔设置高频切换重复扫。
    val shelfVisible = selectedSection == AppSections.Shelf &&
        readerBookId == null && !showLicenses && !showLibraryAdd
    var wasShelfVisible by remember { mutableStateOf(false) }
    var lastAutoSyncAt by remember { mutableStateOf(0L) }
    LaunchedEffect(shelfVisible) {
        if (shelfVisible && !wasShelfVisible) {
            val now = SystemClock.uptimeMillis()
            if (now - lastAutoSyncAt >= AUTO_SYNC_THROTTLE_MS) {
                lastAutoSyncAt = now
                runShelfSync()
            }
        }
        wasShelfVisible = shelfVisible
    }

    // 添加书籍:SAF 文件选择器;重复文件(唯一索引拦截)提示用户
    val addBookLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            appScope.launch {
                val book = bookRepo.addBook(uri)
                if (book == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.shelf_add_duplicate),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    // 持久化读权限,重启后仍可打开该文件
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    // 书名净化后台跑,不阻塞导入
                    if (titleCleaner != null) {
                        bookRepo.aiCleanBook(book, titleCleaner)
                    }
                }
            }
        }
    }

    // 打开书库添加页(扫描勾选入架):覆盖层,main 保持存活
    fun openLibraryAdd() {
        showLibraryAdd = true
    }

    // 添加书库目录:SAF 目录选择器,选择后持久化;自动同步开启时选完立即扫描
    // (用刚选的目录传入 scanLibrary,不依赖 DataStore 异步写入;开关关着只登记不扫)
    val addLibraryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            appScope.launch {
                libraryRepo.setLibraryDir(uri)
            }
            runShelfSync(forceDir = uri.toString())
        }
    }

    // 系统栏图标明暗跟随实际生效主题 + 窗口背景随主题(防深色打开页面时白闪)。
    // 单 Activity 后窗口全局唯一,这里一处搞定(原 5 个 Activity 各自重复的副本随迁移删除)
    val activity = LocalContext.current as? Activity
    val windowScheme = remember(darkTheme) {
        SchemeNeutral(Hct.fromInt(FolioSeedColor.toArgb()), darkTheme, contrastLevel = 0.0)
    }
    SideEffect {
        activity?.window?.let { window ->
            window.decorView.setBackgroundColor(windowScheme.background.toInt())
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // 主题切换整体渐变:深浅任一变化时 Crossfade 交叉淡化整棵界面树,
    // 顶栏/背景等所有元素同步过渡(新副本以新主题初始化,M3 组件不会二次动画)
    Crossfade(
        targetState = darkTheme,
        animationSpec = tween(AnimationTokens.Large),
        label = "themeTransition",
    ) { theme ->
        FolioTheme(darkTheme = theme) {
            NavHost(
                navController = navController,
                startDestination = AppRoutes.MAIN,
                // 页面跳转轻推(350ms,1/16 屏宽)移植自 Book's Story(其页面切换统一用该过渡):
                // 前进=新页从右滑入+旧页向左滑出,返回=反向;解决了原「Tab 有动画、页面走系统过渡」的割裂
                enterTransition = {
                    fadeIn(tween(AnimationTokens.XL)) +
                        slideInHorizontally(tween(AnimationTokens.XL)) { it / 16 }
                },
                exitTransition = {
                    fadeOut(tween(AnimationTokens.XL)) +
                        slideOutHorizontally(tween(AnimationTokens.XL)) { -it / 16 }
                },
                popEnterTransition = {
                    fadeIn(tween(AnimationTokens.XL)) +
                        slideInHorizontally(tween(AnimationTokens.XL)) { -it / 16 }
                },
                popExitTransition = {
                    fadeOut(tween(AnimationTokens.XL)) +
                        slideOutHorizontally(tween(AnimationTokens.XL)) { it / 16 }
                },
            ) {
                composable(AppRoutes.MAIN) {
                    // 阅读页覆盖层需要盖住顶栏/底栏,main 目的地根节点包 Box
                    Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(                modifier = Modifier.fillMaxSize(),
                // 各页面自带 TopAppBar 负责状态栏内边距,外层 Scaffold 不再叠加顶部 inset
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                // 共享顶栏(背景固定,避免透出底色变暗):标题动画在 FolioTopBar 内做
                // (新标题淡入,旧标题淡出),顶栏背景不参与过渡
                topBar = {
                    FolioTopBar(
                        titleRes = selectedSection.labelRes,
                        actions = {
                            // 顶栏操作:仅书架页显示——未选中=overflow 菜单,选中=重命名/删除;
                            // 其他页面=空白(切页淡出隐藏,不留 overflow)。
                            // 固定宽度(两图标)让过渡期容器尺寸不变,旧图标不因内容叠放漂移
                            val topBarActionState = when {
                                selectedSection != AppSections.Shelf -> 0
                                selectedBookIds.isNotEmpty() -> 1
                                else -> 2
                            }
                            Crossfade(
                                targetState = topBarActionState,
                                // 与顶栏标题/内容区切换同档(Large):切页时标题与操作按钮同节奏淡出
                                animationSpec = tween(AnimationTokens.Large),
                                modifier = Modifier.width(96.dp),
                            ) { state ->
                                // 外层占满 Box:在 BoxScope 里用通用 align 靠右(Crossfade 内容
                                // 的 AnimatedContentScope.align 与外层 RowScope.align 重名冲突)
                                Box(modifier = Modifier.fillMaxSize()) {
                                when (state) {
                                    0 -> Unit // 非书架页:无操作按钮(旧内容淡出)
                                    1 -> {
                                        // Crossfade 内容不在 RowScope,两图标须包 Row 才并排
                                        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                                            // 重命名仅单选时可用(无批量重命名场景),淡入淡出与顶栏同档
                                            AnimatedVisibility(
                                                visible = selectedBookIds.size == 1,
                                                enter = fadeIn(animationSpec = tween(AnimationTokens.Large)),
                                                exit = fadeOut(animationSpec = tween(AnimationTokens.Large)),
                                            ) {
                                                IconButton(onClick = { showRenameDialog = true }) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_shelf_rename),
                                                        contentDescription = stringResource(R.string.shelf_rename),
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { showDeleteConfirm = true }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_shelf_delete),
                                                    contentDescription = stringResource(R.string.shelf_delete),
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                                            var shelfMenuExpanded by remember { mutableStateOf(false) }
                                            IconButton(onClick = { shelfMenuExpanded = true }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_more_vert),
                                                    contentDescription = stringResource(R.string.shelf_more),
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = shelfMenuExpanded,
                                            onDismissRequest = { shelfMenuExpanded = false },
                                            // 容器色用 M3 默认 surfaceContainer,圆角 = M3 菜单档(small 8dp)
                                            shape = menuShape,
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(R.string.shelf_add_book)) },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_folder),
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    shelfMenuExpanded = false
                                                    addBookLauncher.launch(arrayOf("*/*"))
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stringResource(
                                                            if (libraryDir == null) {
                                                                R.string.shelf_add_library
                                                            } else {
                                                                R.string.shelf_add_from_library
                                                            },
                                                        ),
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_shelves),
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    shelfMenuExpanded = false
                                                    if (libraryDir == null) {
                                                        addLibraryLauncher.launch(null)
                                                    } else {
                                                        openLibraryAdd()
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                                }
                            }
                            }
                        },
                    )
                },
                bottomBar = {
                    AppNavBar(
                        selectedSection = selectedSection,
                        onSectionSelected = { selectedSection = it },
                    )
                },
            ) { innerPadding ->
                val contentModifier = Modifier.padding(innerPadding)

                // 同级 tab 内容切换:FadeThrough 风格(淡入 + 0.975 微缩放),移植自 Book's Story;
                // 时长仍用全局 Large 档(动画数值统一原则),不照搬其 250ms
                // 注:曾尝试三页常驻组合+透明度切换,实测掉帧更差(隐藏重页面每帧绘制开销大于
                // 切换时组合一次),已回滚;掉帧集中在切换到设置页的首帧组合。
                AnimatedContent(
                    targetState = selectedSection,
                    transitionSpec = {
                        (fadeIn(tween(AnimationTokens.Large)) +
                            scaleIn(tween(AnimationTokens.Large), initialScale = 0.975f))
                            .togetherWith(fadeOut(tween(AnimationTokens.Large)))
                    },
                    label = "sectionContent",
                ) { section ->
                    when (section) {
                        AppSections.Shelf -> ShelfScreen(
                            books = books,
                            shelfLayout = shelfLayout,
                            selectedBookIds = selectedBookIds,
                            scrollToTopSignal = scrollToTopSignal,
                            scrollToTopAnimatedSignal = scrollToTopAnimatedSignal,
                            onToggleSelect = { book ->
                                selectedBookIds =
                                    if (book.id in selectedBookIds) selectedBookIds - book.id
                                    else selectedBookIds + book.id
                            },
                            onAddBook = { addBookLauncher.launch(arrayOf("*/*")) },
                            onOpenBook = { book ->
                                // 先查源文件可读性:被外部删除/不可读时书架层弹框,不进阅读页
                                appScope.launch {
                                    if (bookRepo.isReadable(book)) {
                                        // 打开阅读页覆盖层;快速连点置同一本书是幂等 no-op(无栈可叠)
                                        readerBookId = book.id
                                    } else {
                                        openFailedBook = book
                                    }
                                }
                            },
                            modifier = contentModifier,
                        )
                        AppSections.Settings -> SettingsScreen(
                            expandState = themeExpandState,
                            followSystemTheme = followSystemTheme,
                            manualDark = manualDark,
                            onManualDarkChange = { newValue ->
                                manualDark = newValue
                                appScope.launch { settingsRepo.setManualDark(newValue) }
                            },
                            onOpenLicenses = { showLicenses = true },
                            libraryDirName = libraryDirName,
                            onSelectLibrary = { addLibraryLauncher.launch(null) },
                            shelfSync = shelfSync.enabled,
                            onShelfSyncChange = { enabled ->
                                appScope.launch { shelfSyncRepo.setEnabled(enabled) }
                                // 首次开启时说明「移除的书不会自动加回」(只提示一次)
                                if (enabled) {
                                    appScope.launch {
                                        if (!shelfSyncRepo.hasShownRemovalHint()) {
                                            showShelfSyncHint = true
                                            shelfSyncRepo.markRemovalHintShown()
                                        }
                                    }
                                }
                                // 开启时立即同步一次(关闭只停后续,不清理已有书);force 跳过 DataStore 未写完的旧值
                                if (enabled) runShelfSync(forceEnabled = true)
                            },
                            shelfLayout = shelfLayout,
                            onShelfLayoutChange = { layout ->
                                appScope.launch { shelfSettingsRepo.setShelfLayout(layout) }
                            },
                            pageTurn = pageTurn,
                            onPageTurnChange = { turn ->
                                appScope.launch { pageTurnRepo.setPageTurnMode(turn.mode) }
                            },
                            aiConfig = aiConfig,
                            onAiConfigChange = { config ->
                                appScope.launch { aiRepo.save(config) }
                            },
                            titleClean = titleClean,
                            onTitleCleanChange = { enabled ->
                                appScope.launch { titleCleanRepo.setEnabled(enabled) }
                            },
                            modifier = contentModifier,
                        )
                    }
                }
            }

            // 选择模式下返回键退出选择,而非退出应用
            BackHandler(enabled = selectedBookIds.isNotEmpty()) {
                selectedBookIds = emptySet()
            }

            // 冷启动检测到新版本:下载(浏览器)/关闭(记住该版本,本次不再提醒;更更新的版本仍会提示)
            pendingUpdate?.let { version ->
                UpdateDialog(
                    version = version,
                    onDownload = {
                        pendingUpdate = null
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Polyaris-0413/Folio/releases/latest")),
                        )
                    },
                    onDismiss = {
                        pendingUpdate = null
                        appScope.launch { updateSettingsRepo.setIgnoredVersion(version) }
                        Toast.makeText(context, context.getString(R.string.update_dismissed), Toast.LENGTH_SHORT).show()
                    },
                )
            }

            // 移除确认:仅从书架移除记录,不删除源文件
            if (showDeleteConfirm) {
                FolioAlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text(text = stringResource(R.string.shelf_delete_confirm_title)) },
                    text = {
                        Text(
                            text = if (selectedBookIds.size == 1) {
                                stringResource(R.string.shelf_delete_confirm_message_single)
                            } else {
                                stringResource(R.string.shelf_delete_confirm_message_plural, selectedBookIds.size)
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                // 先快照再清空:launch 异步执行,直接读 state 会读到清空后的空集
                                val ids = selectedBookIds
                                selectedBookIds = emptySet()
                                appScope.launch { ids.forEach { bookRepo.delete(it) } }
                            },
                        ) {
                            Text(text = stringResource(R.string.shelf_delete_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    },
                )
            }

            // 打开失败提示:源文件被外部删除/不可读,书架层直接弹框(不进阅读页);
            // 提供「移除」一步清掉坏记录(文件都没了,进度无意义,无需二次确认)
            val failedBook = openFailedBook
            if (failedBook != null) {
                FolioAlertDialog(
                    onDismissRequest = { openFailedBook = null },
                    title = { Text(text = stringResource(R.string.book_open_failed_title)) },
                    text = { Text(text = stringResource(R.string.book_open_failed_message)) },
                    confirmButton = {
                        TextButton(onClick = { openFailedBook = null }) {
                            Text(text = stringResource(R.string.book_open_failed_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                openFailedBook = null
                                appScope.launch { bookRepo.delete(failedBook.id) }
                            },
                        ) {
                            Text(text = stringResource(R.string.shelf_delete_confirm))
                        }
                    },
                )
            }

            // 重命名(单选时可用):输入框预填当前书名,确定后更新并退出选择模式
            if (showRenameDialog) {
                val renaming = books?.firstOrNull { it.id == selectedBookIds.firstOrNull() }
                if (renaming != null) {
                    var renameText by remember(renaming.id) { mutableStateOf(renaming.title) }
                    FolioAlertDialog(
                        onDismissRequest = { showRenameDialog = false },
                        title = { Text(text = stringResource(R.string.shelf_rename_dialog_title)) },
                        text = {
                            // 浮动 label 在真机上首帧 ~68ms(浮起动画+测量),改普通 Text 标签放字段上方
                            Column {
                                Text(
                                    text = stringResource(R.string.shelf_rename_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = renameText,
                                    onValueChange = { renameText = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = renameText.isNotBlank(),
                                onClick = {
                                    showRenameDialog = false
                                    val newTitle = renameText.trim()
                                    selectedBookIds = emptySet()
                                    appScope.launch { bookRepo.rename(renaming.id, newTitle) }
                                },
                            ) {
                                Text(text = stringResource(R.string.confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRenameDialog = false }) {
                                Text(text = stringResource(R.string.cancel))
                            }
                        },
                    )
                }
            }

            // 首次开启自动同步说明:移除的书不会自动加回,手动添加才重新加入
            if (showShelfSyncHint) {
                FolioAlertDialog(
                    onDismissRequest = { showShelfSyncHint = false },
                    title = { Text(text = stringResource(R.string.shelf_sync_removal_hint_title)) },
                    text = { Text(text = stringResource(R.string.shelf_sync_removal_hint_message)) },
                    confirmButton = {
                        TextButton(onClick = { showShelfSyncHint = false }) {
                            Text(text = stringResource(R.string.shelf_sync_removal_hint_ok))
                        }
                    },
                )
            }
            // 阅读页覆盖层:最后渲染叠在最上;main 保持存活,退出不重组(与目录覆盖层同模式)
            // 进入=从右滑入(前进),退出=向右滑出(弹回书架方向,与 NavHost popExit 一致)
            if (readerBookId != null) lastReaderBookId = readerBookId
            AnimatedVisibility(
                visible = readerBookId != null,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(AnimationTokens.XL)) +
                    slideInHorizontally(tween(AnimationTokens.XL)) { it / 16 },
                exit = fadeOut(tween(AnimationTokens.XL)) +
                    slideOutHorizontally(tween(AnimationTokens.XL)) { it / 16 },
            ) {
                lastReaderBookId?.let { id ->
                    ReaderScreen(
                        bookId = id,
                        darkTheme = theme,
                        onClose = {
                            onReaderClose(id)
                            readerBookId = null
                        },
                    )
                }
            }
            // 许可页覆盖层:与阅读页同模式,main 保持存活,退出不重组
            AnimatedVisibility(
                visible = showLicenses,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(AnimationTokens.XL)) +
                    slideInHorizontally(tween(AnimationTokens.XL)) { it / 16 },
                exit = fadeOut(tween(AnimationTokens.XL)) +
                    slideOutHorizontally(tween(AnimationTokens.XL)) { it / 16 },
            ) {
                LicensesScreen(onBack = { showLicenses = false })
            }
            // 添加页覆盖层
            AnimatedVisibility(
                visible = showLibraryAdd,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(AnimationTokens.XL)) +
                    slideInHorizontally(tween(AnimationTokens.XL)) { it / 16 },
                exit = fadeOut(tween(AnimationTokens.XL)) +
                    slideOutHorizontally(tween(AnimationTokens.XL)) { it / 16 },
            ) {
                LibraryAddScreen(onBack = { showLibraryAdd = false })
            }
            }
            }
        }
        }
    }
}
