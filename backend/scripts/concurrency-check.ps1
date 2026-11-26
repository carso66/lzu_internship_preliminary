<#
.SYNOPSIS
    L2-10 并发不超卖验证脚本。

.DESCRIPTION
    每次运行都会新建一个「库存 = 1」的商品，然后同时发起 N 个有效下单请求（每个买 1 件），
    最后核对三件事：
      1. 响应：恰好 1 个 201 成功，其余全部 409 / 40901「库存不足」；
      2. 数据：商品库存恰好为 0；
      3. 数据：本轮恰好新增 1 条订单。

    脚本自行创建数据、自行核对，可重复运行，不会依赖上一次运行留下的数据。

.PARAMETER BaseUrl
    服务地址，默认 http://localhost:8080

.PARAMETER Concurrency
    并发请求数，默认 10

.EXAMPLE
    pwsh -File backend/scripts/concurrency-check.ps1

.EXAMPLE
    pwsh -File backend/scripts/concurrency-check.ps1 -BaseUrl http://127.0.0.1:8080 -Concurrency 10
#>
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [int]$Concurrency = 10
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$utf8 = New-Object System.Text.UTF8Encoding($false)
$runId = Get-Date -Format 'HHmmssfff'
$productTitle = "并发测试商品-$runId"
$buyerPhone = "199$runId"

function Invoke-Api {
    param([string]$Method, [string]$Path, [string]$Body)

    $arguments = @('-s', '--noproxy', '*', '-m', '15', '-X', $Method, "$BaseUrl$Path")
    if ($null -ne $Body) {
        $temp = Join-Path ([System.IO.Path]::GetTempPath()) "l2-10-$runId-$([guid]::NewGuid().ToString('N')).json"
        [System.IO.File]::WriteAllText($temp, $Body, $utf8)
        $arguments += @('-H', 'Content-Type: application/json; charset=utf-8', '--data-binary', "@$temp")
    }
    $arguments += @('-w', "`n__STATUS__%{http_code}")

    $raw = (& curl.exe @arguments 2>&1) -join "`n"
    $index = $raw.LastIndexOf('__STATUS__')
    if ($index -lt 0) {
        throw "请求 $Method $Path 失败：$raw"
    }
    $json = $raw.Substring(0, $index).Trim()
    $status = [int]$raw.Substring($index + 10).Trim()

    $parsed = $null
    if ($json) {
        $parsed = $json | ConvertFrom-Json
    }
    return [pscustomobject]@{ Status = $status; Code = $parsed.code; Message = $parsed.message; Data = $parsed.data }
}

Write-Host "== L2-10 并发不超卖验证 =="
Write-Host "服务地址: $BaseUrl"
Write-Host "并发数  : $Concurrency"
Write-Host ''

Write-Host '[1/4] 准备数据：新建库存为 1 的商品'
$created = Invoke-Api -Method 'POST' -Path '/api/products' -Body (@{
        title  = $productTitle
        price  = 1990
        stock  = 1
        category = '测试'
    } | ConvertTo-Json -Compress)
if ($created.Status -ne 201) {
    throw "创建商品失败：HTTP $($created.Status) $($created.Message)"
}
$productId = $created.Data.id
Write-Host "      商品 id=$productId，title=$productTitle，stock=1"

Write-Host "[2/4] 并发发起 $Concurrency 个下单请求（每个买 1 件，全部同时打出）"
$payload = @{
    productId  = $productId
    quantity   = 1
    buyerName  = '并发测试'
    buyerPhone = $buyerPhone
    address    = '兰州市城关区天水南路222号'
} | ConvertTo-Json -Compress

# 放开到同一主机的并发连接数上限，否则 .NET 默认只开 2 条连接，压力不够
[System.Net.ServicePointManager]::DefaultConnectionLimit = [Math]::Max(16, $Concurrency * 2)
[System.Net.ServicePointManager]::Expect100Continue = $false
Add-Type -AssemblyName System.Net.Http -ErrorAction SilentlyContinue
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(30)

# 先一次性把 N 个请求全部发出去（不 await），再用 WaitAll 统一收集
$tasks = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]'
for ($i = 1; $i -le $Concurrency; $i++) {
    $content = New-Object System.Net.Http.StringContent($payload, [System.Text.Encoding]::UTF8, 'application/json')
    $tasks.Add($client.PostAsync("$BaseUrl/api/orders", $content))
}
[System.Threading.Tasks.Task]::WaitAll($tasks.ToArray())

$results = @()
foreach ($task in $tasks) {
    $response = $task.Result
    $jsonText = $response.Content.ReadAsStringAsync().Result
    $code = $null
    if ($jsonText) {
        try { $code = ($jsonText | ConvertFrom-Json).code } catch { }
    }
    $results += [pscustomobject]@{ Status = [int]$response.StatusCode; Code = $code }
}
$client.Dispose()

$success = @($results | Where-Object { $_.Status -eq 201 }).Count
$conflict = @($results | Where-Object { $_.Status -eq 409 }).Count
$other = @($results | Where-Object { $_.Status -ne 201 -and $_.Status -ne 409 }).Count
Write-Host "      201 成功: $success 个；409 冲突: $conflict 个；其他: $other 个"

Write-Host '[3/4] 核对最终库存'
$detail = Invoke-Api -Method 'GET' -Path "/api/products/$productId"
$stock = $detail.Data.stock
Write-Host "      商品 id=$productId 当前库存: $stock"

Write-Host '[4/4] 核对本轮新增订单数'
$orders = Invoke-Api -Method 'GET' -Path "/api/orders?buyerPhone=$buyerPhone&page=1&pageSize=50"
$orderCount = [int]$orders.Data.total
Write-Host "      手机号 $buyerPhone 名下订单数: $orderCount"

Write-Host ''
$checks = @(
    @{ Name = '恰好 1 个请求返回 201'; Actual = $success; Expected = 1 },
    @{ Name = "其余 $($Concurrency - 1) 个返回 409"; Actual = $conflict; Expected = $Concurrency - 1 },
    @{ Name = '商品库存为 0'; Actual = $stock; Expected = 0 },
    @{ Name = '恰好新增 1 条订单'; Actual = $orderCount; Expected = 1 }
)

$failed = 0
foreach ($check in $checks) {
    if ($check.Actual -eq $check.Expected) {
        Write-Host ("  PASS  {0,-24} 期望 {1}，实际 {2}" -f $check.Name, $check.Expected, $check.Actual)
    } else {
        $failed++
        Write-Host ("  FAIL  {0,-24} 期望 {1}，实际 {2}" -f $check.Name, $check.Expected, $check.Actual)
    }
}

Write-Host ''
if ($failed -eq 0) {
    Write-Host '结论：不超卖，结果符合 L2-10 要求。'
    exit 0
} else {
    Write-Host "结论：有 $failed 项不符合预期。"
    exit 1
}
