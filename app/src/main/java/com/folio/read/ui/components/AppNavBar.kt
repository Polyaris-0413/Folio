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
 * 底部导航栏,移植自 Finito 的 AppNavBar,后对齐 Book's Story。
 * 文字常显(alwaysShowLabel 默认 true):去掉 Grit 的"选中上浮+文字渐显",
 * 避免与图标填充切换、胶囊展开三层选中动画叠加过乱。
 * 现动效分层:胶囊展开=位置、图标填充=激活态、涟漪=按压。
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
            )
        }
    }
}
