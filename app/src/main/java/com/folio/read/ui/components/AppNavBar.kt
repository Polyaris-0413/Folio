package com.folio.read.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.folio.read.ui.navigation.AppSections
import com.folio.read.ui.navigation.mainRoutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 不产生任何交互事件的 InteractionSource。
 * material3 1.4.0 的 NavigationBarItem 把涟漪硬编码在选中指示胶囊上(不走 LocalIndication),
 * 传入静默源后胶囊上的涟漪因收不到按压事件而不再绘制;选中态动画与点击行为不受影响。
 * 底栏涟漪在此禁用:胶囊展开动画让涟漪"先闪后现"的悬空感明显(涟漪与胶囊是两个独立部件,
 * 见 material3 NavigationBarItem 源码注释),而胶囊展开+图标填充已覆盖位置与激活反馈。
 */
private class SilentInteractionSource : MutableInteractionSource {
    override val interactions: Flow<Interaction> = emptyFlow()
    override suspend fun emit(interaction: Interaction) = Unit
    override fun tryEmit(interaction: Interaction): Boolean = false
}

/**
 * 底部导航栏,移植自 Finito 的 AppNavBar,后对齐 Book's Story。
 * 文字常显(alwaysShowLabel 默认 true):去掉 Grit 的"选中上浮+文字渐显",
 * 避免与图标填充切换、胶囊展开三层选中动画叠加过乱。
 * 现动效分层:胶囊展开=位置、图标填充=激活态(涟漪已禁用)。
 */
@Composable
fun AppNavBar(
    selectedSection: AppSections,
    onSectionSelected: (AppSections) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        mainRoutes.forEach { route ->
            val selected = selectedSection == route
            // 每个 item 独立的静默交互源,取消选中胶囊上的涟漪(悬空感,见类注释)
            val interactionSource = remember { SilentInteractionSource() }
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        onSectionSelected(route)
                    }
                },
                icon = {
                    Crossfade(
                        targetState = selected,
                        animationSpec = tween(150),
                        label = "navIcon",
                    ) { isSelected ->
                        Icon(
                            painter = painterResource(if (isSelected) route.selectedIconRes else route.iconRes),
                            contentDescription = null,
                        )
                    }
                },
                label = { Text(text = stringResource(route.labelRes)) },
                interactionSource = interactionSource,
            )
        }
    }
}
