# D10 幂等 + 限流 全量验证脚本（兼容 Windows PowerShell 5.1）
# 用法：powershell -ExecutionPolicy Bypass -File verify_demo.ps1
# 前提：应用已在 http://localhost:8080 启动
$ErrorActionPreference = 'Continue'
$BASE = 'http://localhost:8080'
$out = New-Object System.Text.StringBuilder
function Log($m) { [void]$out.AppendLine($m) }

# 从异常响应中读取状态码与正文
function Read-Error($ex) {
    $resp = $ex.Exception.Response
    if ($resp) {
        $code = [int]$resp.StatusCode
        try {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $content = $reader.ReadToEnd()
            $reader.Close()
        } catch { $content = '' }
        return "HTTP ${code}: $content"
    }
    return "ERR: $($ex.Exception.Message)"
}

function Send-Order($key, $body) {
    try {
        $r = Invoke-WebRequest -Uri "$BASE/idempotent/order" -Method Post `
            -Headers @{ 'Idempotency-Key' = $key } -ContentType 'application/json' -Body $body -UseBasicParsing
        return "HTTP $([int]$r.StatusCode): $($r.Content)"
    } catch {
        return Read-Error $_
    }
}

# ============ A. 幂等 ============
Log '================ A. 幂等 ================'
$bodyA = '{"userId":1001,"goodsId":"G-1","quantity":2,"remark":"first"}'
$bodyB = '{"userId":1001,"goodsId":"G-2","quantity":99,"remark":"DIFFERENT"}'

Log ("[初始] 业务执行次数: " + ((Invoke-RestMethod "$BASE/idempotent/exec-count") | ConvertTo-Json -Compress))

Log "`n[场景1] 首次 K1（应成功，业务执行一次）"
Log (Send-Order 'K1' $bodyA)

Log "`n[场景2] 相同参数、相同 K1 再次请求（应返回原结果，不重复执行）"
Log (Send-Order 'K1' $bodyA)

Log "`n[场景3] 两个请求同时使用 K3（最多一个进入业务）"
$concScript = {
    param($b)
    try {
        $r = Invoke-WebRequest -Uri 'http://localhost:8080/idempotent/order' -Method Post `
            -Headers @{ 'Idempotency-Key' = 'K3' } -ContentType 'application/json' -Body $b -UseBasicParsing
        return "HTTP $([int]$r.StatusCode): $($r.Content)"
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $code = [int]$resp.StatusCode
            try {
                $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
                $content = $reader.ReadToEnd(); $reader.Close()
            } catch { $content = '' }
            return "HTTP ${code}: $content"
        }
        return "ERR: $($_.Exception.Message)"
    }
}
$pool = [RunspaceFactory]::CreateRunspacePool(1, 2)
$pool.Open()
$ps1 = [PowerShell]::Create(); $ps1.RunspacePool = $pool; [void]$ps1.AddScript($concScript).AddArgument($bodyA)
$ps2 = [PowerShell]::Create(); $ps2.RunspacePool = $pool; [void]$ps2.AddScript($concScript).AddArgument($bodyA)
$h1 = $ps1.BeginInvoke(); $h2 = $ps2.BeginInvoke()
$r1 = $ps1.EndInvoke($h1); $r2 = $ps2.EndInvoke($h2)
Log ("--- 并发请求A ---`n" + ($r1 | Out-String))
Log ("--- 并发请求B ---`n" + ($r2 | Out-String))
$ps1.Dispose(); $ps2.Dispose(); $pool.Close(); $pool.Dispose()

Log "`n[场景4] 不同参数复用 K1（应拒绝）"
Log (Send-Order 'K1' $bodyB)

Log "`n[场景5] 新键 K2（应作为新请求处理）"
Log (Send-Order 'K2' $bodyA)

Log "`n[最终] 业务执行次数（预期=3：K1/K3 各一次、K2 一次）"
Log ((Invoke-RestMethod "$BASE/idempotent/exec-count") | ConvertTo-Json -Compress)

# ============ B. 限流 ============
Log "`n`n================ B. 限流 ================"
function Hit($path, $headers) {
    try {
        $r = Invoke-WebRequest -Uri ($BASE + $path) -Headers $headers -UseBasicParsing
        return [int]$r.StatusCode
    } catch {
        $resp = $_.Exception.Response
        if ($resp) { return [int]$resp.StatusCode }
        return -1
    }
}

Log "`n[场景B1] IP 维度 /ratelimit/ip 突发 10 次（预期约 2 个 200，其余 429）"
$codes = foreach($i in 1..10){ Hit '/ratelimit/ip' @{} }
Log ("状态码: " + ($codes -join ','))
$ok = ($codes | Where-Object { $_ -eq 200 }).Count
$limited = ($codes | Where-Object { $_ -eq 429 }).Count
Log "统计: 200 x $ok, 429 x $limited"

Log "`n[场景B2] 不同 IP 隔离（X-Forwarded-For=1.1.1.1，应拥有独立桶，约 2 个 200）"
$codes2 = foreach($i in 1..5){ Hit '/ratelimit/ip' @{ 'X-Forwarded-For' = '1.1.1.1' } }
Log ("状态码: " + ($codes2 -join ','))

Log "`n[场景B3] 用户维度隔离（X-User-Id=100 与 200 各 5 次，各自约 2 个 200）"
$c1 = foreach($i in 1..5){ Hit '/ratelimit/user' @{ 'X-User-Id' = '100' } }
$c2 = foreach($i in 1..5){ Hit '/ratelimit/user' @{ 'X-User-Id' = '200' } }
Log ("u100: " + ($c1 -join ','))
Log ("u200: " + ($c2 -join ','))

Log "`n[场景B4] 业务维度 /ratelimit/business 突发 5 次（所有调用方共享，约 2 个 200）"
$cb = foreach($i in 1..5){ Hit '/ratelimit/business' @{} }
Log ("状态码: " + ($cb -join ','))

Log "`n[场景B5] 重接口 /ratelimit/heavy 每次消耗 2 令牌，桶容量 4，突发 4 次（预期 2 个 200）"
$ch = foreach($i in 1..4){ Hit '/ratelimit/heavy' @{ 'X-Forwarded-For' = '9.9.9.9' } }
Log ("状态码: " + ($ch -join ','))

$out.ToString()
