package com.folio.read.ui.library

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folio.read.R
import com.folio.read.data.AiConfig
import com.folio.read.data.AiSettingsRepository
import com.folio.read.data.Book
import com.folio.read.data.BookRepository
import com.folio.read.data.BookTitleCleaner
import com.folio.read.data.BookTitleParser
import com.folio.read.data.LibraryFile
import com.folio.read.data.LibraryRepository
import com.folio.read.data.TitleCleanSettings
import com.folio.read.data.TitleCleanSettingsRepository
import com.folio.read.ui.components.bookCoverGradient
import com.folio.read.ui.theme.AnimationTokens
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 条目展示模型:渐变/净化书名/格式化大小在扫描阶段(后台线程)预烘焙完成,
 * 列表组合时纯读取——迷你封面渐变(HCT 色彩计算)与书名净化在组合帧内逐项计算
 * 会拖慢切进书架页的首帧(实测 +~15ms),预烘焙后组合零计算
 */
internal data class FileRow(
    val file: LibraryFile,
    val uri: String,
    val format: String,
    val sizeLabel: String,
    val gradient: List<Color>,
    val cleanName: String,
)

/**
 * 书架候选清单的进程级缓存:tab 切换销毁页面组合,清单留在记忆里。
 * 再进页直接渲染旧清单(秒显,刷新不清空),后台重扫完成后整表原子替换
 * (book-story 同款机制;目录变更时作废)。rows 走 mutableStateOf 保证跨线程可见性。
 * 扫描协程挂自建常驻 scope(不随页面组合销毁):应用启动即可预热扫描,
 * 用户进页前后台扫描通常已完成——book-story 的 viewModelScope 同款行为
 */
internal object LibraryBrowserCache {
    var dir: String? = null
    var rows by mutableStateOf<List<FileRow>?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null
    private var lastScanAt = 0L

    // 进页触发与启动预热/上轮重扫的去重窗口(与 MainActivity 的 AUTO_SYNC_THROTTLE_MS 同思路)
    private const val RESCAN_THROTTLE_MS = 10_000L

    /**
     * 确保缓存与目录同步。
     * @param force 强制重扫(添加书后刷新候选);false 时若扫描已在进行则跳过(启动预热与进页触发的去重)
     */
    fun ensureScanned(repo: LibraryRepository, dir: String, force: Boolean = false) {
        if (this.dir != dir) {
            // 目录变更:旧目录缓存作废,重新走首次加载路径
            this.dir = dir
            rows = null
            lastScanAt = 0L
        }
        if (scanJob?.isActive == true) {
            if (!force) return
            scanJob?.cancel()
        }
        // 节流:缓存就绪且刚扫描过时,进页触发不再重复扫描——重扫的预烘焙计算会与
        // 进页首帧组合抢核加重卡顿(冷启动预热后立即进页实测大帧 141ms;book-story
        // 每次进页都重扫无感是因其 release 组合便宜,debug 下必须节流)
        if (!force && rows != null && SystemClock.uptimeMillis() - lastScanAt < RESCAN_THROTTLE_MS) return
        scanJob = scope.launch {
            val fresh = scanAndBake(repo, dir)
            lastScanAt = SystemClock.uptimeMillis()
            // 整表原子替换:FileRow 是 data class,内容未变的条目 equals 相同被 LazyColumn 跳过,
            // 无重组成本;替换发生在切进页面的组合帧之后,不在动画关键路径上
            rows = fresh
        }
    }
}

/** 扫描+预烘焙展示模型(后台线程):缓存刷新、首次加载与启动预热共用 */
private suspend fun scanAndBake(repo: LibraryRepository, dir: String): List<FileRow> = withContext(Dispatchers.IO) {
    // debug:进页掉帧定位(扫描耗时/候选数/就绪时机);Log.w 因部分 ROM(ColorOS)丢弃 debug 级日志
    val t0 = SystemClock.uptimeMillis()
    Log.w("FolioLibrary", "scan start at $t0")
    val result = repo.scanLibrary(dir)
    Log.w("FolioLibrary", "scan done n=${result.size} dt=${SystemClock.uptimeMillis() - t0}ms")
    // 展示模型后台预烘焙:渐变(HCT)/净化/大小格式化移出组合帧
    result.map { f ->
        val uri = f.uri.toString()
        val cleanName = BookTitleParser.parse(f.name)
            .ifBlank { f.name.substringBeforeLast('.').ifBlank { f.name } }
        FileRow(
            file = f,
            uri = uri,
            format = f.name.substringAfterLast('.').uppercase(),
            sizeLabel = formatFileSize(f.size),
            gradient = bookCoverGradient(uri),
            cleanName = cleanName,
        )
    }
}

/**
 * 书架页(底部 TAB):扫描已选书库目录下的文件,勾选后批量加入书架。
 * 未选目录时显示引导;顶栏由宿主(Scaffold)提供,本页只渲染列表与底部操作。
 */
@Composable
fun LibraryAddScreen(
    libraryDir: String?,
    onSelectLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val libraryRepo = remember { LibraryRepository(context.applicationContext) }
    val bookRepo = remember { BookRepository(context.applicationContext) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 添加成功或目录变更后重扫:新加入的书移出候选,列表始终是「未加入」的候选
    var scanKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // 书名净化:开关开启且配置齐全时启用;净化协程独立于组合生命周期,切走 TAB 后仍继续
    val aiRepo = remember { AiSettingsRepository(context.applicationContext) }
    val aiConfig by aiRepo.config.collectAsState(initial = AiConfig())
    val titleCleanRepo = remember { TitleCleanSettingsRepository(context.applicationContext) }
    val titleCleanSettings by titleCleanRepo.titleClean.collectAsState(initial = TitleCleanSettings())
    val titleClean = titleCleanSettings.enabled
    val cleaner = remember(titleClean, aiConfig) {
        if (titleClean && aiConfig.isConfigured) BookTitleCleaner(aiConfig) else null
    }
    val cleanScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    // 进页触发缓存同步(无缓存/换目录才扫;已在后台扫则跳过);添加书后 scanKey 强制重扫换新候选
    LaunchedEffect(libraryDir, scanKey) {
        if (libraryDir != null) {
            LibraryBrowserCache.ensureScanned(libraryRepo, libraryDir, force = scanKey > 0)
        }
    }

    // 进页分批组合:切进页面第一帧若一次组合可见区全部条目实测冻结 120-155ms(debug),
    // 先组合前 4 项、后台逐帧补齐,配合条目淡入呈现列表浮现动效
    var shownCount by remember { mutableIntStateOf(4) }
    LaunchedEffect(LibraryBrowserCache.rows) {
        val total = LibraryBrowserCache.rows?.size ?: return@LaunchedEffect
        while (shownCount < total) {
            shownCount = min(shownCount + 4, total)
            delay(16)
        }
    }
    val list = LibraryBrowserCache.rows?.take(shownCount)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (libraryDir == null) {
            // 未选目录:居中引导,点击选择书架目录
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.library_dir_empty_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.padding(16.dp))
                Button(onClick = onSelectLibrary) {
                    Text(text = stringResource(R.string.shelf_add_library))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // 列表常驻:LazyColumn 容器不随扫描状态整树切换(曾用 Crossfade 切三态,扫描完成时
                // 整树重建+淡入 → 实测 157ms 掉帧),数据到只增量插入 item,首帧成本摊薄
                LazyColumn(modifier = Modifier.weight(1f)) {
                    when {
                        list == null -> {} // 扫描中:空白
                        list.isEmpty() -> item {
                            Text(
                                text = stringResource(R.string.library_scan_empty),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                            )
                        }
                        else -> items(list, key = { it.uri }) { row ->
                            val isChecked = row.uri in selected
                            ListItem(
                                leadingContent = {
                                    // 迷你封面:与阅读 TAB 封面同源哈希渐变(种子=文件 URI,同一文件
                                    // 添加前后颜色一致),用户在此页看到的颜色即将来封面颜色
                                    Box(
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 60.dp)
                                            .background(
                                                brush = Brush.verticalGradient(row.gradient),
                                                shape = MaterialTheme.shapes.extraSmall,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = row.format,
                                            style = MaterialTheme.typography.labelSmall,
                                            // 渐变色深浅随哈希变化,固定白字与阅读 TAB 封面同款
                                            color = Color.White,
                                        )
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        text = row.cleanName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    // 格式已在迷你封面缩写中呈现,元数据行不再重复,只放大小
                                    Text(text = row.sizeLabel)
                                },
                                trailingContent = {
                                    // M3 原生 Checkbox 纯展示:点击落在整行(onCheckedChange=null
                                    // 不可交互),选中状态经 Checkbox 语义导出(TalkBack 可读)
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = null,
                                    )
                                },
                                // 分批插入的 item 出现时淡入(此前逐批闪现无动画,2026-08-26 用户反馈影响观感)
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = tween(AnimationTokens.Medium),
                                        fadeOutSpec = tween(AnimationTokens.Medium),
                                    )
                                    .clickable {
                                        selected = if (isChecked) selected - row.uri else selected + row.uri
                                    },
                            )
                        }
                    }
                }
                }
            }

            // 上下文操作条:勾选文件时浮出于 TAB 栏上方,无选中时完全隐藏(底部不与 TAB 拥挤)。
            // 条只在有选中时可见,"添加选中"恒可用,无需禁用态
            AnimatedVisibility(
                visible = list != null && selected.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                enter = fadeIn(tween(AnimationTokens.Medium)) +
                    slideInVertically(tween(AnimationTokens.Medium)) { it },
                exit = fadeOut(tween(AnimationTokens.Medium)) +
                    slideOutVertically(tween(AnimationTokens.Medium)) { it },
            ) {
                Surface(
                    // 浮动元素:最高档容器色与列表区分,large 圆角与 FAB 档阴影
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.library_selected_count, selected.size),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            selected = if (selected.size == list!!.size) {
                                emptySet()
                            } else {
                                list.map { it.uri }.toSet()
                            }
                        }) {
                            Text(text = stringResource(R.string.library_select_all))
                        }
                        Button(onClick = {
                            scope.launch {
                                var skipped = 0
                                val toClean = mutableListOf<Book>()
                                withContext(Dispatchers.IO) {
                                    selected.forEach { uri ->
                                        // 重复文件由唯一索引自动跳过
                                        val book = bookRepo.addBook(Uri.parse(uri))
                                        if (book == null) {
                                            skipped++
                                        } else if (cleaner != null) {
                                            toClean.add(book)
                                        }
                                    }
                                }
                                if (skipped > 0) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.library_add_skipped, skipped),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                // TAB 化:添加后不返回,清空选中并重扫,新加入的书从候选消失
                                selected = emptySet()
                                scanKey++
                                // 书名净化后台跑,不阻塞列表刷新
                                toClean.forEach { book ->
                                    cleanScope.launch {
                                        cleaner?.let { bookRepo.aiCleanBook(book, it) }
                                    }
                                }
                            }
                        }) {
                            Text(text = stringResource(R.string.library_add_selected))
                        }
                    }
                }
            }
    }
}

/** 字节数 → 人读大小(1024 进制,GB/MB 一位小数、KB 取整、字节原样);Locale.ROOT 固定小数点,不随系统语言变逗号 */
private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f".format(Locale.ROOT, bytes / (1024f * 1024f * 1024f)) + " GB"
    bytes >= 1L shl 20 -> "%.1f".format(Locale.ROOT, bytes / (1024f * 1024f)) + " MB"
    bytes >= 1L shl 10 -> "%.0f".format(Locale.ROOT, bytes / 1024f) + " KB"
    else -> "$bytes B"
}
