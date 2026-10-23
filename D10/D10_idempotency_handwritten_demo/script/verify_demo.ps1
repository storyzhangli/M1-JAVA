# D10 数据库唯一约束幂等 全量验证脚本（兼容 Windows PowerShell 5.1）
# 用法：powershell -ExecutionPolicy Bypass -File verify_demo.ps1
# 前提：应用已在 http://localhost:8083 启动
$ErrorActionPreference = 'Continue'
$BASE = 'http://localhost:8083'
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

function Send-Order($key, $body, $tenant) {
    $headers = @{ 'Content-Type' = 'application/json' }
    if ($key) { $headers['Idempotency-Key'] = $key }
    if ($tenant) { $headers['X-Tenant-Id'] = $tenant }
    try {
        $r = Invoke-WebRequest -Uri "$BASE/api/orders" -Method Post `
            -Headers $headers -ContentType 'application/json' -Body $body -UseBasicParsing
        return "HTTP $([int]$r.StatusCode): $($r.Content)"
    } catch {
        return Read-Error $_
    }
}

$bodyA = '{"productId":1001,"quantity":2}'
$bodyB = '{"productId":2002,"quantity":9}'

# ============ 场景1：首次请求 K1（预期 201，业务执行一次） ============
Log '================ 场景1：首次请求 K1（预期 201） ================'
Log (Send-Order 'K1' $bodyA $null)

# ============ 场景2：相同参数、相同 K1（预期 200，返回原结果） ============
Log "`n================ 场景2：重复 K1（预期 200） ================"
Log (Send-Order 'K1' $bodyA $null)

# ============ 场景3：20 线程并发使用同一幂等键 CK1（预期仅 1 个 201） ============
Log "`n================ 场景3：20 线程并发 CK1（预期仅 1 个 201） ================"
$concScript = {
    param($b)
    try {
        $r = Invoke-WebRequest -Uri 'http://localhost:8083/api/orders' -Method Post `
            -Headers @{ 'Idempotency-Key' = 'CK1'; 'Content-Type' = 'application/json' } `
            -ContentType 'application/json' -Body $b -UseBasicParsing
        return [int]$r.StatusCode
    } catch {
        $resp = $_.Exception.Response
        if ($resp) { return [int]$resp.StatusCode }
        return -1
    }
}
$pool = [RunspaceFactory]::CreateRunspacePool(1, 20)
$pool.Open()
$jobs = @()
$handles = @()
for ($i = 0; $i -lt 20; $i++) {
    $ps = [PowerShell]::Create()
    $ps.RunspacePool = $pool
    [void]$ps.AddScript($concScript).AddArgument($bodyA)
    $handles += $ps.BeginInvoke()
    $jobs += $ps
}
$results = @()
for ($i = 0; $i -lt 20; $i++) {
    $results += ($jobs[$i].EndInvoke($handles[$i]))
    $jobs[$i].Dispose()
}
$pool.Close(); $pool.Dispose()
$created = ($results | Where-Object { $_ -eq 201 }).Count
$duplicate = ($results | Where-Object { $_ -eq 200 }).Count
$conflict = ($results | Where-Object { $_ -eq 409 }).Count
Log "20 并发结果统计：201 x $created，200 x $duplicate，409 x $conflict"
Log ("全部状态码: " + ($results -join ','))

# ============ 场景4：不同参数复用 K1（预期 409） ============
Log "`n================ 场景4：不同参数复用 K1（预期 409） ================"
Log (Send-Order 'K1' $bodyB $null)

# ============ 场景5：新键 K2（预期 201） ============
Log "`n================ 场景5：新键 K2（预期 201） ================"
Log (Send-Order 'K2' $bodyA $null)

# ============ 场景6：缺少幂等键（预期 400） ============
Log "`n================ 场景6：缺少幂等键（预期 400） ================"
Log (Send-Order $null $bodyA $null)

# ============ 场景7：参数校验失败（预期 400） ============
Log "`n================ 场景7：参数校验失败（预期 400） ================"
Log (Send-Order 'K3' '{"productId":-1,"quantity":0}' $null)

# ============ 场景8：租户隔离（tenant=T1 使用 K1，预期 201，独立作用域） ============
Log "`n================ 场景8：租户隔离 X-Tenant-Id=T1 使用 K1（预期 201） ================"
Log (Send-Order 'K1' $bodyA 'T1')

$out.ToString()
