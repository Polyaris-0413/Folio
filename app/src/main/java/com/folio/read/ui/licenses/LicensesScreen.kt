package com.folio.read.ui.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.folio.read.R
import com.folio.read.ui.components.FolioAlertDialog
import com.folio.read.ui.components.FolioTopBar
import com.folio.read.ui.components.detachedItemShape
import com.folio.read.ui.components.endItemShape
import com.folio.read.ui.components.groupItemSpacing
import com.folio.read.ui.components.leadingItemShape
import com.folio.read.ui.components.listItemColors
import com.folio.read.ui.components.middleItemShape
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library

/**
 * 开源协议页(单 Activity 目的地)。
 * 库清单由 AboutLibraries 插件在构建时自动生成(aboutlibraries.json),
 * 列表复用设置页的拼接圆角卡片样式。
 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // 插件生成的 aboutlibraries.json 位于 raw 资源,手动读取后交给 Libs 解析;失败兜底为空列表
    val libs = remember {
        runCatching {
            val json = context.resources.openRawResource(R.raw.aboutlibraries)
                .bufferedReader().use { it.readText() }
            Libs.Builder().withJson(json).build()
        }.getOrNull()
    }
    var selectedLibrary by remember { mutableStateOf<Library?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FolioTopBar(titleRes = R.string.settings_about_licenses, onBack = onBack)

            val libraries = libs?.libraries.orEmpty()
            if (libraries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(R.string.settings_about_licenses_empty))
                }
            } else {
                // 拼接圆角卡片:首项 leading、末项 end、中间 middle,与设置页同一套样式
                // 底部间距自适应导航栏 inset + 固定呼吸空间,避免最后一项被手势条遮挡
                val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 16.dp,
                        bottom = 16.dp + navBarInset,
                    ),
                    verticalArrangement = Arrangement.spacedBy(groupItemSpacing),
                ) {
                    itemsIndexed(libraries) { index, library ->
                        val shape = when {
                            libraries.size == 1 -> detachedItemShape()
                            index == 0 -> leadingItemShape()
                            index == libraries.lastIndex -> endItemShape()
                            else -> middleItemShape()
                        }
                        ListItem(
                            headlineContent = { Text(text = library.name) },
                            supportingContent = {
                                Text(
                                text = listOf(
                                    library.licenses.firstOrNull()?.name,
                                    // 手动登记的自定义库无版本号,空白版本不参与拼接,避免「 · 」空尾巴
                                    library.artifactVersion?.takeIf { it.isNotBlank() },
                                ).filterNotNull().joinToString(" · "),
                                )
                            },
                            colors = listItemColors(),
                            modifier = Modifier
                                .clip(shape)
                                .clickable { selectedLibrary = library },
                        )
                    }
                }
            }
        }
    }

    selectedLibrary?.let { library ->
        val license = library.licenses.firstOrNull()
        FolioAlertDialog(
            onDismissRequest = { selectedLibrary = null },
            // 标题用项目名,协议名与全文放正文
            title = { Text(text = library.name) },
            text = {
                Column {
                    license?.name?.let { licenseName ->
                        Text(
                            text = licenseName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = license?.licenseContent.orEmpty().ifEmpty { license?.name.orEmpty() },
                        style = MaterialTheme.typography.bodySmall,
                        // 长协议文本限高后可滚动,避免超出被截断
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedLibrary = null }) {
                    Text(text = stringResource(R.string.back))
                }
            },
        )
    }
}
