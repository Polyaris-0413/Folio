package com.folio.read.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 圆角分组卡片辅助函数,移植自 Finito 的 ListItemExt.kt(源自 Grit)。
 * 拼接原理:组内多个 ListItem 以 2dp 间距竖排,各 item 用 clip 切分段圆角
 * (首项上大下小、中间全小、末项上小下大),视觉上连成一张圆角卡片。
 *
 * Copyright (C) 2026  Shubham Gorai
 * SPDX-License-Identifier: GPL-3.0-only
 * 来源:https://github.com/shub39/Grit(shared/ui/.../components/ListItemExt.kt)
 * GPL-3.0 全文:https://www.gnu.org/licenses/gpl-3.0.html
 */

private const val CONNECTED_CORNER_RADIUS = 4
private const val END_CORNER_RADIUS = 16

/** 组内贴合处的圆角(与相邻 item 拼接) */
val connectedCornerRadius: Dp = CONNECTED_CORNER_RADIUS.dp

/** 组外端的大圆角 */
val endCornerRadius: Dp = END_CORNER_RADIUS.dp

/** 拼接组内 item 的间距(圆角贴合处的缝隙) */
val groupItemSpacing: Dp = 2.dp

/** 分组标题与卡片组的间距(M3 间距基线 8dp) */
val groupTitleSpacing: Dp = 8.dp

/** 分组卡片底色:surfaceContainerHigh,比默认 ListItem 的 surfaceContainerLow 更突出 */
@Composable
fun listItemColors(): ListItemColors {
    return ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
}

/** 组内第一个 item:顶部大圆角,底部小圆角(与下一项贴合) */
fun leadingItemShape(
    topRadius: Int = END_CORNER_RADIUS,
    bottomRadius: Int = CONNECTED_CORNER_RADIUS,
): Shape =
    RoundedCornerShape(
        topStart = topRadius.dp,
        topEnd = topRadius.dp,
        bottomEnd = bottomRadius.dp,
        bottomStart = bottomRadius.dp,
    )

/** 组内中间 item:全小圆角 */
fun middleItemShape(radius: Int = CONNECTED_CORNER_RADIUS): Shape =
    RoundedCornerShape(
        topStart = radius.dp,
        topEnd = radius.dp,
        bottomStart = radius.dp,
        bottomEnd = radius.dp,
    )

/** 组内最后一个 item:顶部小圆角,底部大圆角 */
fun endItemShape(
    topRadius: Int = CONNECTED_CORNER_RADIUS,
    bottomRadius: Int = END_CORNER_RADIUS,
): Shape =
    RoundedCornerShape(
        topStart = topRadius.dp,
        topEnd = topRadius.dp,
        bottomEnd = bottomRadius.dp,
        bottomStart = bottomRadius.dp,
    )

/** 单独一个 item(整组只有一项):全大圆角 */
fun detachedItemShape(radius: Int = END_CORNER_RADIUS): Shape = RoundedCornerShape(radius.dp)

/**
 * 按组内位置选拼接形状:首项上大下小、中间全小、末项上小下大、单项全大。
 * 组的形状规则单一来源——新增组/加项只需按 index/count 调用,无需手写各位置形状。
 */
fun groupItemShape(index: Int, count: Int): Shape = when {
    count <= 1 -> detachedItemShape()
    index == 0 -> leadingItemShape()
    index == count - 1 -> endItemShape()
    else -> middleItemShape()
}
