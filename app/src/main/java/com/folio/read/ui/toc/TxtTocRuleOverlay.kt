package com.folio.read.ui.toc

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folio.read.R
import com.folio.read.ui.components.FolioAlertDialog
import com.folio.read.ui.components.FolioTopBar
import com.folio.read.ui.components.groupItemShape
import com.folio.read.ui.components.groupItemSpacing
import com.folio.read.ui.components.listItemColors

/**
 * TXT 目录规则覆盖层（单 Activity 阅读页内，与 [com.folio.read.ui.reader.TocOverlay] 同款形态）。
 *
 * 交互（对齐 legado 的功能面，组件树按本项目的 Material 3 约定重画）：
 *  - 行尾开关 = 启用/停用该规则（参与打分择优）；
 *  - 行尾「更多」= 编辑 / 删除（**可见入口**，不再依赖长按——长按对无鼠标设备与
 *    读屏都不友好，且与 Folio「开关行不整行可点击」的既有约定冲突）；
 *  - 选择模式（[pickEnabled]）下点按整行 = 把该规则应用到本书，左侧用对勾标出当前规则；
 *  - 顶栏 = 新建（图标）+ 溢出菜单（恢复内置——它是删除全部预置规则的批量操作，
 *    不与「新建」同级平铺）。
 *
 * 入参用 [TocRuleUi] 而非移植来的 `TxtTocRule`，原因见 [TocRuleUi] 的注释（相等语义与
 * Compose 状态判定的冲突）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TxtTocRuleOverlay(
    rules: List<TocRuleUi>,
    currentRule: String,
    onToggle: (id: Long, enabled: Boolean) -> Unit,
    onSave: (TocRuleUi) -> Unit,
    onDelete: (id: Long) -> Unit,
    onRestoreBuiltIn: () -> Unit,
    /** 传空串表示清除本书规则、回到自动择优 */
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    /**
     * 是否处于「为某本书选规则」的上下文。设置页进入时为 false（没有当前书），
     * 此时整行不可点，仅保留行尾菜单，避免出现一个点了没反应的入口
     */
    pickEnabled: Boolean = true,
    /**
     * 规则预览：规则 id → 该规则能把当前书切出的章节数（尚未算出的条目不在表里）。
     * 只在「为某本书选规则」时有意义，由调用方在后台逐条试算后填入。
     */
    previewCounts: Map<Long, Int> = emptyMap(),
    /** 预览进度（已算完 / 总数）；null 表示当前没在算 */
    previewProgress: Pair<Int, Int>? = null,
) {
    BackHandler { onDismiss() }

    var editing by remember { mutableStateOf<TocRuleUi?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TocRuleUi?>(null) }
    var topMenuOpen by remember { mutableStateOf(false) }

    // 预览一旦开始，章数行就**恒占位**：原先未算完时不渲染该槽，后台逐条填入的过程中
    // ListItem 会在 Two-line 与 Three-line 之间反复跳高，列表看着在抖。
    val previewActive = previewProgress != null || previewCounts.isNotEmpty()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            FolioTopBar(
                titleRes = R.string.toc_rule_manage,
                onBack = onDismiss,
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.toc_rule_new),
                        )
                    }
                    Box {
                        IconButton(onClick = { topMenuOpen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.shelf_more),
                            )
                        }
                        DropdownMenu(
                            expanded = topMenuOpen,
                            onDismissRequest = { topMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.toc_rule_restore)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_settings_restore),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    topMenuOpen = false
                                    confirmRestore = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // 宽屏不拉满:与设置页一致地限宽,避免大屏上文本行过长
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                // 与设置页一致：横向 12dp 内边距，卡片不贴屏边
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(groupItemSpacing),
            ) {
                previewProgress?.let { (done, total) ->
                    item {
                        // 预览在后台逐条试算：给出进度，避免用户以为界面卡住
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = stringResource(R.string.toc_rule_preview_progress, done, total),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                progress = { if (total == 0) 0f else done.toFloat() / total },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                }
                if (rules.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.toc_rule_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                // contentType 按开关状态分组:LazyColumn 只会在同一 contentType 内复用条目组合。
                // M3 的 Switch 把滑块的动画状态存在 Modifier.Node 的普通字段(非 remember 状态),
                // 若「开」与「关」的条目共用复用池,回收来的节点会带着相反状态的旧位置,
                // 新条目一出现就从旧位置滑到新位置——表现为滚动时开关重播切换动画。
                // 现象规律也印证:全部开启或全部关闭时不复现,状态混杂时才复现。
                itemsIndexed(
                    items = rules,
                    key = { _, rule -> rule.id },
                    contentType = { _, rule -> rule.enable },
                ) { index, rule ->
                    val isCurrent = rule.rule == currentRule && currentRule.isNotEmpty()
                    var rowMenuOpen by remember { mutableStateOf(false) }
                    ListItem(
                        overlineContent = if (previewActive) {
                            {
                                // 章数:选规则的直接依据。空正则条目按语义显示「按字数分章」。
                                // 颜色用 onSurfaceVariant:primary 留给「当前规则」这一个信号
                                val count = previewCounts[rule.id]
                                Text(
                                    text = when {
                                        rule.rule.isBlank() ->
                                            stringResource(R.string.toc_rule_by_word_count)
                                        count != null ->
                                            stringResource(R.string.toc_rule_chapter_count, count)
                                        else -> stringResource(R.string.toc_rule_counting)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            null
                        },
                        headlineContent = {
                            Text(
                                text = rule.name,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                // 空正则条目是 legado 的兜底规则,启用后等价于「按字数分章」
                                text = rule.example?.takeIf { it.isNotBlank() }
                                    ?: rule.rule.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.toc_rule_builtin_fallback),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 当前规则标记放在行尾(与设置页选择面板同一套表达:primary + 对勾)。
                                // 曾放在行首,非当前行必须留同宽占位,于是最左空出一块、且只有一行缩进
                                if (isCurrent) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(24.dp),
                                    )
                                }
                                // 开关放进固定尺寸的居中方框:开关视觉高 32dp、触摸目标 48dp,
                                // 与 40dp 的 IconButton 直接同排时视觉中心会随行高浮动,
                                // 用等高方框把两者的中心钉在同一条中线上
                                Box(
                                    modifier = Modifier.width(52.dp).height(48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Switch(
                                        checked = rule.enable,
                                        onCheckedChange = { onToggle(rule.id, it) },
                                    )
                                }
                                Box {
                                    IconButton(onClick = { rowMenuOpen = true }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_more_vert),
                                            contentDescription = stringResource(R.string.toc_rule_edit),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = rowMenuOpen,
                                        onDismissRequest = { rowMenuOpen = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.toc_rule_edit)) },
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_edit),
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                rowMenuOpen = false
                                                editing = rule
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.toc_rule_delete)) },
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_shelf_delete),
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                rowMenuOpen = false
                                                confirmDelete = rule
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        colors = listItemColors(),
                        modifier = Modifier
                            .clip(groupItemShape(index, rules.size))
                            // 整行只在「为某本书选规则」时可点:那是选择列表,整行点击是 M3 单选行的常规做法。
                            // 管理态（设置页入口）不给整行点击,编辑走行尾菜单,避免点了没反应或与开关抢手势
                            .then(
                                if (pickEnabled) Modifier.clickable { onPick(rule.rule) } else Modifier,
                            ),
                    )
                }
            }
        }
    }

    val editTarget = editing
    if (editTarget != null) {
        RuleEditDialog(
            initial = editTarget,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
            onDelete = {
                editing = null
                confirmDelete = editTarget
            },
        )
    }
    if (creating) {
        RuleEditDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = {
                onSave(it)
                creating = false
            },
        )
    }

    val deleteTarget = confirmDelete
    if (deleteTarget != null) {
        FolioAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.toc_rule_delete)) },
            text = { Text(stringResource(R.string.toc_rule_delete_confirm, deleteTarget.name)) },
            confirmButton = {
                TextButton(
                    // 破坏性动作用 error 角色
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = {
                        onDelete(deleteTarget.id)
                        confirmDelete = null
                    },
                ) { Text(stringResource(R.string.toc_rule_delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmRestore) {
        FolioAlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.toc_rule_restore)) },
            text = { Text(stringResource(R.string.toc_rule_restore_confirm)) },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = {
                        onRestoreBuiltIn()
                        confirmRestore = false
                    },
                ) { Text(stringResource(R.string.toc_rule_restore_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}


/** 新建/编辑规则：字段沿用 legado 的 TxtTocRule（名称/正则/示例），并对正则做实时语法校验 */
@Composable
private fun RuleEditDialog(
    initial: TocRuleUi?,
    onDismiss: () -> Unit,
    onSave: (TocRuleUi) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var rule by remember { mutableStateOf(initial?.rule.orEmpty()) }
    var example by remember { mutableStateOf(initial?.example.orEmpty()) }

    // 正则语法合法性:与引擎一致按 MULTILINE 编译(引擎正是这样编译规则的)
    val regexValid = remember(rule) {
        rule.isBlank() || runCatching { Regex(rule, RegexOption.MULTILINE) }.isSuccess
    }
    val canSave = name.isNotBlank() && regexValid

    FolioAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.toc_rule_new else R.string.toc_rule_edit))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.toc_rule_name)) },
                    isError = name.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rule,
                    onValueChange = { rule = it },
                    label = { Text(stringResource(R.string.toc_rule_regex)) },
                    isError = !regexValid,
                    supportingText = if (!regexValid) {
                        { Text(stringResource(R.string.toc_rule_regex_invalid)) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it },
                    label = { Text(stringResource(R.string.toc_rule_example)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                if (onDelete != null) {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        onClick = onDelete,
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text(stringResource(R.string.toc_rule_delete)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        TocRuleUi(
                            id = initial?.id ?: TocRuleUi.NEW_ID,
                            name = name,
                            rule = rule,
                            example = example.ifBlank { null },
                            // 新建规则排到末尾:serialNumber 取 -1(与 legado 数据类默认值一致),
                            // 实际顺序由用户后续编辑调整
                            serialNumber = initial?.serialNumber ?: -1,
                            enable = initial?.enable ?: true,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.toc_rule_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
