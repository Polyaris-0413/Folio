package com.folio.read.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folio.read.R
import com.folio.read.data.AiConfig
import com.folio.read.data.LlmClient
import com.folio.read.data.PageTurnMode
import com.folio.read.data.PageTurnSettings
import com.folio.read.data.ShelfLayout
import com.folio.read.data.ShelfLayoutMode
import com.folio.read.data.UpdateCheckResult
import com.folio.read.data.UpdateChecker
import com.folio.read.data.compareVersions
import com.folio.read.ui.components.UpdateDialog
import com.folio.read.ui.components.connectedCornerRadius
import com.folio.read.ui.components.endCornerRadius
import com.folio.read.ui.components.endItemShape
import com.folio.read.ui.components.groupItemShape
import com.folio.read.ui.components.groupItemSpacing
import com.folio.read.ui.components.groupTitleSpacing
import com.folio.read.ui.components.listItemColors
import com.folio.read.ui.licenses.LicensesActivity
import com.folio.read.ui.theme.AnimationTokens
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Folio 的 GitHub 仓库地址 */
private const val GITHUB_REPO_URL = "https://github.com/Polyaris-0413/Folio"
private const val GITHUB_ISSUES_URL = "$GITHUB_REPO_URL/issues/new"

/**
 * "系统主题"项的展开状态:展开进度、圆角与动画序列。
 * 由 MainActivity 在主题 Crossfade 之外持有;展开高度由共享进度驱动,
 * 主题切换重建页面副本时,新副本读取同一进度继续动画,不中断。
 */
class ThemeItemExpandState(
    initialVisible: Boolean,
    private val onFollowSystemThemeChange: (Boolean) -> Unit,
    private val animationScope: CoroutineScope,
) {
    /** 展开进度:0 = 收起,1 = 展开 */
    val expandProgress = Animatable(if (initialVisible) 1f else 0f)
    val bottomRadius = Animatable(
        initialValue = if (initialVisible) connectedCornerRadius else endCornerRadius,
        typeConverter = Dp.VectorConverter,
    )
    private var animJob: Job? = null

    /** 启动恢复:直接跳到目标展开状态,不播放动画 */
    suspend fun restore(expanded: Boolean) {
        animJob?.cancel()
        expandProgress.snapTo(if (expanded) 1f else 0f)
        bottomRadius.snapTo(if (expanded) connectedCornerRadius else endCornerRadius)
    }

    fun onToggle(newValue: Boolean) {
        animJob?.cancel()
        onFollowSystemThemeChange(newValue)
        // 动画协程用调用方传入的 composition scope(随页面生命周期取消)
        animJob = animationScope.launch {
            if (!newValue) {
                // 展开:先收圆角(Micro),再展开内容(Medium)
                bottomRadius.animateTo(connectedCornerRadius, tween(AnimationTokens.Micro))
                expandProgress.animateTo(1f, tween(AnimationTokens.Medium))
            } else {
                // 收起:先折叠(Medium),再复原圆角(Micro)
                expandProgress.animateTo(0f, tween(AnimationTokens.Medium))
                bottomRadius.animateTo(endCornerRadius, tween(AnimationTokens.Micro))
            }
        }
    }
}

/**
 * 设置页,外观分组采用拼接卡片样式(移植自 Finito/Grit 的分段圆角写法)。
 * 主题切换(方案 2):"系统主题"开关默认开启;关闭后展开"主题"二选一,
 * 深/浅默认选中系统当前主题对应的那一项(由调用方在关闭开关时同步)。
 * 动画:展开/收起均为等待(串行)编排——展开先收圆角(Micro)再展开内容(Medium),
 * 收起先折叠(Medium)再复原圆角(Micro);时长取自 AnimationTokens 档位。
 */
@Composable
fun SettingsScreen(
    expandState: ThemeItemExpandState,
    followSystemTheme: Boolean,
    manualDark: Boolean,
    onManualDarkChange: (Boolean) -> Unit,
    darkTheme: Boolean,
    libraryDirName: String?,
    onSelectLibrary: () -> Unit,
    shelfSync: Boolean,
    onShelfSyncChange: (Boolean) -> Unit,
    shelfLayout: ShelfLayout,
    onShelfLayoutChange: (ShelfLayout) -> Unit,
    pageTurn: PageTurnSettings,
    onPageTurnChange: (PageTurnSettings) -> Unit,
    aiConfig: AiConfig,
    onAiConfigChange: (AiConfig) -> Unit,
    titleClean: Boolean,
    onTitleCleanChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showLayoutDialog by remember { mutableStateOf(false) }
    var showPageTurnDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }

    // 检查更新:查 GitHub 最新 release,有新版本时弹窗;查询失败静默
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
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    // 分组标题:后续同类型设置项归入此组
                    Text(
                        text = stringResource(R.string.settings_appearance_group),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = groupTitleSpacing),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(groupItemSpacing)) {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_follow_system_theme)) },
                        leadingContent = { SettingsIcon(R.drawable.ic_settings_theme) },
                            trailingContent = {
                                Switch(
                                    checked = followSystemTheme,
                                    onCheckedChange = expandState::onToggle,
                                )
                            },
                        colors = listItemColors(),
                        // 独立项(全圆角)⇄ 组首(下贴直角)的圆角过渡由 bottomRadius 驱动
                        modifier = Modifier.clip(
                            RoundedCornerShape(
                                topStart = endCornerRadius,
                                topEnd = endCornerRadius,
                                bottomEnd = expandState.bottomRadius.value,
                                bottomStart = expandState.bottomRadius.value,
                            ),
                        ),
                    )
                        // 展开/收起由共享的 expandProgress 驱动高度(替代 AnimatedVisibility):
                        // 主题切换重建副本时,新副本从同一进度继续动画,不中断
                        var fullHeightPx by remember { mutableIntStateOf(0) }
                        val density = LocalDensity.current
                        val itemHeight = with(density) {
                            (fullHeightPx * expandState.expandProgress.value).roundToInt().toDp()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                // 裁剪形状与内容一致(上 4dp 圆角),避免直角裁剪在展开初期切出尖角
                                .clip(endItemShape()),
                        ) {
                            // 内容按完整高度测量(unbounded 无视父级高度约束),父级裁剪实现收起/展开;
                            // 每次量到非零高度都刷新,避免某次组合量出 0 后展开区永久不可见(Android 12 曾出现)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(unbounded = true)
                                    .onSizeChanged { if (it.height > 0) fullHeightPx = it.height },
                            ) {
                                // 主题行是"标题 + 大控件"结构,自定义布局:
                                // 图标 + 标题一行,分段按钮全宽铺在下方,避免 ListItem 缩进错位
                                Column(
                                    modifier = Modifier
                                        .clip(endItemShape())
                                        .background(listItemColors().containerColor)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SettingsIcon(R.drawable.ic_settings_mode)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = stringResource(R.string.settings_theme),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = !manualDark,
                                            onClick = { onManualDarkChange(false) },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        ) {
                                            Text(text = stringResource(R.string.settings_theme_light))
                                        }
                                        SegmentedButton(
                                            selected = manualDark,
                                            onClick = { onManualDarkChange(true) },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        ) {
                                            Text(text = stringResource(R.string.settings_theme_dark))
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(groupTitleSpacing)) {
                // 分组标题:阅读(阅读页行为设置)
                Text(
                    text = stringResource(R.string.settings_group_reading),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(groupItemSpacing)) {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_page_turn)) },
                        leadingContent = { SettingsIcon(R.drawable.ic_settings_page_turn) },
                        trailingContent = {
                            Text(
                                text = pageTurnModeLabel(pageTurn.mode),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = listItemColors(),
                        modifier = Modifier
                            .clip(groupItemShape(0, 1))
                            .clickable { showPageTurnDialog = true },
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(groupTitleSpacing)) {
                // 分组标题:书架
                Text(
                    text = stringResource(R.string.settings_group_shelf),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(groupItemSpacing)) {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_shelf_library)) },
                        leadingContent = { SettingsIcon(R.drawable.ic_shelves) },
                        trailingContent = {
                            Text(
                                text = libraryDirName
                                    ?: stringResource(R.string.settings_shelf_library_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = listItemColors(),
                        modifier = Modifier
                            .clip(groupItemShape(0, 3))
                            .clickable { onSelectLibrary() },
                    )
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_shelf_sync)) },
                        supportingContent = { Text(text = stringResource(R.string.settings_shelf_sync_desc)) },
                        leadingContent = { SettingsIcon(R.drawable.ic_settings_shelf_sync) },
                        trailingContent = {
                            Switch(
                                checked = shelfSync,
                                onCheckedChange = onShelfSyncChange,
                            )
                        },
                        colors = listItemColors(),
                        modifier = Modifier.clip(groupItemShape(1, 3)),
                    )
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_shelf_layout)) },
                        leadingContent = { SettingsIcon(R.drawable.ic_shelf_layout) },
                        trailingContent = {
                            Text(
                                text = shelfLayoutModeLabel(shelfLayout.mode),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = listItemColors(),
                        modifier = Modifier
                            .clip(groupItemShape(2, 3))
                            .clickable { showLayoutDialog = true },
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(groupTitleSpacing)) {
                Text(
                    text = stringResource(R.string.settings_group_ai),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(groupItemSpacing)) {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_ai_key)) },
                        leadingContent = { SettingsIcon(R.drawable.ic_settings_ai_key) },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_right),
                                contentDescription = null,
                            )
                        },
                        colors = listItemColors(),
                        modifier = Modifier
                            .clip(groupItemShape(0, 2))
                            .clickable { showAiDialog = !showAiDialog },
                    )
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_ai_title_clean)) },
                        leadingContent = { SettingsIcon(R.drawable.ic_settings_title_clean) },
                        trailingContent = {
                            Switch(
                                checked = titleClean,
                                onCheckedChange = onTitleCleanChange,
                            )
                        },
                        colors = listItemColors(),
                        modifier = Modifier.clip(groupItemShape(1, 2)),
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(groupTitleSpacing)) {
                Text(
                    text = stringResource(R.string.settings_group_about),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
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
                            .clickable {
                                // 独立 Activity:主题跟随来源页面选择
                                context.startActivity(
                                    Intent(context, LicensesActivity::class.java)
                                        .putExtra(LicensesActivity.EXTRA_DARK_THEME, darkTheme),
                                )
                            },
                    )
                }
            }
        }
    }
}

    if (showLayoutDialog) {
        ShelfLayoutSheet(
            current = shelfLayout,
            // 只应用布局;面板在收起动画结束后自行回调 onDismiss 移除
            onApply = { onShelfLayoutChange(it) },
            onDismiss = { showLayoutDialog = false },
        )
    }
    if (showPageTurnDialog) {
        PageTurnSheet(
            current = pageTurn,
            onApply = { onPageTurnChange(it) },
            onDismiss = { showPageTurnDialog = false },
        )
    }
    if (showAiDialog) {
        AiConfigSheet(
            current = aiConfig,
            onApply = { onAiConfigChange(it) },
            onDismiss = { showAiDialog = false },
        )
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


/** 设置项前置图标:统一尺寸与色调 */
@Composable
private fun SettingsIcon(@DrawableRes iconRes: Int) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 书架排版模式显示名 */
@Composable
private fun shelfLayoutModeLabel(mode: ShelfLayoutMode): String = stringResource(
    when (mode) {
        ShelfLayoutMode.ONE -> R.string.shelf_layout_one
        ShelfLayoutMode.ADAPTIVE -> R.string.shelf_layout_adaptive
    },
)

/** 翻页手势模式显示名 */
@Composable
private fun pageTurnModeLabel(mode: PageTurnMode): String = stringResource(
    when (mode) {
        PageTurnMode.CLICK -> R.string.page_turn_click
        PageTurnMode.SWIPE -> R.string.page_turn_swipe
    },
)

/** 翻页手势选择面板:复用书架排版面板结构,点选即生效 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageTurnSheet(
    current: PageTurnSettings,
    onApply: (PageTurnSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMode by remember { mutableStateOf(current.mode) }
    // 跳过半展开:面板只保留完全展开/隐藏两个位置,避免内容超高时停在半高裁掉底部
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val modes = listOf(
        PageTurnMode.CLICK to R.string.page_turn_click,
        PageTurnMode.SWIPE to R.string.page_turn_swipe,
    )

    // 应用选择并播放收起动画,动画结束后再移除面板(直接移除会跳过退出动画)
    fun applyAndDismiss(mode: PageTurnMode) {
        onApply(PageTurnSettings(mode = mode))
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.page_turn_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            // 拼接卡片:点选即生效
            Column(verticalArrangement = Arrangement.spacedBy(groupItemSpacing)) {
                modes.forEachIndexed { index, (mode, labelRes) ->
                    val isSelected = selectedMode == mode
                    val shape = groupItemShape(index, modes.size)
                    ListItem(
                        headlineContent = { Text(text = stringResource(labelRes)) },
                        trailingContent = {
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        colors = listItemColors(),
                        modifier = Modifier
                            .clip(shape)
                            .clickable {
                                selectedMode = mode
                                applyAndDismiss(mode)
                            },
                    )
                }
            }
        }
    }
}

/** 连通测试结果:空闲/测试中/成功(模型回复)/失败(错误信息) */
private sealed interface ConnectionTestResult {
    data object Idle : ConnectionTestResult
    data object Running : ConnectionTestResult
    data class Success(val reply: String) : ConnectionTestResult
    data class Failure(val message: String) : ConnectionTestResult
}

/** AI 配置弹窗:API Key/地址/模型,保存即生效 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiConfigSheet(
    current: AiConfig,
    onApply: (AiConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var apiKey by remember { mutableStateOf(current.apiKey) }
    var baseUrl by remember { mutableStateOf(current.baseUrl) }
    var model by remember { mutableStateOf(current.model) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionTestResult>(ConnectionTestResult.Idle) }

    fun applyAndDismiss() {
        onApply(AiConfig(apiKey = apiKey.trim(), baseUrl = baseUrl.trim(), model = model.trim()))
        saved = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    /** 用弹窗内当前输入值请求一次 LLM,验证连通性,结果用 Toast 提示 */
    suspend fun runConnectionTest() {
        testResult = ConnectionTestResult.Running
        val result = try {
            val reply = LlmClient(
                AiConfig(apiKey = apiKey.trim(), baseUrl = baseUrl.trim(), model = model.trim()),
            ).chat(
                systemPrompt = "你是连通性测试助手,请只回复:连通正常",
                userMessage = "连通性测试",
            )
            ConnectionTestResult.Success(reply)
        } catch (e: Exception) {
            ConnectionTestResult.Failure(e.message ?: e.javaClass.simpleName)
        }
        testResult = result
        val message = when (result) {
            // 成功直接显示模型回复(已含「连通正常」语义),失败才加前缀说明原因
            is ConnectionTestResult.Success -> result.reply
            is ConnectionTestResult.Failure -> context.getString(R.string.ai_test_failure, result.message)
            else -> return
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.ai_base_url_label)) },
                    placeholder = { Text(stringResource(R.string.ai_base_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.ai_key_label)) },
                    placeholder = { Text(stringResource(R.string.ai_key_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.ai_model_label)) },
                    placeholder = { Text(stringResource(R.string.ai_model_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { scope.launch { runConnectionTest() } },
                        enabled = testResult !is ConnectionTestResult.Running &&
                            baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (testResult is ConnectionTestResult.Running) {
                                stringResource(R.string.ai_testing)
                            } else {
                                stringResource(R.string.ai_test_connection)
                            },
                        )
                    }
                    Button(
                        onClick = { applyAndDismiss() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (saved) stringResource(R.string.ai_saved) else stringResource(R.string.ai_save))
                    }
                }
            }
        }
    }
}

/** 书架排版选择面板:拼接卡片三选一,点选即生效 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelfLayoutSheet(
    current: ShelfLayout,
    onApply: (ShelfLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMode by remember { mutableStateOf(current.mode) }
    // 跳过半展开:面板只保留完全展开/隐藏两个位置,避免内容超高时停在半高裁掉底部
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val modes = listOf(
        ShelfLayoutMode.ONE to R.string.shelf_layout_one,
        ShelfLayoutMode.ADAPTIVE to R.string.shelf_layout_adaptive,
    )

    // 应用选择并播放收起动画,动画结束后再移除面板(直接移除会跳过退出动画)
    fun applyAndDismiss(layout: ShelfLayout) {
        onApply(layout)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.shelf_layout_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            // 拼接卡片:三选一,点选即生效
            Column(verticalArrangement = Arrangement.spacedBy(groupItemSpacing)) {
                modes.forEachIndexed { index, (mode, labelRes) ->
                    val isSelected = selectedMode == mode
                    val shape = groupItemShape(index, modes.size)
                    ListItem(
                        headlineContent = { Text(text = stringResource(labelRes)) },
                        trailingContent = {
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        colors = listItemColors(),
                        modifier = Modifier
                            .clip(shape)
                            .clickable {
                                selectedMode = mode
                                applyAndDismiss(ShelfLayout(mode = mode))
                            },
                    )
                }
            }
        }
    }
}
