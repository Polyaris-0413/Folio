package com.folio.read.ui.library

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.folio.read.R
import com.folio.read.data.AiConfig
import com.folio.read.data.AiSettingsRepository
import com.folio.read.data.Book
import com.folio.read.data.BookRepository
import com.folio.read.data.BookTitleCleaner
import com.folio.read.data.LibraryRepository
import com.folio.read.data.TitleCleanSettings
import com.folio.read.data.TitleCleanSettingsRepository
import com.folio.read.ui.components.FolioTopBar
import com.folio.read.ui.theme.AnimationTokens
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 扫描结果候选:SAF document URI + 文件名 */
private data class FileCandidate(val uri: Uri, val name: String)

/**
 * 书库添加页(单 Activity 目的地):扫描登记目录下的 txt,勾选后批量加入书架。
 * 主题/系统栏由宿主统一管理,本页只关心内容与返回。
 */
@Composable
fun LibraryAddScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val libraryRepo = remember { LibraryRepository(context.applicationContext) }
    val bookRepo = remember { BookRepository(context.applicationContext) }
    var candidates by remember { mutableStateOf<List<FileCandidate>?>(null) } // null = 扫描中
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    // 手势返回与顶栏返回按钮同行为:关闭覆盖层回书架(单 Activity 覆盖层需自行接管返回键)
    BackHandler(enabled = true) { onBack() }

    // 书名净化:开关开启且配置齐全时启用;净化协程独立于组合生命周期,返回书架后仍继续
    val aiRepo = remember { AiSettingsRepository(context.applicationContext) }
    val aiConfig by aiRepo.config.collectAsState(initial = AiConfig())
    val titleCleanRepo = remember { TitleCleanSettingsRepository(context.applicationContext) }
    val titleCleanSettings by titleCleanRepo.titleClean.collectAsState(initial = TitleCleanSettings())
    val titleClean = titleCleanSettings.enabled
    val cleaner = remember(titleClean, aiConfig) {
        if (titleClean && aiConfig.isConfigured) BookTitleCleaner(aiConfig) else null
    }
    val cleanScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    // 扫描书架目录下的 txt(复用 LibraryRepository.scanLibrary;IO 线程,避免大目录卡 UI)
    LaunchedEffect(Unit) {
        // debug:进页掉帧定位(扫描耗时/候选数/就绪时机)
        val t0 = SystemClock.uptimeMillis()
        Log.d("FolioLibrary", "scan start at $t0")
        val result = withContext(Dispatchers.IO) {
            libraryRepo.scanLibrary().map { (uri, name) ->
                FileCandidate(uri, name.ifBlank { context.getString(R.string.unnamed) })
            }
        }
        Log.d("FolioLibrary", "scan done n=${result.size} dt=${SystemClock.uptimeMillis() - t0}ms")
        // 分批展示:一次性组合全部候选会冻结主线程一帧(实测 10 项 ~124ms),
        // 分批插入每帧只组合 2-3 项,列表渐进出现无冻结(曾试遮罩掩盖,淡出动画帧反而更卡,弃)
        val step = 3
        var shown = 0
        while (shown < result.size) {
            shown = min(shown + step, result.size)
            candidates = result.take(shown)
            if (shown < result.size) delay(16) // 16=60Hz 一帧,让出整帧再插下一批,避免同帧连续组合
        }
    }

    val list = candidates
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FolioTopBar(titleRes = R.string.library_add_title, onBack = onBack)

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
                    else -> items(list) { candidate ->
                        val uri = candidate.uri.toString()
                        val isChecked = uri in selected
                        ListItem(
                            headlineContent = { Text(text = candidate.name) },
                            trailingContent = {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + uri else selected - uri
                                    },
                                )
                            },
                            modifier = Modifier.clickable {
                                selected = if (isChecked) selected - uri else selected + uri
                            },
                        )
                    }
                }
            }
            if (list != null && list.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        selected = if (selected.size == list.size) {
                            emptySet()
                        } else {
                            list.map { it.uri.toString() }.toSet()
                        }
                    }) {
                        Text(text = stringResource(R.string.library_select_all))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // 按钮颜色过渡:M3 1.4.0 Button 对 enabled 颜色变化是瞬变(源码确认无 animateColorAsState),
                    // 显式加容器/文字色过渡(未选中灰 → 选中主题色)
                    val addEnabled = selected.isNotEmpty()
                    val btnContainer by animateColorAsState(
                        targetValue = if (addEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) // M3 禁用容器色
                        },
                        animationSpec = tween(AnimationTokens.Medium),
                        label = "addBtnContainer",
                    )
                    val btnContent by animateColorAsState(
                        targetValue = if (addEnabled) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) // M3 禁用文字色
                        },
                        animationSpec = tween(AnimationTokens.Medium),
                        label = "addBtnContent",
                    )
                    Button(
                        enabled = addEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = btnContainer,
                            contentColor = btnContent,
                            disabledContainerColor = btnContainer,
                            disabledContentColor = btnContent,
                        ),
                        onClick = {
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
                                onBack()
                                // 书名净化后台跑,不阻塞返回书架
                                toClean.forEach { book ->
                                    cleanScope.launch {
                                        cleaner?.let { bookRepo.aiCleanBook(book, it) }
                                    }
                                }
                            }
                        },
                    ) {
                        Text(text = stringResource(R.string.library_add_selected))
                    }
                }
            }
        }
    }
}
