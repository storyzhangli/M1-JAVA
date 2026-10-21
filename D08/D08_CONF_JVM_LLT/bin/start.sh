#!/usr/bin/env bash
# 启动 D08_CONF_JVM_LLT（Linux/macOS）
set -euo pipefail

APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
JAR_FILE="$APP_HOME/target/D08_CONF_JVM_LLT-1.0-SNAPSHOT.jar"
JVM_OPTIONS="$APP_HOME/script/jvm.options"
LOG_DIR="$APP_HOME/logs"
PID_FILE="$APP_HOME/bin/app.pid"

mkdir -p "$LOG_DIR/gc" "$LOG_DIR/heapdump" "$LOG_DIR/app"

if [ ! -f "$JAR_FILE" ]; then
    echo "[错误] 找不到可执行 JAR：$JAR_FILE"
    echo "       请先执行： mvn clean package -DskipTests"
    exit 1
fi

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "[提示] 应用已在运行，PID=$(cat "$PID_FILE")，如需重启请先执行 stop.sh"
    exit 1
fi

echo "[启动] JVM 参数文件：$JVM_OPTIONS"
nohup java @"$JVM_OPTIONS" -jar "$JAR_FILE" > "$LOG_DIR/console.out" 2>&1 &
echo $! > "$PID_FILE"
echo "[启动] 已启动，PID=$(cat "$PID_FILE")，日志：$LOG_DIR/console.out"
