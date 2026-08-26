package com.folio.read.ui.components

/*
 * 顶栏标题切换 FadeThrough 动画移植自 Book's Story(https://github.com/Acclorite/book-story)
 * SPDX-License-Identifier: GPL-3.0-only
 */

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
                // 与内容区同款 FadeThrough(Large 档):淡入 + 0.975 微缩放,切页节奏一致
                AnimatedContent(
                    targetState = titleRes,
                    transitionSpec = {
                        (fadeIn(tween(AnimationTokens.Large)) +
                            scaleIn(tween(AnimationTokens.Large), initialScale = 0.975f))
                            .togetherWith(fadeOut(tween(AnimationTokens.Large)))
                    },
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
            // 显式设导航图标色:防默认值在深色下异常,返回键始终跟随主题前景色
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
