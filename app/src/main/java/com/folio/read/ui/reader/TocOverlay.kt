package com.folio.read.ui.reader

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.folio.read.R
import com.folio.read.ui.components.FolioTopBar

/**
 * 章节目录覆盖层(单 Activity 阅读页内):全屏盖在阅读页上,阅读页组合保持存活
 * (分页状态/朗读绑定不丢),行为与旧「目录 Activity 盖在阅读页上」一致。
 * 章节标题由阅读页直接提供(打开即渲染,零加载);点击章节回调跳转,返回键关闭。
 */
@Composable
fun TocOverlay(
    titles: List<String>,
    currentChapter: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // 覆盖层在前:返回键优先关闭目录,不触发阅读页返回
    BackHandler { onDismiss() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            FolioTopBar(
                titleRes = R.string.toc,
                onBack = onDismiss,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // 打开即定位到当前章,免手动下滑
                state = rememberLazyListState(initialFirstVisibleItemIndex = currentChapter.coerceAtLeast(0)),
            ) {
                itemsIndexed(titles) { index, title ->
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
                        modifier = Modifier.clickable { onSelect(index) },
                    )
                }
            }
        }
    }
}
