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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
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
import com.folio.read.data.BookTitleParser
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
import com.folio.read.ui.components.rememberBelowTooltipPositionProvider
import com.folio.read.ui.components.FolioAlertDialog
import com.folio.read.ui.components.FolioTopBar
import com.folio.read.ui.components.UpdateDialog
import com.folio.read.ui.components.groupItemShape
import com.folio.read.ui.components.groupItemSpacing
import com.folio.read.ui.components.listItemColors
import com.folio.read.ui.library.LibraryAddScreen
import com.folio.read.ui.licenses.LicensesScreen
import com.folio.read.ui.navigation.AppRoutes
import com.folio.read.ui.navigation.AppSections
import com.folio.read.ui.reader.ReaderScreen
import com.folio.read.ui.reader.ReaderHPadding
import com.folio.read.ui.reader.ReaderVPadding
import com.folio.read.ui.reader.preWarmBook
import com.folio.read.ui.screens.ShelfScreen
import com.folio.read.ui.settings.AboutScreen
import com.folio.read.ui.settings.SettingsScreen
import com.folio.read.ui.settings.ThemeItemExpandState
import com.folio.read.ui.theme.AnimationTokens
import com.folio.read.ui.theme.FolioSeedColor
import com.folio.read.ui.theme.FolioTheme
import com.folio.read.ui.theme.dynamicColorScheme
import com.folio.read.ui.theme.dynamicSchemeCache
import com.folio.read.ui.theme.dynamicSeedArgb
import com.folio.read.ui.theme.warmDynamicSchemes
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var dynamicColor by remember { mutableStateOf(false) }
    // tab 状态不走 by 委托:AppRoot 函数体不读 .value,切 tab 时重组只发生在读取它的小组件
    // (MainBottomBar/SectionContent/ShelfAutoSyncEffect/TabBackHandlers)内,800 行的 AppRoot
    // 不再整体重启(2026-09-01 掉帧排查:切 tab 触发 AppRoot 级联重组占切换帧大头之一)
    val selectedSectionState = rememberSaveable { mutableStateOf(AppSections.Shelf) }

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

    // 冷启动后台预热动态取色配色(壁纸提取 + 配色生成在 IO 线程),切动态取色时直接命中不卡顿
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { warmDynamicSchemes(context) }
    }

    // 启动时从持久化恢复主题设置,并同步展开状态
    LaunchedEffect(Unit) {
        val restoredFollow = settingsRepo.followSystemTheme.first()
        val restoredDark = settingsRepo.manualDark.first()
        val restoredDynamic = settingsRepo.dynamicColor.first()
        followSystemTheme = restoredFollow
        manualDark = restoredDark
        dynamicColor = restoredDynamic
        themeExpandState.restore(expanded = !restoredFollow)
    }

    // 书架数据
    val bookRepo = remember { BookRepository(context.applicationContext) }
    // null=查询中(启动首帧不显示空态占位符,有书时避免闪几帧占位);empty=确实无书
    val books by bookRepo.books.collectAsState(initial = null)
    // 书名净化兜底:存量书名仍是文件名(带 .txt/.epub/.azw3 后缀)的,按文件名重新解析——
    // 历史数据/某路径下书名存成了原始文件名,新增走 addBook 的 BookTitleParser.parse 会净化,存量需补;
    // 只净化带文件扩展名的明显文件名,不碰用户手动重命名的正常书名(2026-08-26 用户反馈「自带书名过滤未生效」)
    var shelfNameCleaned by remember { mutableStateOf(false) }
    LaunchedEffect(books) {
        if (shelfNameCleaned) return@LaunchedEffect
        val list = books ?: return@LaunchedEffect
        shelfNameCleaned = true
        list.filter {
            val t = it.title
            // 文件名特征:文件后缀 或 资源站/下载源标记(z-library/1lib 等)
            t.endsWith(".txt", true) || t.endsWith(".epub", true) || t.endsWith(".azw3", true) ||
                t.endsWith(".mobi", true) || t.contains("z-lib", true) || t.contains("1lib", true) ||
                t.contains("librs", true) || t.contains("libgen", true)
        }.forEach { b ->
            val cleaned = BookTitleParser.parse(b.title)
                .ifBlank { b.title.substringBeforeLast('.').ifBlank { b.title } }
            if (cleaned != b.title) appScope.launch { bookRepo.rename(b.id, cleaned) }
        }
    }

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
    // 书架内联搜索:书架顶栏搜索图标切换(展开=下滑出搜索框并过滤网格);关书回书架后
    // 状态保留,方便继续找下一本;收起时清空关键词(避免下次展开带着旧过滤结果误以为书丢了)
    // 搜索状态保留 by 委托(AppRoot 内大量读写);同时暴露 State 对象给 SectionContent/TabBackHandlers
    val searchState = remember { mutableStateOf(false) }
    var showSearch by searchState
    val searchQueryState = remember { mutableStateOf("") }
    var searchQuery by searchQueryState
    // 收起搜索框时顺带收键盘(覆盖层/展开切换不会自动收)
    val keyboard = LocalSoftwareKeyboardController.current
    // 退出动画期间内容需保持组合:捕获最后非空 id 供覆盖层内容使用
    var lastReaderBookId by remember { mutableStateOf<Long?>(null) }
    // 许可页/设置页/关于页覆盖层:与阅读页同模式,main 保持存活,退出不重组
    var showLicenses by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
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
                libraryRepo.scanLibrary(dir).forEach { file ->
                    if (bookRepo.isRemoved(file.uri)) return@forEach
                    val book = bookRepo.addBook(file.uri)
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
    // 触发 = 阅读 tab 从不可见变为可见(无覆盖层 + 阅读 tab 选中);节流 10s,避免 tab 高频切换重复扫。
    // 状态读取隔离进 ShelfAutoSyncEffect:tab 切换不触发 AppRoot 重组
    ShelfAutoSyncEffect(
        sectionState = selectedSectionState,
        readerBookId = readerBookId,
        showLicenses = showLicenses,
        showSettings = showSettings,
        showAbout = showAbout,
        onSync = { runShelfSync() },
    )

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

    // 顶栏 overflow(关于/设置)菜单已移至文件级 GlobalOverflowMenu(参数化回调),
    // 供 SectionContent 内两个 TAB 的顶栏共用

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
    // 窗口背景跟随目标配色:动态取色时用系统壁纸配色(与 FolioTheme 同一 API),否则用琥珀种子色。
    // 曾用 animateColorAsState 平滑过渡,实测主题切换时每帧重组 + 系统栏 Binder 调用造成 100ms+ 掉帧
    val targetWindowBg = if (dynamicColor) {
        (dynamicSchemeCache[darkTheme] ?: dynamicColorScheme(dynamicSeedArgb, darkTheme)).background
    } else {
        Color(remember(darkTheme) { SchemeNeutral(Hct.fromInt(FolioSeedColor.toArgb()), darkTheme, contrastLevel = 0.0) }.background)
    }
    val windowBg = remember(darkTheme, dynamicColor) { targetWindowBg }
    SideEffect {
        activity?.window?.let { window ->
            window.decorView.setBackgroundColor(windowBg.toArgb())
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // 主题切换整体渐变:深浅切换走 Crossfade 交叉淡化整棵界面树(新副本以新主题初始化,M3 组件不会二次动画)。
    // 动态取色开关不触发整树过渡——配色由 FolioTheme 原地更新(树保持组合,Switch 等控件动画不被重建打断),
    // 配色瞬间切换无中间帧,避免开关瞬间新树未就绪露底的黑闪
    Crossfade(
        targetState = darkTheme,
        animationSpec = tween(AnimationTokens.Large),
        label = "themeTransition",
    ) { theme ->
        FolioTheme(darkTheme = theme, dynamicColor = dynamicColor) {
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
                    Scaffold(modifier = Modifier.fillMaxSize(),
                // 各页面自带 TopAppBar 负责状态栏内边距,外层 Scaffold 不再叠加顶部 inset
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    MainBottomBar(sectionState = selectedSectionState)
                },
            ) { innerPadding ->
                val contentModifier = Modifier.padding(innerPadding)

                // 同级 tab 内容切换:FadeThrough 风格(淡入 + 0.975 微缩放),移植自 Book's Story;
                // 时长仍用全局 Large 档(动画数值统一原则),不照搬其 250ms。
                // 整块读取隔离进 SectionContent(单一动画层+顶栏并入内容子树):tab 切换只重启
                // 该组件与 MainBottomBar 等,AppRoot 不再整体重组(掉帧排查结论,见组件注释)
                SectionContent(
                    sectionState = selectedSectionState,
                    contentPadding = innerPadding,
                    books = books,
                    shelfLayout = shelfLayout,
                    selectedBookIds = selectedBookIds,
                    searchActive = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchToggle = { expand ->
                        if (expand) {
                            showSearch = true
                        } else {
                            // 收起时清空关键词(避免下次展开带着旧过滤结果误以为书丢了)+收键盘
                            showSearch = false
                            searchQuery = ""
                            keyboard?.hide()
                        }
                    },
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
                                // 搜索中开书=已找到目标:与收起同逻辑(收框+清词+收键盘),
                                // 回到书架即完整书架(此前保留搜索状态的方案被此交互取代)
                                if (showSearch) {
                                    showSearch = false
                                    searchQuery = ""
                                    keyboard?.hide()
                                }
                                // 打开阅读页覆盖层;快速连点置同一本书是幂等 no-op(无栈可叠)
                                readerBookId = book.id
                            } else {
                                openFailedBook = book
                            }
                        }
                    },
                    libraryDir = libraryDir,
                    onSelectLibrary = { addLibraryLauncher.launch(null) },
                    onShowRenameDialog = { showRenameDialog = true },
                    onShowDeleteConfirm = { showDeleteConfirm = true },
                    onAbout = { showAbout = true },
                    onSettings = { showSettings = true },
                )
            }

            // tab 相关返回键(搜索收起/书架切回阅读):状态读取隔离进 TabBackHandlers,
            // tab 切换不触发 AppRoot 重组。声明在选择模式之前,选择优先退出(组合顺序语义不变)
            TabBackHandlers(
                sectionState = selectedSectionState,
                searchActive = showSearch,
                onCollapseSearch = {
                    showSearch = false
                    searchQuery = ""
                    keyboard?.hide()
                },
            )

            // 选择模式下返回键退出选择,而非退出应用
            BackHandler(enabled = selectedBookIds.isNotEmpty()) {
                selectedBookIds = emptySet()
            }

            // 设置/关于覆盖层返回:关闭覆盖层(与阅读页覆盖层同语义)
            BackHandler(enabled = showSettings) { showSettings = false }
            BackHandler(enabled = showAbout) { showAbout = false }

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
            // 注:曾禁用动画排查「进入阅读页顶栏黑帧」,实测与覆盖层动画无关(根因=阅读页内加载淡入过渡,已修)
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
                        dynamicColor = dynamicColor,
                        onClose = {
                            onReaderClose(id)
                            readerBookId = null
                            // 返回回搜索页:搜索页驻留(进书是覆盖),只关阅读页,动画只动阅读一层
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
            // 设置页覆盖层:与阅读页同模式,main 保持存活,退出不重组
            AnimatedVisibility(
                visible = showSettings,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(AnimationTokens.XL)) +
                    slideInHorizontally(tween(AnimationTokens.XL)) { it / 16 },
                exit = fadeOut(tween(AnimationTokens.XL)) +
                    slideOutHorizontally(tween(AnimationTokens.XL)) { it / 16 },
            ) {
                SettingsScreen(
                    onBack = { showSettings = false },
                    expandState = themeExpandState,
                    followSystemTheme = followSystemTheme,
                    manualDark = manualDark,
                    onManualDarkChange = { newValue ->
                        manualDark = newValue
                        appScope.launch { settingsRepo.setManualDark(newValue) }
                    },
                    dynamicColor = dynamicColor,
                    onDynamicColorChange = { newValue ->
                        dynamicColor = newValue
                        appScope.launch { settingsRepo.setDynamicColor(newValue) }
                    },
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
                )
            }
            // 关于页覆盖层:设置页「关于」分组拆出后独立,与阅读页同模式
            AnimatedVisibility(
                visible = showAbout,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(AnimationTokens.XL)) +
                    slideInHorizontally(tween(AnimationTokens.XL)) { it / 16 },
                exit = fadeOut(tween(AnimationTokens.XL)) +
                    slideOutHorizontally(tween(AnimationTokens.XL)) { it / 16 },
            ) {
                AboutScreen(
                    onBack = { showAbout = false },
                    onOpenLicenses = { showLicenses = true },
                )
            }
            }
            }
        }
        }
    }
}

/**
 * 底部导航:tab 状态读取隔离在此(MainBottomBar 读 selectedSectionState.value),
 * 切 tab 时重组只发生在这里,不再触发 AppRoot 整体重启(2026-09-01 掉帧排查结论)
 */
@Composable
private fun MainBottomBar(sectionState: MutableState<AppSections>) {
    AppNavBar(
        selectedSection = sectionState.value,
        onSectionSelected = { sectionState.value = it },
    )
}

/**
 * tab 内容区:内容切换 FadeThrough(淡入 + 0.975 微缩放,移植自 Book's Story;时长用全局
 * Large 档,不照搬其 250ms)。顶栏在本组件内、动画子树外(静态背景层),仅内容区做 FadeThrough:
 * 顶栏若随内容淡入淡出,交叉帧像素必然介于顶栏色与底色之间(黑闪,静态背景救不了顶栏自身),
 * 且 scaleIn 起始帧顶栏未贴屏幕顶会露出与背景的色差缝。掉帧收益不依赖顶栏并入——2026-09-01
 * 掉帧排查(四套动画并行各贡献 15-30ms 致单帧 100ms+)的真正解是状态读取隔离:顶栏动画与本
 * 内容动画都在本隔离组件内,tab 切换只重启这里,AppRoot 不整体重组
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionContent(
    sectionState: MutableState<AppSections>,
    /** Scaffold innerPadding(含底栏高度):不应用则内容延伸到底栏下(全选/添加被盖、列表滚底被遮) */
    contentPadding: PaddingValues,
    books: List<Book>?,
    shelfLayout: ShelfLayout,
    selectedBookIds: Set<Long>,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    scrollToTopSignal: Int,
    scrollToTopAnimatedSignal: Int,
    onToggleSelect: (Book) -> Unit,
    onAddBook: () -> Unit,
    onOpenBook: (Book) -> Unit,
    libraryDir: String?,
    onSelectLibrary: () -> Unit,
    onShowRenameDialog: () -> Unit,
    onShowDeleteConfirm: () -> Unit,
    onAbout: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 静态背景兜底:旧页淡出时透出本底而非更深的 Scaffold 底色
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
    ) {
        FolioTopBar(
            titleRes = sectionState.value.labelRes,
            actions = {
                // 三态交叉淡化(原共享顶栏同款):书架 TAB=添加+overflow;阅读选择态=重命名/删除;
                // 阅读普通态=搜索+overflow。固定两图标宽度防过渡期容器漂移
                val topBarActionState = when {
                    sectionState.value == AppSections.Library -> 0
                    selectedBookIds.isNotEmpty() -> 1
                    else -> 2
                }
                Crossfade(
                    targetState = topBarActionState,
                    animationSpec = tween(AnimationTokens.Large),
                    modifier = Modifier.width(96.dp),
                    label = "topBarActions",
                ) { state ->
                    // Crossfade 内容不在 RowScope:占满 Box 后用通用 align 靠右
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (state) {
                            0 -> {
                                // 书架 TAB:手动添加(SAF 选文件)+ overflow(设置、关于)
                                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                                    TooltipBox(
                                        positionProvider = rememberBelowTooltipPositionProvider(),
                                        tooltip = { PlainTooltip { Text(stringResource(R.string.shelf_add_book)) } },
                                        state = rememberTooltipState(),
                                    ) {
                                        IconButton(onClick = onAddBook) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_folder),
                                                contentDescription = stringResource(R.string.shelf_add_book),
                                            )
                                        }
                                    }
                                    GlobalOverflowMenu(onAbout = onAbout, onSettings = onSettings)
                                }
                            }
                            1 -> {
                                // 选择态:重命名(仅单选时可用)+删除
                                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                                    AnimatedVisibility(
                                        visible = selectedBookIds.size == 1,
                                        enter = fadeIn(animationSpec = tween(AnimationTokens.Large)),
                                        exit = fadeOut(animationSpec = tween(AnimationTokens.Large)),
                                    ) {
                                        TooltipBox(
                                            positionProvider = rememberBelowTooltipPositionProvider(),
                                            tooltip = { PlainTooltip { Text(stringResource(R.string.shelf_rename)) } },
                                            state = rememberTooltipState(),
                                        ) {
                                            IconButton(onClick = onShowRenameDialog) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_shelf_rename),
                                                    contentDescription = stringResource(R.string.shelf_rename),
                                                )
                                            }
                                        }
                                    }
                                    TooltipBox(
                                        positionProvider = rememberBelowTooltipPositionProvider(),
                                        tooltip = { PlainTooltip { Text(stringResource(R.string.shelf_delete)) } },
                                        state = rememberTooltipState(),
                                    ) {
                                        IconButton(onClick = onShowDeleteConfirm) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_shelf_delete),
                                                contentDescription = stringResource(R.string.shelf_delete),
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                // 阅读普通态:搜索 + overflow(设置、关于)
                                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                                    TooltipBox(
                                        positionProvider = rememberBelowTooltipPositionProvider(),
                                        tooltip = { PlainTooltip { Text(stringResource(R.string.search)) } },
                                        state = rememberTooltipState(),
                                    ) {
                                        IconButton(onClick = { onSearchToggle(!searchActive) }) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_search),
                                                contentDescription = stringResource(R.string.search),
                                            )
                                        }
                                    }
                                    GlobalOverflowMenu(onAbout = onAbout, onSettings = onSettings)
                                }
                            }
                        }
                    }
                }
            },
        )
        AnimatedContent(
            targetState = sectionState.value,
            transitionSpec = {
                (fadeIn(tween(AnimationTokens.Large)) +
                    scaleIn(tween(AnimationTokens.Large), initialScale = 0.975f))
                    .togetherWith(fadeOut(tween(AnimationTokens.Large)))
            },
            modifier = Modifier.weight(1f),
            label = "sectionContent",
        ) { section ->
            when (section) {
                AppSections.Shelf -> ShelfScreen(
                    books = books,
                    shelfLayout = shelfLayout,
                    selectedBookIds = selectedBookIds,
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    scrollToTopSignal = scrollToTopSignal,
                    scrollToTopAnimatedSignal = scrollToTopAnimatedSignal,
                    onToggleSelect = onToggleSelect,
                    onAddBook = onAddBook,
                    onOpenBook = onOpenBook,
                    modifier = Modifier.fillMaxSize(),
                )
                AppSections.Library -> LibraryAddScreen(
                    libraryDir = libraryDir,
                    onSelectLibrary = onSelectLibrary,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * 顶栏 overflow 菜单(关于在上/设置在下,文本居中无图标):阅读与书架 TAB 顶栏共用。
 * 原 AppRoot 局部函数;移出后覆盖层开关经回调写回(showSettings/showAbout 仍由 AppRoot 持有)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalOverflowMenu(onAbout: () -> Unit, onSettings: () -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    TooltipBox(
        positionProvider = rememberBelowTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.shelf_more)) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = { showSheet = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.shelf_more),
            )
        }
    }
    if (showSheet) {
        // 跳过半展开:面板只保留完全展开/隐藏两个位置(与设置页各弹窗面板一致)
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sheetScope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                // 复用设置页条目样式(ListItem + 拼接卡),与 PageTurnSheet 等面板同款
                verticalArrangement = Arrangement.spacedBy(groupItemSpacing),
            ) {
                // 条目点击:等收起动画播完再移除面板并打开对应覆盖层(直接移除会跳过退出动画)
                fun closeThen(action: () -> Unit) {
                    sheetScope.launch { sheetState.hide() }.invokeOnCompletion {
                        showSheet = false
                        action()
                    }
                }
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.settings_group_about),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = listItemColors(),
                    modifier = Modifier
                        .clip(groupItemShape(0, 2))
                        .clickable { closeThen { onAbout() } },
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.nav_settings),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = listItemColors(),
                    modifier = Modifier
                        .clip(groupItemShape(1, 2))
                        .clickable { closeThen { onSettings() } },
                )
            }
        }
    }
}

/**
 * 进入书架页自动同步:阅读 tab 从不可见变为可见(无覆盖层 + 阅读 tab 选中)时触发,节流 10s。
 * 状态读取隔离在此:tab 切换只重启本组件,AppRoot 不整体重组(2026-09-01 掉帧排查)
 */
@Composable
private fun ShelfAutoSyncEffect(
    sectionState: MutableState<AppSections>,
    readerBookId: Long?,
    showLicenses: Boolean,
    showSettings: Boolean,
    showAbout: Boolean,
    onSync: () -> Unit,
) {
    val shelfVisible = sectionState.value == AppSections.Shelf &&
        readerBookId == null && !showLicenses && !showSettings && !showAbout
    var wasShelfVisible by remember { mutableStateOf(false) }
    var lastAutoSyncAt by remember { mutableStateOf(0L) }
    LaunchedEffect(shelfVisible) {
        if (shelfVisible && !wasShelfVisible) {
            val now = SystemClock.uptimeMillis()
            if (now - lastAutoSyncAt >= AUTO_SYNC_THROTTLE_MS) {
                lastAutoSyncAt = now
                onSync()
            }
        }
        wasShelfVisible = shelfVisible
    }
}

/**
 * tab 相关返回键:搜索展开时收起搜索(仅书架 tab 生效,设置页按返回仍切回书架);
 * 书架 TAB 返回切回阅读 tab(返回手势语义=回到上一界面,而非退出应用)。
 * 状态读取隔离在此:tab 切换只重启本组件,AppRoot 不整体重组(2026-09-01 掉帧排查)
 */
@Composable
private fun TabBackHandlers(
    sectionState: MutableState<AppSections>,
    searchActive: Boolean,
    onCollapseSearch: () -> Unit,
) {
    BackHandler(enabled = searchActive && sectionState.value == AppSections.Shelf) {
        onCollapseSearch()
    }
    BackHandler(enabled = sectionState.value == AppSections.Library) {
        sectionState.value = AppSections.Shelf
    }
}
