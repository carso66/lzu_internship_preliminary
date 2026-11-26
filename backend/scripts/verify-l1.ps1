# L1 订单接口真实验收/冒烟脚本（PowerShell 5.1+）
# 前置：先启动独立文件库服务（禁止使用 ./data/marketplace）：
#   mvn -DskipTests package
#   java -jar target/marketplace-0.0.1-SNAPSHOT.jar --spring.config.additional-location=file:./config/verify.yaml
# 运行（PowerShell 5.1 需显式以 UTF-8 载入脚本，同 verify-l0.ps1）：
#   powershell -NoProfile -Command "& ([ScriptBlock]::Create([IO.File]::ReadAllText('scripts\verify-l1.ps1',[Text.UTF8Encoding]::new(`$false))))"
# 或 PowerShell 7：pwsh -NoProfile -File scripts/verify-l1.ps1
# 说明：订单插入失败的回滚场景无法在真实 HTTP 下确定性地注入（无删除/改价接口），
#       由 src/test 中 L1TransactionRollbackTest 覆盖，本脚本聚焦可 HTTP 复现的路径。
param(
    [string]$Base = "http://localhost:18080"
)

# 每次运行唯一的买家手机号（日期+时刻构成，纯数字），使脚本可重复执行、不依赖库内历史数据
$smokePhone = "139" + (Get-Date -Format "MMddHHmmss")
$otherPhone = "137" + (Get-Date -Format "MMddHHmmss")

$script:pass = 0
$script:fail = 0
$results = New-Object System.Collections.ArrayList
$tmp = Join-Path $env:TEMP ("mp-verify-l1-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tmp | Out-Null

function Save-Body([string]$json) {
    $path = Join-Path $tmp ([guid]::NewGuid().ToString("N") + ".json")
    [IO.File]::WriteAllText($path, $json, (New-Object Text.UTF8Encoding($false)))
    return $path
}

# Invoke-Case -Id -Desc -ExpHttp -ExpCode -Method -Path [-Json] [-MustContain[]] [-MustMatch]
function Invoke-Case {
    param(
        [string]$Id, [string]$Desc, [string]$ExpHttp, [string]$ExpCode,
        [string]$Method, [string]$Path, [string]$Json,
        [string[]]$MustContain, [string]$MustMatch
    )
    $curlArgs = @("-s", "-o", (Join-Path $tmp "resp.json"), "-w", "%{http_code}", "-X", $Method, ($Base + $Path))
    if ($PSBoundParameters.ContainsKey('Json') -and -not [string]::IsNullOrEmpty($Json)) {
        $curlArgs += @("-H", "Content-Type: application/json", "--data-binary", "@$(Save-Body $Json)")
    }
    $http = & curl.exe @curlArgs
    $resp = Get-Content (Join-Path $tmp "resp.json") -Raw -Encoding UTF8
    if (-not $resp) { $resp = "" }

    $ok = $true
    $reason = @()
    if ("$http" -ne "$ExpHttp") { $ok = $false; $reason += "HTTP $http != $ExpHttp" }
    $code = $null
    try { $code = ($resp | ConvertFrom-Json).code } catch { $ok = $false; $reason += "响应不是合法 JSON" }
    if ($null -ne $code -and "$code" -ne "$ExpCode") { $ok = $false; $reason += "code $code != $ExpCode" }
    foreach ($field in @('code', 'message', 'data')) {
        if ($resp -notmatch ('"' + $field + '"\s*:')) { $ok = $false; $reason += "缺少顶层字段 $field" }
    }
    if ($PSBoundParameters.ContainsKey('MustContain') -and $null -ne $MustContain) {
        foreach ($needle in $MustContain) {
            if ($resp.IndexOf($needle, [StringComparison]::Ordinal) -lt 0) {
                $ok = $false; $reason += "响应未包含 '$needle'"
            }
        }
    }
    $matchSubject = $null
    if ($PSBoundParameters.ContainsKey('MustMatch') -and -not [string]::IsNullOrEmpty($MustMatch)) {
        try { $matchSubject = ($resp | ConvertFrom-Json).data.orderNo } catch {}
        if (-not $matchSubject -or $matchSubject -notmatch $MustMatch) {
            $ok = $false; $reason += "orderNo '$matchSubject' 不匹配 $MustMatch"
        }
    }

    $status = if ($ok) { "PASS" } else { "FAIL" }
    if ($ok) { $script:pass++ } else { $script:fail++ }
    $short = if ($resp.Length -gt 140) { $resp.Substring(0, 140) + "..." } else { $resp }
    [void]$results.Add([pscustomobject]@{
            Id       = $Id
            Case     = $Desc
            Expected = "HTTP $ExpHttp / code $ExpCode"
            Actual   = "HTTP $http / " + $short
            Status   = $status
            Note     = ($reason -join "; ")
        })
    Write-Host ("[{0}] {1} {2} -> HTTP {3}" -f $status, $Id, $Desc, $http)
    return $resp
}

# ---------------- 准备：发布专用冒烟商品（data-verify 中的既有数据不动） ----------------
$r = Invoke-Case -Id "PRE-01" -Desc "发布冒烟商品（stock=6, price=12345 分）" -ExpHttp 201 -ExpCode 0 -Method POST -Path "/api/products" `
    -Json '{"title":"L1冒烟专用商品","price":12345,"stock":6,"category":"冒烟","description":"L1","sellerName":"冒烟店铺"}'
$productId = ($r | ConvertFrom-Json).data.id
Write-Host "  -> productId = $productId"

Write-Host "=== L1-6 下单 ==="
$orderJson = '{"productId":' + $productId + ',"quantity":2,"buyerName":"张三","buyerPhone":"' + $smokePhone + '","address":"兰州市城关区天水南路222号"}'
$r = Invoke-Case -Id "L1-6-01" -Desc "正常下单 qty=2（服务端算价 12345*2=24690）" -ExpHttp 201 -ExpCode 0 -Method POST -Path "/api/orders" `
    -Json $orderJson -MustContain '"totalAmount":24690' -MustMatch '^SO\d{20}$'
$orderNo = ($r | ConvertFrom-Json).data.orderNo
Write-Host "  -> orderNo = $orderNo"

Invoke-Case -Id "L1-6-02" -Desc "库存扣减 6→4 且落库" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products/$productId" -MustContain '"stock":4' | Out-Null

Invoke-Case -Id "L1-6-03" -Desc "库存不足 qty=99 → 409/40901" -ExpHttp 409 -ExpCode 40901 -Method POST -Path "/api/orders" `
    -Json ('{"productId":' + $productId + ',"quantity":99,"buyerName":"张三","buyerPhone":"13800001111","address":"a"}') `
    -MustContain '库存不足' | Out-Null

Invoke-Case -Id "L1-6-04" -Desc "失败后库存不变（仍为 4，未幻扣）" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products/$productId" -MustContain '"stock":4' | Out-Null

$invalidCases = @(
    @{ Id = "L1-6-05"; Desc = "quantity=0 → 400"; Json = '{"productId":PID,"quantity":0,"buyerName":"张三","buyerPhone":"PHONE","address":"a"}'; Must = 'quantity' },
    @{ Id = "L1-6-06"; Desc = "quantity=-1 → 400"; Json = '{"productId":PID,"quantity":-1,"buyerName":"张三","buyerPhone":"PHONE","address":"a"}'; Must = 'quantity' },
    @{ Id = "L1-6-07"; Desc = 'quantity 字符串 "3" → 400'; Json = '{"productId":PID,"quantity":"3","buyerName":"张三","buyerPhone":"PHONE","address":"a"}'; Must = 'quantity' },
    @{ Id = "L1-6-08"; Desc = "quantity=1.5 → 400"; Json = '{"productId":PID,"quantity":1.5,"buyerName":"张三","buyerPhone":"PHONE","address":"a"}'; Must = 'quantity' },
    @{ Id = "L1-6-09"; Desc = "缺 quantity → 400"; Json = '{"productId":PID,"buyerName":"张三","buyerPhone":"PHONE","address":"a"}'; Must = 'quantity' },
    @{ Id = "L1-6-10"; Desc = "缺 buyerName → 400"; Json = '{"productId":PID,"quantity":1,"buyerPhone":"PHONE","address":"a"}'; Must = 'buyerName' },
    @{ Id = "L1-6-11"; Desc = "缺 buyerPhone → 400"; Json = '{"productId":PID,"quantity":1,"buyerName":"张三","address":"a"}'; Must = 'buyerPhone' },
    @{ Id = "L1-6-12"; Desc = "缺 address → 400"; Json = '{"productId":PID,"quantity":1,"buyerName":"张三","buyerPhone":"PHONE"}'; Must = 'address' },
    @{ Id = "L1-6-13"; Desc = "buyerPhone 含非数字 → 400"; Json = '{"productId":PID,"quantity":1,"buyerName":"张三","buyerPhone":"138-0000-0000","address":"a"}'; Must = 'buyerPhone' }
)
foreach ($c in $invalidCases) {
    $json = $c.Json.Replace('PID', "$productId").Replace('PHONE', $smokePhone)
    Invoke-Case -Id $c.Id -Desc $c.Desc -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/orders" -Json $json -MustContain $c.Must | Out-Null
}

Invoke-Case -Id "L1-6-14" -Desc "productId 不存在 → 404/40401" -ExpHttp 404 -ExpCode 40401 -Method POST -Path "/api/orders" `
    -Json ('{"productId":999999,"quantity":1,"buyerName":"张三","buyerPhone":"' + $smokePhone + '","address":"a"}') | Out-Null

Invoke-Case -Id "L1-6-15" -Desc "下架商品" -ExpHttp 200 -ExpCode 0 -Method PATCH -Path "/api/products/$productId/status" -Json '{"status":"off"}' | Out-Null
Invoke-Case -Id "L1-6-16" -Desc "下架商品下单 → 409/40902" -ExpHttp 409 -ExpCode 40902 -Method POST -Path "/api/orders" `
    -Json ('{"productId":' + $productId + ',"quantity":1,"buyerName":"张三","buyerPhone":"' + $smokePhone + '","address":"a"}') `
    -MustContain '已下架' | Out-Null
Invoke-Case -Id "L1-6-17" -Desc "商品复位上架" -ExpHttp 200 -ExpCode 0 -Method PATCH -Path "/api/products/$productId/status" -Json '{"status":"on"}' | Out-Null
Invoke-Case -Id "L1-6-18" -Desc "全部失败单未幻扣库存（仍为 4）" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products/$productId" -MustContain '"stock":4' | Out-Null

Write-Host "=== L1-6 边界补充 ==="
Invoke-Case -Id "L1-6-19" -Desc "quantity 溢出 2147483648 → 400" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/orders" `
    -Json ('{"productId":' + $productId + ',"quantity":2147483648,"buyerName":"张三","buyerPhone":"' + $smokePhone + '","address":"a"}') `
    -MustContain 'quantity' | Out-Null
Invoke-Case -Id "L1-6-20" -Desc "quantity 布尔 true → 400" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/orders" `
    -Json ('{"productId":' + $productId + ',"quantity":true,"buyerName":"张三","buyerPhone":"' + $smokePhone + '","address":"a"}') `
    -MustContain 'quantity' | Out-Null
Invoke-Case -Id "L1-6-21" -Desc "缺 productId → 400" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/orders" `
    -Json ('{"quantity":1,"buyerName":"张三","buyerPhone":"' + $smokePhone + '","address":"a"}') -MustContain 'productId' | Out-Null
Invoke-Case -Id "L1-6-22" -Desc "buyerName 超 64 字 → 400" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/orders" `
    -Json ('{"productId":' + $productId + ',"quantity":1,"buyerName":"' + ('名' * 65) + '","buyerPhone":"' + $smokePhone + '","address":"a"}') `
    -MustContain 'buyerName' | Out-Null
Invoke-Case -Id "L1-6-23" -Desc "address 超 200 字 → 400" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/orders" `
    -Json ('{"productId":' + $productId + ',"quantity":1,"buyerName":"张三","buyerPhone":"' + $smokePhone + '","address":"' + ('址' * 201) + '"}') `
    -MustContain 'address' | Out-Null
Invoke-Case -Id "L1-6-24" -Desc "客户端传 unitPrice → 400" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/orders" `
    -Json ('{"productId":' + $productId + ',"quantity":1,"buyerName":"张三","buyerPhone":"' + $smokePhone + '","address":"a","unitPrice":1}') `
    -MustContain 'unitPrice' | Out-Null
Invoke-Case -Id "L1-6-25" -Desc "边界补充后库存仍为 4（全部失败未扣减）" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products/$productId" -MustContain '"stock":4' | Out-Null
Invoke-Case -Id "L1-6-26" -Desc "买空剩余库存 4 件 → 201，库存归 0" -ExpHttp 201 -ExpCode 0 -Method POST -Path "/api/orders" `
    -Json ('{"productId":' + $productId + ',"quantity":4,"buyerName":"张三","buyerPhone":"' + $smokePhone + '","address":"a"}') `
    -MustContain '"totalAmount":49380' | Out-Null
Invoke-Case -Id "L1-6-27" -Desc "库存已为 0 再下单 → 409/40901" -ExpHttp 409 -ExpCode 40901 -Method POST -Path "/api/orders" `
    -Json ('{"productId":' + $productId + ',"quantity":1,"buyerName":"张三","buyerPhone":"' + $smokePhone + '","address":"a"}') | Out-Null

Write-Host "=== L1-7 订单详情 ==="
Invoke-Case -Id "L1-7-01" -Desc "按订单号查详情（含快照与金额）" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/orders/$orderNo" `
    -MustContain @('"productTitle":"L1冒烟专用商品"', '"totalAmount":24690') | Out-Null
Invoke-Case -Id "L1-7-02" -Desc "详情含 buyerName 与 created 状态" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/orders/$orderNo" `
    -MustContain @('"buyerName":"张三"', '"status":"created"') | Out-Null
Invoke-Case -Id "L1-7-03" -Desc "订单号不存在 → 404/40401" -ExpHttp 404 -ExpCode 40401 -Method GET -Path "/api/orders/SO99999999999999999999" | Out-Null

Write-Host "=== L1-8 订单列表 ==="
Invoke-Case -Id "L1-8-01" -Desc "按手机号查列表（total=2：qty=2 与买空 4 各一单，含 orderNo）" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/orders?buyerPhone=$smokePhone" `
    -MustContain @('"total":2', $orderNo) | Out-Null
Invoke-Case -Id "L1-8-02" -Desc "其他手机号 → 空列表" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/orders?buyerPhone=$otherPhone" `
    -MustContain '"total":0' | Out-Null
Invoke-Case -Id "L1-8-03" -Desc "缺 buyerPhone → 400/40001" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/orders?page=1&pageSize=10" -MustContain 'buyerPhone' | Out-Null
Invoke-Case -Id "L1-8-04" -Desc "pageSize=51 → 400/40001" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/orders?buyerPhone=$smokePhone&pageSize=51" -MustContain 'pageSize' | Out-Null
Invoke-Case -Id "L1-8-05" -Desc "page=abc → 400/40001" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/orders?buyerPhone=$smokePhone&page=abc" -MustContain 'page' | Out-Null
Invoke-Case -Id "L1-8-06" -Desc "buyerPhone 超 20 位 → 400/40001" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/orders?buyerPhone=123456789012345678901" -MustContain 'buyerPhone' | Out-Null
Invoke-Case -Id "L1-8-07" -Desc "分页 page=1 pageSize=1 正常" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/orders?buyerPhone=$smokePhone&page=1&pageSize=1" `
    -MustContain '"pageSize":1' | Out-Null
Invoke-Case -Id "L1-8-08" -Desc "buyerPhone 非数字（与下单同样校验）→ 400" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/orders?buyerPhone=138-0000-0000" -MustContain 'buyerPhone' | Out-Null
Invoke-Case -Id "L1-8-09" -Desc "SQL 注入 buyerPhone → 400（纯数字校验拦截）" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/orders?buyerPhone=13800138000%27%20OR%20%271%27%3D%271" | Out-Null
Invoke-Case -Id "L1-8-10" -Desc "page=0 → 400" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/orders?buyerPhone=$smokePhone&page=0" -MustContain 'page' | Out-Null
Invoke-Case -Id "L1-8-11" -Desc "超出有效页数 → total 仍为 2、items 空" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/orders?buyerPhone=$smokePhone&page=99&pageSize=10" `
    -MustContain @('"total":2', '"items":[]') | Out-Null

Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue

Write-Host ""
$results | Format-Table -Property Id, Case, Expected, Status, Note -AutoSize -Wrap | Out-String -Width 220 | Write-Host
$total = $script:pass + $script:fail
Write-Host ("TOTAL={0} PASS={1} FAIL={2}" -f $total, $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }

