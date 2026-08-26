package com.folio.read.ui.components

/*
 * 封面图(Compose 组合版):渐变 + 书名,竖排书名渲染成位图、横排(拉丁)走 Text。
 * 从 ShelfScreen 抽取共用(书架卡片 + 搜索结果)。竖排位图渲染与缓存见同包 BookCover.kt。
 */

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.folio.read.R
import com.folio.read.data.Book

/**
 * 封面横排书名(仅拉丁书名,如 "Book's Story")。竖排书名已走位图渲染(renderCoverBitmap),
 * 此组件只服务 CoverArtwork 的横排分支——曾经的竖排分支(单 Text+换行)已由位图取代。
 */
@Composable
private fun CoverTitle(name: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textSize = with(density) { maxWidth.toPx() / 8f }
        val textSizeSp = with(density) { textSize.toSp() }
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
    }
}

/**
 * 封面图:渐变 + 书名。竖排书名渲染成位图缓存——书架重组时逐字 Text 节点
 * 是退出阅读页 ~200ms 掉帧的根因,位图直接画零组合成本;横排(拉丁书名)罕见,保持 Compose 渲染。
 */
@Composable
fun CoverArtwork(book: Book, gradient: List<Color>, modifier: Modifier = Modifier) {
    val title = book.title.ifEmpty { stringResource(R.string.book_cover_placeholder) }
    if (isHorizontalTitle(title)) {
        Box(modifier = modifier.background(Brush.verticalGradient(gradient)), contentAlignment = Alignment.Center) {
            CoverTitle(title)
        }
    } else {
        BoxWithConstraints(modifier = modifier) {
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val bitmap = remember(w, h, book.id, title) {
                CoverCache.get("${book.id}|$title|${w}x${h}|v2") {
                    renderCoverBitmap(w, h, title, gradient)
                }
            }
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}
