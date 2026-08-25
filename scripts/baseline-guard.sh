#!/usr/bin/env bash
# Baseline Profile 录制守护:后台执行长命令,每 30 秒查一次进度,日志长时间无更新则判定卡住并自动停止
# 用途:录制版 generateBaselineProfile 可能卡住(gradle 挂起/设备无响应),此脚本兜底,避免无人值守时干等
# 用法: scripts/baseline-guard.sh "<要执行的命令>" [日志路径] [卡住分钟数]
#   例: scripts/baseline-guard.sh "./gradlew :app:generateBaselineProfile"
#        scripts/baseline-guard.sh "./gradlew :app:generateBaselineProfile" build/logs/bp.log 5
# 退出码:0=任务正常结束(等于任务自身退出码);1=判定卡住被停止
set -uo pipefail

CMD="${1:?用法: baseline-guard.sh \"<命令>\" [日志路径] [卡住分钟数]}"
LOG="${2:-build/logs/baseline-guard.log}"
STALE_MIN="${3:-5}"
mkdir -p "$(dirname "$LOG")"

echo "[guard] $(date '+%F %T') 启动: $CMD"
echo "[guard] 日志=$LOG | 卡住判定=${STALE_MIN} 分钟无新增输出"

# 后台启动;bash -c 包裹使管道/重定向按整条命令执行,$! 为包装进程 PID
MSYS_NO_PATHCONV=1 bash -c "$CMD" >"$LOG" 2>&1 &
TASK_PID=$!

# MSYS PID -> Windows PID(taskkill 只认 Windows PID)
winpid_of() {
  ps -W | awk -v p="$1" '$2==p {print $1; exit}'
}

stop_task_tree() {
  local WINPID
  WINPID=$(winpid_of "$TASK_PID")
  if [ -n "$WINPID" ]; then
    MSYS_NO_PATHCONV=1 taskkill /PID "$WINPID" /T /F >/dev/null 2>&1
  fi
  kill -9 "$TASK_PID" >/dev/null 2>&1
}

while true; do
  sleep 30
  if ! kill -0 "$TASK_PID" 2>/dev/null; then
    wait "$TASK_PID"
    CODE=$?
    echo "[guard] $(date '+%F %T') 任务已结束, exit=$CODE"
    exit "$CODE"
  fi
  # 日志最后更新超过 STALE_MIN 分钟 => 判定卡住
  if [ ! -f "$LOG" ] || [ -z "$(find "$LOG" -mmin "-${STALE_MIN}")" ]; then
    echo "[guard] $(date '+%F %T') 日志 ${STALE_MIN} 分钟无新增,判定卡住 → 停止任务"
    stop_task_tree
    echo "[guard] $(date '+%F %T') 已停止(卡住), 日志尾部:"
    tail -n 5 "$LOG" 2>/dev/null | sed 's/^/    /'
    exit 1
  fi
  echo "[guard] $(date '+%F %T') 运行中 | 日志 $(stat -c%s "$LOG" 2>/dev/null || echo 0)B | 最后更新 $(stat -c '%y' "$LOG" 2>/dev/null | cut -d. -f1)"
done
