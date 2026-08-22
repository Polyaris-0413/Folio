package com.folio.read.ui.components

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
 */
private class SilentInteractionSource : MutableInteractionSource {
    override val interactions: Flow<Interaction> = emptyFlow()
    override suspend fun emit(interaction: Interaction) = Unit
    override fun tryEmit(interaction: Interaction): Boolean = false
}

/**
 * 底部导航栏,移植自 Finito 的 AppNavBar。
 * 使用标准 Material3 NavigationBarItem(alwaysShowLabel = false),
 * 胶囊指示器 + 图标上浮 + label 渐显动画由 material3 内置驱动。
 */
@Composable
fun AppNavBar(
    selectedSection: AppSections,
    onSectionSelected: (AppSections) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        mainRoutes.forEach { route ->
            // 每个 item 独立的静默交互源,用于取消选中胶囊上的涟漪
            val interactionSource = remember { SilentInteractionSource() }
            NavigationBarItem(
                selected = selectedSection == route,
                onClick = {
                    if (selectedSection != route) {
                        onSectionSelected(route)
                    }
                },
                icon = {
                    Icon(painter = painterResource(route.iconRes), contentDescription = null)
                },
                label = { Text(text = stringResource(route.labelRes)) },
                alwaysShowLabel = false,
                interactionSource = interactionSource,
            )
        }
    }
}
