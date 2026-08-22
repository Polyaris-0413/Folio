#!/usr/bin/env bash
# 调试操作开始前的双端通知:手机(adb 非静默通知)+ 电脑(PowerShell 气泡通知)
# 用法: scripts/notify_debug.sh "要说明的操作内容"
set -uo pipefail

MSG="${1:-调试操作开始}"
ADB="/c/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
TAG="folio-debug"

# 手机通知:固定 tag,重复发送会覆盖旧通知,不会堆积
# 注意:整个远程命令作为单个字符串传给 adb shell,避免引号被拼接丢失
if MSYS_NO_PATHCONV=1 "$ADB" shell "cmd notification post -t 'Folio 调试' '$TAG' '$MSG'" >/dev/null 2>&1; then
  echo "[notify] 手机通知已发送"
else
  echo "[notify] 手机通知发送失败(设备可能未连接)"
fi

# 电脑通知:PowerShell 气泡提示,经 UTF-16LE base64 传递避免中文乱码
PS_SCRIPT='Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$n = New-Object System.Windows.Forms.NotifyIcon
$n.Icon = [System.Drawing.SystemIcons]::Information
$n.BalloonTipTitle = "Folio 调试"
$n.BalloonTipText = "'"$MSG"'"
$n.Visible = $true
$n.ShowBalloonTip(6000)
Start-Sleep -Seconds 7
$n.Dispose()'
PS_B64=$(iconv -f UTF-8 -t UTF-16LE <<< "$PS_SCRIPT" | base64 -w0)
if powershell.exe -NoProfile -EncodedCommand "$PS_B64" >/dev/null 2>&1; then
  echo "[notify] 电脑通知已发送"
else
  echo "[notify] 电脑通知发送失败"
fi
