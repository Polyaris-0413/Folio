package com.folio.read.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.folio.read.R

/**
 * 发现新版本对话框:标题 + 版本号 + 「更新日志请前往查看」提示 + 下载/关闭。
 * 冷启动自动检查与设置页手动检查共用;关闭 = 调用方记住该版本并 Toast「本次更新不再提醒」。
 */
@Composable
fun UpdateDialog(
    version: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.update_found_title)) },
        text = { Text(text = stringResource(R.string.update_found_message, version)) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.primary,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(text = stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.update_close))
            }
        },
    )
}
