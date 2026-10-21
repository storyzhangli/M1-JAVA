#!/usr/bin/env bash
# 停止 D08_CONF_JVM_LLT（Linux/macOS）
APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="$APP_HOME/bin/app.pid"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    PID="$(cat "$PID_FILE")"
    kill "$PID"
    echo "[停止] 已发送停止信号，PID=$PID"
    rm -f "$PID_FILE"
else
    echo "[停止] 未发现运行中的进程（PID 文件不存在或进程已结束）。"
fi
