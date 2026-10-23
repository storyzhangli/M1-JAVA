# 启动 D10_IARL（PowerShell）
# 用法：在 bin 目录执行  ./start.ps1
$ErrorActionPreference = 'Stop'

# 定位模块根目录（bin 的上一级）
$APP_HOME = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$JVM_OPTIONS = Join-Path $APP_HOME 'script\jvm.options'
$JAR_FILE = Join-Path $APP_HOME 'target\D10_IARL-1.0-SNAPSHOT.jar'
$LOG_DIR = Join-Path $APP_HOME 'logs'
$CONSOLE_LOG = Join-Path $LOG_DIR 'console.out'

# 准备日志目录
foreach ($sub in @('', 'gc', 'heapdump', 'app')) {
    $dir = Join-Path $LOG_DIR $sub
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
}

if (-not (Test-Path -LiteralPath $JAR_FILE)) {
    Write-Output "[错误] 找不到可执行 JAR：$JAR_FILE"
    Write-Output "       请先在模块根目录执行： mvn clean package -DskipTests"
    exit 1
}

# 防重复启动：命令行包含模块名的 java 进程
$existing = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*D10_IARL*' }
if ($existing) {
    Write-Output "[提示] 应用可能已在运行（PID=$($existing.ProcessId)），如需重启请先执行 stop.ps1"
    exit 1
}

Write-Output "[启动] 工作目录 : $APP_HOME"
Write-Output "[启动] JVM 参数 : $JVM_OPTIONS"
Write-Output "[启动] 控制台日志: $CONSOLE_LOG"

# 在新窗口启动，窗口标题 D10-APP；工作目录锁定到模块根目录
Start-Process -FilePath 'cmd.exe' `
    -ArgumentList '/c', 'title D10-APP && java @"{0}" -jar "{1}" > "{2}" 2>&1' -f $JVM_OPTIONS, $JAR_FILE, $CONSOLE_LOG `
    -WorkingDirectory $APP_HOME `
    -WindowStyle Normal

Write-Output "[启动] 已在新窗口启动（标题 D10-APP）。可通过 stop.ps1 停止。"
