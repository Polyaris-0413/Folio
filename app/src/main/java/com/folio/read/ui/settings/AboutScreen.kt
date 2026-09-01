package com.folio.read.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.folio.read.R
import com.folio.read.data.UpdateCheckResult
import com.folio.read.data.UpdateChecker
import com.folio.read.data.compareVersions
import com.folio.read.ui.components.FolioTopBar
import com.folio.read.ui.components.SettingsIcon
import com.folio.read.ui.components.UpdateDialog
import com.folio.read.ui.components.groupItemShape
import com.folio.read.ui.components.groupItemSpacing
import com.folio.read.ui.components.groupTitleSpacing
import com.folio.read.ui.components.listItemColors
import kotlinx.coroutines.launch

/** Folio 的 GitHub 仓库地址 */
private const val GITHUB_REPO_URL = "https://github.com/Polyaris-0413/Folio"
private const val GITHUB_ISSUES_URL = "$GITHUB_REPO_URL/issues/new"

/**
 * 关于页(单 Activity 覆盖层):设置页的「关于」分组拆出后独立成页。
 * 检查更新/访问仓库/问题反馈/开源声明/赞助作者;主题与系统栏由宿主统一管理,本页只关心内容与返回。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    // 手势返回与顶栏返回按钮同行为:关闭覆盖层(单 Activity 覆盖层需自行接管返回键)
    BackHandler(enabled = true) { onBack() }

    // 检查更新:查 GitHub 最新 release,有新版本时弹窗;查询失败静默(逻辑随关于分组从设置页迁来)
    val scope = rememberCoroutineScope()
    val updateChecker = remember { UpdateChecker() }
    var updateVersion by remember { mutableStateOf<String?>(null) }

    fun checkUpdate() {
        Toast.makeText(context, context.getString(R.string.update_checking), Toast.LENGTH_SHORT).show()
        scope.launch {
            when (val result = updateChecker.checkLatest()) {
                is UpdateCheckResult.Latest -> {
                    val latest = result.release
                    val current = runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "0"
                    // 手动检查总提示;「不再提示」只抑制将来的自动检查(启动/定时),不影响主动查看
                    if (compareVersions(latest.version, current) <= 0) {
                        Toast.makeText(context, context.getString(R.string.update_latest), Toast.LENGTH_SHORT).show()
                    } else {
                        updateVersion = latest.version
                    }
                }
                // 仓库尚无 release = 没有可更新的版本
                UpdateCheckResult.NoRelease -> {
                    Toast.makeText(context, context.getString(R.string.update_latest), Toast.LENGTH_SHORT).show()
                }
                UpdateCheckResult.Failed -> {
                    Toast.makeText(context, context.getString(R.string.update_check_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FolioTopBar(titleRes = R.string.settings_group_about, onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(groupTitleSpacing),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(groupItemSpacing)) {
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.settings_about_update)) },
                            leadingContent = { SettingsIcon(R.drawable.ic_settings_update) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_right),
                                    contentDescription = null,
                                )
                            },
                            colors = listItemColors(),
                            modifier = Modifier
                                .clip(groupItemShape(0, 5))
                                .clickable { checkUpdate() },
                        )
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.settings_about_repo)) },
                            leadingContent = { SettingsIcon(R.drawable.ic_settings_source) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_right),
                                    contentDescription = null,
                                )
                            },
                            colors = listItemColors(),
                            modifier = Modifier
                                .clip(groupItemShape(1, 5))
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)),
                                    )
                                },
                        )
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.settings_about_sponsor)) },
                            leadingContent = { SettingsIcon(R.drawable.ic_settings_sponsor) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_right),
                                    contentDescription = null,
                                )
                            },
                            colors = listItemColors(),
                            modifier = Modifier
                                .clip(groupItemShape(2, 5))
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://ifdian.net/a/Polyaris")),
                                    )
                                },
                        )
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.settings_about_feedback)) },
                            leadingContent = { SettingsIcon(R.drawable.ic_settings_feedback) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_right),
                                    contentDescription = null,
                                )
                            },
                            colors = listItemColors(),
                            modifier = Modifier
                                .clip(groupItemShape(3, 5))
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_ISSUES_URL)),
                                    )
                                },
                        )
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.settings_about_licenses)) },
                            leadingContent = { SettingsIcon(R.drawable.ic_settings_license) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_right),
                                    contentDescription = null,
                                )
                            },
                            colors = listItemColors(),
                            modifier = Modifier
                                .clip(groupItemShape(4, 5))
                                .clickable { onOpenLicenses() },
                        )
                    }
                }
            }
        }
    }

    // 发现新版本弹窗:下载(浏览器)/关闭(仅关本次弹窗——手动检查不记「不再提醒」,
    // 该语义只属于冷启动自动检查,见 MainActivity)
    updateVersion?.let { version ->
        UpdateDialog(
            version = version,
            onDownload = {
                updateVersion = null
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("$GITHUB_REPO_URL/releases/latest")),
                )
            },
            onDismiss = {
                updateVersion = null
            },
        )
    }
}
