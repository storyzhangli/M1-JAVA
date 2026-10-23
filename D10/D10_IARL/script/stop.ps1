# 停止 D10_IARL：查找命令行中包含模块名的 java 进程并结束。
# 采用逐进程遍历 + try/catch，避免某个进程的 CommandLine 访问异常中断整个流程。
$ErrorActionPreference = 'SilentlyContinue'

$targets = @()
foreach ($p in Get-CimInstance Win32_Process -Filter "Name='java.exe'") {
    try {
        if ($p.CommandLine -and $p.CommandLine -like '*D10_IARL*') {
            $targets += $p.ProcessId
        }
    } catch {
        # 忽略单个进程的访问异常，继续检查其余进程
    }
}

if ($targets.Count -eq 0) {
    Write-Output "未发现正在运行的 D10_IARL 进程。"
    exit 0
}

foreach ($id in $targets) {
    try {
        Stop-Process -Id $id -Force -ErrorAction Stop
        Write-Output ("停止进程 PID=" + $id)
    } catch {
        Write-Output ("无法停止 PID=" + $id + "，原因：" + $_.Exception.Message)
    }
}
Write-Output "停止流程执行完毕。"
