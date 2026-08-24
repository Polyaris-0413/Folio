package com.folio.read.ui.library

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        candidates = withContext(Dispatchers.IO) {
            libraryRepo.scanLibrary().map { (uri, name) ->
                FileCandidate(uri, name.ifBlank { context.getString(R.string.unnamed) })
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FolioTopBar(titleRes = R.string.library_add_title, onBack = onBack)

            val list = candidates
            Crossfade(
                targetState = when {
                    list == null -> 0
                    list.isEmpty() -> 1
                    else -> 2
                },
                modifier = Modifier.weight(1f),
                animationSpec = tween(AnimationTokens.Large),
                label = "libraryScan",
            ) { state ->
                when (state) {
                    0 -> Unit // 扫描期间不显示任何内容,就绪后淡入
                    1 -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = stringResource(R.string.library_scan_empty))
                    }
                    else -> {
                        val result = list!!
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(result) { candidate ->
                                    val uri = candidate.uri.toString()
                                    val isChecked = uri in selected
                                    ListItem(
                                        headlineContent = { Text(text = candidate.name) },
                                        trailingContent = {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selected = if (checked) {
                                                        selected + uri
                                                    } else {
                                                        selected - uri
                                                    }
                                                },
                                            )
                                        },
                                        modifier = Modifier.clickable {
                                            selected = if (isChecked) selected - uri else selected + uri
                                        },
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = {
                                    selected = if (selected.size == result.size) {
                                        emptySet()
                                    } else {
                                        result.map { it.uri.toString() }.toSet()
                                    }
                                }) {
                                    Text(text = stringResource(R.string.library_select_all))
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    enabled = selected.isNotEmpty(),
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
                                    Text(text = stringResource(R.string.library_add_selected, selected.size))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
