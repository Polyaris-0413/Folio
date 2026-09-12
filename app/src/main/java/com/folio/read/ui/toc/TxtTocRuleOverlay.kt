package com.folio.read.ui.toc

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folio.read.R
import com.folio.read.ui.components.FolioAlertDialog
import com.folio.read.ui.components.FolioTopBar

/**
 * TXT 目录规则覆盖层（单 Activity 阅读页内，与 [com.folio.read.ui.reader.TocOverlay] 同款形态）。
 *
 * 交互与 legado 的规则页对齐到「功能面」而非组件树——legado 那套依赖其自有设计系统
 * （LegadoTheme + ui.widget.components + Koin），与本项目 Material 3 规范冲突，故用 M3 重画：
 *  - 点击某条规则 = 应用到本书（重新分章），空正则条目表示「回到自动择优」；
 *  - 右侧开关 = 启用/停用该规则（参与打分择优）；
 *  - 长按 = 编辑（含删除）；
 *  - 顶栏 = 新建 / 恢复内置规则。
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
     * 此时点击条目改为打开编辑，避免出现一个点了没反应的入口
     */
    pickEnabled: Boolean = true,
) {
    BackHandler { onDismiss() }

    var editing by remember { mutableStateOf<TocRuleUi?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TocRuleUi?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            FolioTopBar(
                titleRes = R.string.toc_rule_manage,
                onBack = onDismiss,
                actions = {
                    TextButton(onClick = { creating = true }) {
                        Text(stringResource(R.string.toc_rule_new))
                    }
                    TextButton(onClick = { confirmRestore = true }) {
                        Text(stringResource(R.string.toc_rule_restore))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = stringResource(
                            if (pickEnabled) R.string.toc_rule_hint else R.string.toc_rule_hint_manage,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
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
                items(rules, key = { it.id }) { rule ->
                    val isCurrent = rule.rule == currentRule && currentRule.isNotEmpty()
                    ListItem(
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
                            Switch(
                                checked = rule.enable,
                                onCheckedChange = { onToggle(rule.id, it) },
                            )
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = { if (pickEnabled) onPick(rule.rule) else editing = rule },
                            onLongClick = { editing = rule },
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
                TextButton(onClick = {
                    onDelete(deleteTarget.id)
                    confirmDelete = null
                }) { Text(stringResource(R.string.shelf_delete_confirm)) }
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
                TextButton(onClick = {
                    onRestoreBuiltIn()
                    confirmRestore = false
                }) { Text(stringResource(R.string.confirm)) }
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
                        .padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it },
                    label = { Text(stringResource(R.string.toc_rule_example)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.padding(top = 8.dp),
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
