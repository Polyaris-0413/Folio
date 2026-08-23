package com.folio.read.ui.components

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
            )
        }
    }
}
