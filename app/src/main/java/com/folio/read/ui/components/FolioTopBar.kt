package com.folio.read.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.folio.read.R
import com.folio.read.ui.theme.AnimationTokens

/**
 * 全局统一顶栏:标题居左,可选返回键与右侧操作区。
 * 容器色用比页面高一档的 surfaceContainer,与页面 surface 背景区分,体现层级。
 * 标题文字随页面切换做交叉淡化,背景不参与(顶栏始终不透明、不变色)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolioTopBar(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** 动态标题(如书名)优先于 titleRes;传入时 titleRes 传 0 即可 */
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            if (title != null) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // 仅文字淡入淡出,与内容区切换同步(Large 档)
                Crossfade(
                    targetState = titleRes,
                    animationSpec = tween(AnimationTokens.Large),
                    label = "topBarTitle",
                ) { res ->
                    Text(text = stringResource(res))
                }
            }
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.back),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
