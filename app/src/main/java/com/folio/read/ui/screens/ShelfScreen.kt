package com.folio.read.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.folio.read.ui.theme.AnimationTokens
import com.materialkolor.palettes.TonalPalette
import kotlin.math.abs
import kotlin.math.floor

/**
 * 封面渐变:官方 TonalPalette(Google material-color-utilities)按书标识哈希生成,每本书专属不撞色。
 * 色相跳过 55..100° 黄绿段(低 chroma 下呈土色,观感差);chroma 36 = 官方「表达色」档;
 * tone 50→35 上浅下深:白字对比度上端 ≈4.49:1、下端 ≈7.78:1,覆盖大文本 3:1 与普通文本 4.5:1
 * (依据 tone 即 CIE L*,对比度=(1.05)/(Y+0.05),Y=((L*+16)/116)³)。
 * 曾试 45→30(对比度最稳但整体偏暗,用户感觉暗),回退到 50→35 折中档。
 * 种子用 dedupKey(文件稳定标识)而非书名:书名净化(本地/AI)改变时颜色不跳变,
 * 颜色始终是"这本书"的身份色而非"这个名字"的。
 */
private fun bookCoverGradient(seed: String): List<Color> {
    // 哈希 → 0..309,再跳过 55..99 土色段映射到 0..354,保证不撞土色
    val raw = abs(seed.hashCode()) % 310
    val hue = if (raw < 55) raw.toDouble() else (raw + 45).toDouble()
    val palette = TonalPalette.fromHueAndChroma(hue, 36.0)
    return listOf(
        Color(palette.tone(50)),
        Color(palette.tone(35)),
    )
}

/** 书架页:封面网格,无书时显示空态引导;列数由书架排版设置决定 */
@Composable
fun ShelfScreen(
    /** null=书库查询中(启动首帧),不显示空态占位符(有书时避免闪几帧占位);empty=确实无书 */
    books: List<Book>?,
    shelfLayout: ShelfLayout,
    /** 选择模式(长按进入):选中的书 id 集合,非空时点击卡片=切换选中、顶栏显示删除 */
    selectedBookIds: Set<Long>,
    onToggleSelect: (Book) -> Unit,
    onAddBook: () -> Unit,
    onOpenBook: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
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
            LazyVerticalGrid(
                columns = columns,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    val selected = book.id in selectedBookIds
                    // 加入/移除淡入淡出(净化改名/移出书架时位置变化由 placement 动画接管)
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
            // 空态 overlay:网格常驻保证删除动画可播,空态浮层淡入(与删书淡出重叠,过渡顺滑)
            // 内容自包居中 Box(AnimatedVisibility 内容不在 BoxScope,Modifier.align 不生效)
            if (books.isEmpty()) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(AnimationTokens.Medium)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_book),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(96.dp),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.shelf_empty_title),
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
@Composable
private fun CoverTitle(name: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textSize = with(density) { maxWidth.toPx() / 8f }
        val textSizeSp = with(density) { textSize.toSp() }
        // 含拉丁字母(如 "Book's Story")强制横排,否则竖排(书脊式)
        val isHorizontal = name.count { it in 'A'..'Z' || it in 'a'..'z' }.toFloat() / name.length > 0.3f
        if (isHorizontal) {
            Text(
                text = name,
                fontSize = textSizeSp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .align(Alignment.Center),
            )
        } else {
            // 竖排:逐字竖排,列满换列;列间距 = 字宽 × 1.2
            with(density) {
                val charHeight = textSize * 1.2f
                val perColumn = floor(maxHeight.toPx() * 0.6f / charHeight).toInt().coerceAtLeast(1)
                val columns = name.filter { it.isLetterOrDigit() }.toList().chunked(perColumn)
                Row(
                    horizontalArrangement = Arrangement.spacedBy((textSize * 0.2f).toDp()),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    columns.forEach { column ->
                        Column {
                            column.forEach { char ->
                                Text(
                                    text = char.toString(),
                                    fontSize = textSizeSp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    lineHeight = (charHeight * 1.05f).toSp(),
                                    modifier = Modifier.height((charHeight * 1.05f).toDp()),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
                .clip(shape)
                .background(Brush.verticalGradient(gradient)),
            contentAlignment = Alignment.Center,
        ) {
            CoverTitle(book.title.ifEmpty { stringResource(R.string.book_cover_placeholder) })
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
                .clip(shape)
                .background(Brush.verticalGradient(gradient)),
            contentAlignment = Alignment.Center,
        ) {
            CoverTitle(book.title.ifEmpty { stringResource(R.string.book_cover_placeholder) })
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
