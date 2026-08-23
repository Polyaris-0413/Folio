package com.folio.read.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.folio.read.ui.navigation.AppSections
import com.folio.read.ui.navigation.mainRoutes

/**
 * 底部导航栏,移植自 Finito 的 AppNavBar。
 * 使用标准 Material3 NavigationBarItem(alwaysShowLabel = false),
 * 胶囊指示器 + 图标上浮 + label 渐显动画由 material3 内置驱动;
 * 图标切换:选中/未选中两套图标(FILL1 实心 / FILL0 描边)交叉淡化,同 Book's Story。
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
                alwaysShowLabel = false,
            )
        }
    }
}
