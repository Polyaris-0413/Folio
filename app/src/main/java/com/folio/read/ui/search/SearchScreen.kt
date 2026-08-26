package com.folio.read.ui.search

/*
 * 搜索页:覆盖层(单 Activity,与阅读页/许可页同模式)。顶栏用统一 FolioTopBar(标题「搜索」+返回键),
 * 输入框放顶栏下方一整行(标准高度,文本完整);结果=书名实时过滤,结果项用书架同款封面卡片
 * 并带淡入;点结果打开书进阅读页。
 */

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folio.read.R
import com.folio.read.data.Book
import com.folio.read.ui.components.CoverArtwork
import com.folio.read.ui.components.FolioTopBar
import com.folio.read.ui.components.bookCoverGradient
import com.folio.read.ui.theme.AnimationTokens

@Composable
fun SearchScreen(
    books: List<Book>,
    onSelectBook: (Book) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // 按书名过滤(忽略大小写);空输入时不显示结果
    val results = remember(query, books) {
        if (query.isBlank()) emptyList()
        else books.filter { it.title.contains(query, ignoreCase = true) }
    }
    // 覆盖层返回手势:关搜索而不是退出 App
    BackHandler { onBack() }
    // 进入搜索页自动聚焦输入框 + 弹出输入法(搜索页常规体验,省一步点击)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // 点结果进阅读页前收起键盘(否则键盘留在阅读页,问题:覆盖层切换不自动收)
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶栏统一:FolioTopBar(标题「搜索」+返回键),与书架/阅读页一致
        FolioTopBar(titleRes = R.string.search, onBack = onBack)
        // 输入框:顶栏下方一整行,标准高度(文本完整、垂直居中)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(text = stringResource(R.string.search_hint)) },
            singleLine = true,
            // 圆角=corner-small 8dp(M3 文本字段规范 token,见 material-3 skill;此前 28dp 胶囊是魔数,已改)
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusRequester(focusRequester),
        )
        // 结果区:空态与结果常驻/过渡,避免列表首次组合无动画
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { book ->
                    // 书架单列卡片同款:封面缩略图 + 书名;结果项出现/消失淡入淡出
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = tween(AnimationTokens.Medium),
                                fadeOutSpec = tween(AnimationTokens.Medium),
                            )
                            .fillMaxWidth()
                            .clickable {
                                keyboard?.hide()
                                onSelectBook(book)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(12.dp)),
                        ) {
                            CoverArtwork(
                                book = book,
                                gradient = bookCoverGradient(book.dedupKey.ifBlank { book.title }),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // 空态浮层:始终组合,alpha 整体淡入淡出(避免 AnimatedContent 对内容尺寸过渡=从角放大收缩)
            val showEmpty = results.isEmpty() && query.isNotBlank()
            val emptyAlpha by animateFloatAsState(
                targetValue = if (showEmpty) 1f else 0f,
                animationSpec = tween(AnimationTokens.Medium),
                label = "searchEmptyAlpha",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
                    .graphicsLayer { alpha = emptyAlpha },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(96.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.search_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
