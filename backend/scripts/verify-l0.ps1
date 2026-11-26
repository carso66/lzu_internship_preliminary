# L0 接口真实验收脚本（PowerShell 5.1+ / PowerShell 7）
# 前置：先启动独立文件库服务（不要用 ./data/marketplace）：
#   mvn -DskipTests package
#   java -jar target/marketplace-0.0.1-SNAPSHOT.jar --spring.config.additional-location=file:./config/verify.yaml
# 运行（PowerShell 5.1 按 ANSI 读盘，必须显式以 UTF-8 载入脚本）：
#   powershell -NoProfile -Command "& ([ScriptBlock]::Create([IO.File]::ReadAllText('scripts\verify-l0.ps1',[Text.UTF8Encoding]::new(`$false))))"
# 或 PowerShell 7：pwsh -NoProfile -File scripts/verify-l0.ps1
param(
    [string]$Base = "http://localhost:18080"
)

$script:pass = 0
$script:fail = 0
$results = New-Object System.Collections.ArrayList
$tmp = Join-Path $env:TEMP ("mp-verify-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tmp | Out-Null

function Save-Body([string]$json) {
    $path = Join-Path $tmp ([guid]::NewGuid().ToString("N") + ".json")
    [IO.File]::WriteAllText($path, $json, (New-Object Text.UTF8Encoding($false)))
    return $path
}

# Invoke-Case -Id -Desc -ExpHttp -ExpCode -Method -Path [-Json] [-MustContain]
function Invoke-Case {
    param(
        [string]$Id, [string]$Desc, [string]$ExpHttp, [string]$ExpCode,
        [string]$Method, [string]$Path, [string]$Json, [string]$MustContain
    )
    $curlArgs = @("-s", "-o", (Join-Path $tmp "resp.json"), "-w", "%{http_code}", "-X", $Method, ($Base + $Path))
    if ($PSBoundParameters.ContainsKey('Json') -and -not [string]::IsNullOrEmpty($Json) -and $Json -ne 'null') {
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
    if ($PSBoundParameters.ContainsKey('MustContain') -and -not [string]::IsNullOrEmpty($MustContain)) {
        if ($resp.IndexOf($MustContain, [StringComparison]::Ordinal) -lt 0) {
            $ok = $false
            $reason += "响应未包含 '$MustContain'"
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

$idA = $null
$idB = $null

Write-Host "=== 回归：健康检查 ==="
Invoke-Case -Id "REG-HEALTH" -Desc "GET /api/health" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/health" | Out-Null

Write-Host "=== L0-2 发布商品 ==="
$r = Invoke-Case -Id "L0-2-01" -Desc "正常发布（201）" -ExpHttp 201 -ExpCode 0 -Method POST -Path "/api/products" `
    -Json '{"title":"验收商品A","price":1990,"stock":10,"category":"文具","description":"备注","sellerName":"校园数码铺"}'
$idA = ($r | ConvertFrom-Json).data.id
Invoke-Case -Id "L0-2-02" -Desc "默认状态 on" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products/$idA" -MustContain '"status":"on"' | Out-Null
Invoke-Case -Id "L0-2-03" -Desc "缺少 title" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"price":1990,"stock":1,"category":"c","sellerName":"s"}' -MustContain 'title' | Out-Null
Invoke-Case -Id "L0-2-04" -Desc "缺少 price" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","stock":1,"category":"c","sellerName":"s"}' -MustContain 'price' | Out-Null
Invoke-Case -Id "L0-2-05" -Desc "缺少 stock" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","price":1990,"category":"c","sellerName":"s"}' -MustContain 'stock' | Out-Null
Invoke-Case -Id "L0-2-06" -Desc "缺少 category" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","price":1990,"stock":1,"sellerName":"s"}' -MustContain 'category' | Out-Null
Invoke-Case -Id "L0-2-07" -Desc "缺少 sellerName" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","price":1990,"stock":1,"category":"c"}' -MustContain 'sellerName' | Out-Null
Invoke-Case -Id "L0-2-08" -Desc "空标题" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"  ","price":1990,"stock":1,"category":"c","sellerName":"s"}' -MustContain 'title' | Out-Null
Invoke-Case -Id "L0-2-09" -Desc "title 101 字符超长" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json ('{"title":"' + ("t" * 101) + '","price":1990,"stock":1,"category":"c","sellerName":"s"}') -MustContain 'title' | Out-Null
Invoke-Case -Id "L0-2-10" -Desc "零价格" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","price":0,"stock":1,"category":"c","sellerName":"s"}' -MustContain 'price' | Out-Null
Invoke-Case -Id "L0-2-11" -Desc "负数价格" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","price":-100,"stock":1,"category":"c","sellerName":"s"}' -MustContain 'price' | Out-Null
Invoke-Case -Id "L0-2-12" -Desc "小数价格" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","price":19.9,"stock":1,"category":"c","sellerName":"s"}' -MustContain 'price' | Out-Null
Invoke-Case -Id "L0-2-13" -Desc "字符串价格" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","price":"1990","stock":1,"category":"c","sellerName":"s"}' -MustContain 'price' | Out-Null
Invoke-Case -Id "L0-2-14" -Desc "布尔价格" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","price":true,"stock":1,"category":"c","sellerName":"s"}' -MustContain 'price' | Out-Null
Invoke-Case -Id "L0-2-15" -Desc "价格溢出 BIGINT" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","price":99999999999999999999,"stock":1,"category":"c","sellerName":"s"}' -MustContain 'price' | Out-Null
Invoke-Case -Id "L0-2-16" -Desc "零库存（合法，201）" -ExpHttp 201 -ExpCode 0 -Method POST -Path "/api/products" -Json '{"title":"零库存商品","price":100,"stock":0,"category":"文具","sellerName":"s"}' | Out-Null
Invoke-Case -Id "L0-2-17" -Desc "负数库存" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","price":100,"stock":-1,"category":"c","sellerName":"s"}' -MustContain 'stock' | Out-Null
Invoke-Case -Id "L0-2-18" -Desc "字符串库存" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":"t","price":100,"stock":"10","category":"c","sellerName":"s"}' -MustContain 'stock' | Out-Null
Invoke-Case -Id "L0-2-19" -Desc "非法 JSON" -ExpHttp 400 -ExpCode 40001 -Method POST -Path "/api/products" -Json '{"title":' | Out-Null
Invoke-Case -Id "L0-2-20" -Desc "发布商品B" -ExpHttp 201 -ExpCode 0 -Method POST -Path "/api/products" -Json '{"title":"验收商品B","price":2990,"stock":5,"category":"数码","sellerName":"数码铺"}' | Out-Null
$r = Invoke-Case -Id "L0-2-21" -Desc "发布商品C" -ExpHttp 201 -ExpCode 0 -Method POST -Path "/api/products" -Json '{"title":"验收商品C","price":3990,"stock":2,"category":"数码","sellerName":"数码铺"}'
$idB = ($r | ConvertFrom-Json).data.id

Write-Host "=== L0-3 列表 / 筛选 / 分页 ==="
Invoke-Case -Id "L0-3-01" -Desc "默认分页" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products" -MustContain '"pageSize":10' | Out-Null
Invoke-Case -Id "L0-3-02" -Desc "自定义分页 page=2 pageSize=1" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?page=2&pageSize=1" -MustContain '"page":2' | Out-Null
Invoke-Case -Id "L0-3-03" -Desc "跨页 page=2 pageSize=2" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?page=2&pageSize=2" | Out-Null
Invoke-Case -Id "L0-3-04" -Desc "keyword 中文模糊匹配" -ExpHttp 200 -ExpCode 0 -Method GET -Path ("/api/products?keyword=" + [uri]::EscapeDataString("验收商品")) | Out-Null
Invoke-Case -Id "L0-3-05" -Desc "category 精确匹配" -ExpHttp 200 -ExpCode 0 -Method GET -Path ("/api/products?category=" + [uri]::EscapeDataString("数码")) | Out-Null
Invoke-Case -Id "L0-3-06" -Desc "status=on" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?status=on" | Out-Null
Invoke-Case -Id "L0-3-07" -Desc "status=off" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?status=off" | Out-Null
Invoke-Case -Id "L0-3-08" -Desc "三条件叠加" -ExpHttp 200 -ExpCode 0 -Method GET -Path ("/api/products?keyword=" + [uri]::EscapeDataString("验收商品") + "&category=" + [uri]::EscapeDataString("数码") + "&status=on") | Out-Null
Invoke-Case -Id "L0-3-09" -Desc "无匹配结果返回空数组" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?keyword=zzz-no-match" -MustContain 'items":[]' | Out-Null
Invoke-Case -Id "L0-3-10" -Desc "超出有效页数" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?page=999&pageSize=10" -MustContain 'items":[]' | Out-Null
Invoke-Case -Id "L0-3-11" -Desc "pageSize=1" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?pageSize=1" | Out-Null
Invoke-Case -Id "L0-3-12" -Desc "pageSize=50" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?pageSize=50" | Out-Null
Invoke-Case -Id "L0-3-13" -Desc "pageSize=0" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/products?pageSize=0" -MustContain 'pageSize' | Out-Null
Invoke-Case -Id "L0-3-14" -Desc "pageSize=51" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/products?pageSize=51" -MustContain 'pageSize' | Out-Null
Invoke-Case -Id "L0-3-15" -Desc "page=0" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/products?page=0" -MustContain 'page' | Out-Null
Invoke-Case -Id "L0-3-16" -Desc "page=abc" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/products?page=abc" -MustContain 'page' | Out-Null
Invoke-Case -Id "L0-3-17" -Desc "非法 status=ON" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/products?status=ON" -MustContain 'status' | Out-Null
Invoke-Case -Id "L0-3-18" -Desc "SQL 注入 keyword" -ExpHttp 200 -ExpCode 0 -Method GET -Path ("/api/products?keyword=" + [uri]::EscapeDataString("' OR '1'='1")) -MustContain '"total":0' | Out-Null
Invoke-Case -Id "L0-3-19" -Desc "LIKE 特殊字符 keyword=%" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?keyword=%25" -MustContain '"total":0' | Out-Null
Invoke-Case -Id "L0-3-20" -Desc "偏移量 page=10000000 可查询" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?page=10000000&pageSize=50" | Out-Null
Invoke-Case -Id "L0-3-21" -Desc "偏移量 page=10000001 拒绝" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/products?page=10000001" -MustContain 'page' | Out-Null
Invoke-Case -Id "L0-3-22" -Desc "非法 URL 编码 → 400 而非 500" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/products?keyword=%B2%E2%CA%D4" | Out-Null

Write-Host "=== L0-4 详情 ==="
Invoke-Case -Id "L0-4-01" -Desc "正常商品" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products/$idA" | Out-Null
Invoke-Case -Id "L0-4-02" -Desc "不存在商品 → 404/40401" -ExpHttp 404 -ExpCode 40401 -Method GET -Path "/api/products/999999" | Out-Null
Invoke-Case -Id "L0-4-03" -Desc "非法 ID（abc）" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/products/abc" -MustContain 'id' | Out-Null
Invoke-Case -Id "L0-4-04" -Desc "非法 ID（小数）" -ExpHttp 400 -ExpCode 40001 -Method GET -Path "/api/products/1.5" -MustContain 'id' | Out-Null

Write-Host "=== L0-5 上下架 ==="
Invoke-Case -Id "L0-5-01" -Desc "下架" -ExpHttp 200 -ExpCode 0 -Method PATCH -Path "/api/products/$idA/status" -Json '{"status":"off"}' -MustContain '"status":"off"' | Out-Null
Invoke-Case -Id "L0-5-02" -Desc "下架后详情同步" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products/$idA" -MustContain '"status":"off"' | Out-Null
Invoke-Case -Id "L0-5-03" -Desc "下架后列表 status=off 可见" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?status=off" -MustContain "`"id`":$idA" | Out-Null
Invoke-Case -Id "L0-5-04" -Desc "重新上架" -ExpHttp 200 -ExpCode 0 -Method PATCH -Path "/api/products/$idA/status" -Json '{"status":"on"}' -MustContain '"status":"on"' | Out-Null
Invoke-Case -Id "L0-5-05" -Desc "重复设置相同状态（幂等）" -ExpHttp 200 -ExpCode 0 -Method PATCH -Path "/api/products/$idA/status" -Json '{"status":"on"}' | Out-Null
Invoke-Case -Id "L0-5-06" -Desc "非法状态 sold" -ExpHttp 400 -ExpCode 40001 -Method PATCH -Path "/api/products/$idA/status" -Json '{"status":"sold"}' -MustContain 'status' | Out-Null
Invoke-Case -Id "L0-5-07" -Desc "缺少 status" -ExpHttp 400 -ExpCode 40001 -Method PATCH -Path "/api/products/$idA/status" -Json '{}' -MustContain 'status' | Out-Null
Invoke-Case -Id "L0-5-08" -Desc "不存在商品 → 404/40401" -ExpHttp 404 -ExpCode 40401 -Method PATCH -Path "/api/products/999999/status" -Json '{"status":"off"}' | Out-Null
Invoke-Case -Id "L0-5-09" -Desc "上架后列表同步 status=on" -ExpHttp 200 -ExpCode 0 -Method GET -Path "/api/products?status=on" -MustContain "`"id`":$idA" | Out-Null

Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue

Write-Host ""
$results | Format-Table -Property Id, Case, Expected, Status, Note -AutoSize -Wrap | Out-String -Width 220 | Write-Host
$total = $script:pass + $script:fail
Write-Host ("TOTAL={0} PASS={1} FAIL={2}" -f $total, $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
