package com.folio.read.ui.screens

/*
 * 封面书名排版(CoverTitle)移植自 legado(https://github.com/gedoor/legado)
 * 经 legado-with-MD3(https://github.com/HapeLee/legado-with-MD3)参考
 * SPDX-License-Identifier: GPL-3.0-only
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folio.read.R
import com.folio.read.data.Book
import com.folio.read.data.ShelfLayout
import com.folio.read.data.ShelfLayoutMode
import com.folio.read.ui.components.CoverArtwork
import com.folio.read.ui.components.CoverCache
import com.folio.read.ui.components.bookCoverGradient
import com.folio.read.ui.components.isHorizontalTitle
import com.folio.read.ui.components.renderCoverBitmap
import com.folio.read.ui.theme.AnimationTokens

/**
 * 书架页:顶栏下方内联搜索框(展开时按书名实时过滤)+ 封面网格,无书时显示空态引导;
 * 列数由书架排版设置决定 */
@Composable
fun ShelfScreen(
    /** null=书库查询中(启动首帧),不显示空态占位符(有书时避免闪几帧占位);empty=确实无书 */
    books: List<Book>?,
    shelfLayout: ShelfLayout,
    /** 选择模式(长按进入):选中的书 id 集合,非空时点击卡片=切换选中、顶栏显示删除 */
    selectedBookIds: Set<Long>,
    /** 搜索框展开(顶栏搜索图标切换);展开且有关键词时网格只显示匹配的书 */
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    /** 读过书返回书架时 +1,触发滚回顶部(刚读的书已置顶第 1 位,让用户直接看到) */
    scrollToTopSignal: Int = 0,
    scrollToTopAnimatedSignal: Int = 0,
    onToggleSelect: (Book) -> Unit,
    onAddBook: () -> Unit,
    onOpenBook: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 搜索过滤防抖:IME 逐字上屏会连续触发过滤 diff(每次都是全列表剧变,离场/重排动画错位),
    // 停止输入 300ms 后才应用关键词;清空关键词立即恢复,不防抖
    var debouncedQuery by remember { mutableStateOf(searchQuery) }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            debouncedQuery = ""
            return@LaunchedEffect
        }
        delay(300)
        debouncedQuery = searchQuery
    }
    // 搜索中=展开且有关键词(防抖后);空关键词时网格照常显示全部(过滤只在有关键词时生效)
    val searching = searchActive && debouncedQuery.isNotBlank()
    // debug:搜索动画 BUG 排查日志(可见性状态翻转)
    LaunchedEffect(searchActive) {
        Log.w("FolioSearch", "searchActive=$searchActive")
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 内联搜索框:顶栏下方下滑展开/上滑收起(expand/shrinkVertically 与图标开关配套)
        AnimatedVisibility(
            visible = searchActive,
            enter = expandVertically(animationSpec = tween(AnimationTokens.Medium)) +
                fadeIn(animationSpec = tween(AnimationTokens.Medium)),
            exit = shrinkVertically(animationSpec = tween(AnimationTokens.Medium)) +
                fadeOut(animationSpec = tween(AnimationTokens.Medium)),
        ) {
            // 展开即聚焦弹键盘(搜索页常规体验,省一步点击);LaunchedEffect 放在内容内,
            // 保证字段已组合后 requestFocus 才生效
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                Log.w("FolioSearch", "search field composed, request focus (keyboard up)")
                focusRequester.requestFocus()
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(text = stringResource(R.string.search_hint)) },
                singleLine = true,
                // 圆角=corner-small 8dp(M3 文本字段规范 token,与重命名/搜索输入框统一)
                // 留白:上方 16dp(=书架网格顶部内边距,三段间距统一:顶栏→搜索框=搜索框→书=顶栏→书),
                // 下方 0(与书的间距由网格自身的 16dp 顶边距提供,收起/展开时网格不跳动)
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .focusRequester(focusRequester),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (books == null) {
                // 加载中:空白等待查询结果,不渲染空态(空态仅确认真无书时显示)
            } else {
                // 网格列数:单列/自适应(空列表也渲染网格:删除最后一本时 item 退出动画
                // 依赖网格常驻,切到空态分支会让整格移出、动画直接消失)
                val columns = when (shelfLayout.mode) {
                    ShelfLayoutMode.ONE -> GridCells.Fixed(1)
                    // 自适应:单元格最小 152dp,保证封面与两行书名有足够宽度,避免列数过多文字被截断
                    ShelfLayoutMode.ADAPTIVE -> GridCells.Adaptive(minSize = 152.dp)
                }
                // 当前结果集(按书名过滤,忽略大小写):空态判断用;网格渲染在下方 AnimatedContent 内
                val displayBooks = if (searching) {
                    books.filter { it.title.contains(debouncedQuery, ignoreCase = true) }
                } else {
                    books
                }
                // 搜索词变化=全新结果集:旧结果网格整体淡出、新结果网格整体淡入(容器级过渡)。
                // 不用逐条目 animateItem 承担过滤过渡——LazyGrid 离场书的位置由新布局决定,
                // "列表骤减+滚动回弹"下离场书会错位(实测搜「三体」时未匹配的书跳到列表顶部
                // 再淡出);容器级过渡与此机制无关,天然无错位。
                // 各分支网格独立:过渡期间旧树按旧词冻结渲染,新树从顶部开始,滚动互不干扰
                AnimatedContent(
                    targetState = debouncedQuery,
                    transitionSpec = {
                        fadeIn(tween(AnimationTokens.Medium)) togetherWith
                            fadeOut(tween(AnimationTokens.Medium))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "searchResults",
                ) { query ->
                    val gridState = rememberLazyGridState()
                    // 读过书返回/自动同步新增书:平滑滚顶到 item 0(作用于当前分支网格)。
                    // 曾用 scrollToItem(0)(瞬移):会打断 books 重排的 placement 位移动画,
                    // 书多时书本从原位置滑到第一位的过程被瞬移掩盖(用户反馈「位移动画只有书少才可见」)。
                    LaunchedEffect(scrollToTopSignal, scrollToTopAnimatedSignal, books) {
                        if ((scrollToTopSignal > 0 || scrollToTopAnimatedSignal > 0) && books != null) {
                            gridState.animateScrollToItem(0)
                        }
                    }
                    // 过渡期间旧树按旧词冻结渲染,新树按新词过滤
                    val booksForGrid = if (query.isNotBlank()) {
                        books.filter { it.title.contains(query, ignoreCase = true) }
                    } else {
                        books
                    }
                    LazyVerticalGrid(
                        columns = columns,
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(booksForGrid, key = { it.id }) { book ->
                            val selected = book.id in selectedBookIds
                            // 加入/移除淡入淡出 + 默认弹簧位移动画(位置重排由框架接管);
                            // 承担删书/清空恢复等非搜索场景动画,搜索切换的过渡由容器负责
                            val itemAnim = Modifier.animateItem(
                                fadeInSpec = tween(AnimationTokens.Medium),
                                fadeOutSpec = tween(AnimationTokens.Medium),
                            )
                            // 选择模式下点击=切换选中,否则打开书;长按一律切换选中
                            val onClick = {
                                if (selectedBookIds.isNotEmpty()) onToggleSelect(book) else onOpenBook(book)
                            }
                            val onLongClick = { onToggleSelect(book) }
                            if (shelfLayout.mode == ShelfLayoutMode.ONE) {
                                BookRowCard(book, selected, onClick, onLongClick, modifier = itemAnim)
                            } else {
                                BookCard(book, selected, onClick, onLongClick, modifier = itemAnim)
                            }
                        }
                    }
                }
                // 空态浮层:visible 跟随结果集是否为空(翻转时淡入/淡出,含清空搜索恢复的淡出);
                // 空书架搜索时浮层恒在(无翻转),此时图标与文案的切换由内部 AnimatedContent 交叉淡化
                androidx.compose.animation.AnimatedVisibility(
                    visible = displayBooks.isEmpty(),
                    enter = fadeIn(animationSpec = tween(AnimationTokens.Medium)),
                    exit = fadeOut(animationSpec = tween(AnimationTokens.Medium)),
                ) {
                    AnimatedContent(
                        targetState = searching,
                        // fillMaxSize 固定容器尺寸(内容宽度变化不带动容器伸缩,无尺寸平移);
                        // 居中由 Column 自身排列控制(fillMaxSize+Center),不依赖
                        // AnimatedContent 的 contentAlignment(部分版本对稳态内容的居中不可靠)
                        transitionSpec = {
                            fadeIn(tween(AnimationTokens.Medium)) togetherWith
                                fadeOut(tween(AnimationTokens.Medium))
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "emptyState",
                    ) { isSearching ->
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // 搜索无匹配=放大镜+「没有找到相关书籍」;真无书=书+引导文案
                            Icon(
                                painter = painterResource(
                                    if (isSearching) R.drawable.ic_search else R.drawable.ic_book,
                                ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(96.dp),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(
                                    if (isSearching) R.string.search_empty else R.string.shelf_empty_title,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 封面书名排版,照搬 Legado CoilBookCover.CoverTextOverlay 的规则:
 * 字号 = 封面宽 ÷ 8;竖排逐字、列满换列;书名含拉丁字母占比 >30% 强制横排(最多 3 行,占宽 80%)。
 * 竖排时过滤标点符号(如「三体(全集)」→「三体全集」):Legado 竖排不处理成对标点,
 * 括号会单独成列、读序崩坏(三/体//(/全/集/)/);封面仅作装饰,展示名与正式书名解耦。
 */
/** 书籍卡片:专属渐变封面(完整书名)+ 书名;选中时主题色边框+封面罩色(淡入淡出) */
@Composable
private fun BookCard(
    book: Book,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient = bookCoverGradient(book.dedupKey.ifBlank { book.title })
    // M3 形状 token:卡片 = medium 12dp
    val shape = RoundedCornerShape(12.dp)
    // 选中反馈:罩色透明度 + 边框颜色过渡,出现/消失淡入淡出
    val overlayAlpha by animateFloatAsState(
        targetValue = if (selected) 0.25f else 0f,
        animationSpec = tween(AnimationTokens.Medium),
        label = "selectionOverlay",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(AnimationTokens.Medium),
        label = "selectionBorder",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .border(2.dp, borderColor, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            CoverArtwork(book = book, gradient = gradient, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = overlayAlpha)),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 单列(列表式)书籍卡片:封面缩略图在左,书名在右,整行宽度可完整显示长书名 */
@Composable
private fun BookRowCard(
    book: Book,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient = bookCoverGradient(book.dedupKey.ifBlank { book.title })
    // M3 形状 token:卡片 = medium 12dp
    val shape = RoundedCornerShape(12.dp)
    // 选中反馈:罩色透明度 + 边框颜色过渡,出现/消失淡入淡出
    val overlayAlpha by animateFloatAsState(
        targetValue = if (selected) 0.25f else 0f,
        animationSpec = tween(AnimationTokens.Medium),
        label = "selectionOverlay",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(AnimationTokens.Medium),
        label = "selectionBorder",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .border(2.dp, borderColor, shape),
    ) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .aspectRatio(3f / 4f)
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            CoverArtwork(book = book, gradient = gradient, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = overlayAlpha)),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
