package com.folio.read.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

/**
 * 让 Tooltip 气泡固定在锚点(按钮)下方弹出的位置规则。
 * 顶栏上的图标按钮(溢出菜单/删除/目录等)位于屏幕顶部,用默认规则(Material 自动判断上下)
 * 往往会挤到上方、顶住屏幕边缘显得拥挤;这里统一强制放到按钮下缘之下,
 * 水平按锚点居中对齐并在屏幕内夹紧。返回值可直接用作 TooltipBox 的 positionProvider。
 *
 * 注:此版本 Material3 的 TooltipBox.positionProvider 类型是 androidx.compose.ui.window.PopupPositionProvider
 * (普通接口,非 fun interface),方法为 calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)。
 */
@Composable
fun rememberBelowTooltipPositionProvider(spacing: Dp = 8.dp): PopupPositionProvider {
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    return object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowSize: IntSize,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
        ): IntOffset {
            val x = (anchorBounds.center.x - popupContentSize.width / 2)
                .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
            return IntOffset(x, anchorBounds.bottom + spacingPx.roundToInt())
        }
    }
}
