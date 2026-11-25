param([string]$BaseUrl = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'
$originalConsoleEncoding = [Console]::OutputEncoding
$originalPipeEncoding = $OutputEncoding
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$BaseUrl = $BaseUrl.TrimEnd('/')
$script:Passed = 0
$script:CurrentCase = 'setup'
$script:TestPhone = 'L1TEST' + [guid]::NewGuid().ToString('N').Substring(0, 12)

function Assert-That {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Send-Api {
    param([string]$Method, [string]$Path, [string]$Body)
    $curlArgs = @('--silent', '--show-error', '--max-time', '15',
        '--request', $Method, '--url', ($BaseUrl + $Path),
        '--header', 'Accept: application/json',
        '--write-out', '\n__HTTP_META__:%{http_code}|%{content_type}')
    if ($Method -ne 'GET') {
        $curlArgs += @('--header', 'Content-Type: application/json', '--data-binary', '@-')
        $raw = ($Body | & curl.exe @curlArgs) -join "`n"
    } else {
        $raw = (& curl.exe @curlArgs) -join "`n"
    }
    if ($LASTEXITCODE -ne 0) { throw "curl failed ($LASTEXITCODE). Check service availability." }
    $match = [regex]::Match($raw, '(?s)^(.*)\r?\n__HTTP_META__:(\d{3})\|([^\r\n]*)\s*$')
    if (-not $match.Success) { throw "Cannot parse response: $raw" }
    $textBody = $match.Groups[1].Value.Trim()
    try { $json = $textBody | ConvertFrom-Json -ErrorAction Stop }
    catch { throw "Response is not JSON: $textBody" }
    return [pscustomobject]@{ Status=[int]$match.Groups[2].Value; ContentType=$match.Groups[3].Value; Json=$json; Text=$textBody }
}

function Assert-Response {
    param($Response, [int]$Status, [int]$Code)
    Assert-That ($Response.Status -eq $Status) "Expected HTTP $Status; actual $($Response.Status). $($Response.Text)"
    Assert-That ($Response.ContentType -match '^application/json(?:;|$)') "Unexpected Content-Type: $($Response.ContentType)"
    foreach ($name in @('code', 'message', 'data')) {
        Assert-That ($Response.Json.PSObject.Properties.Name -contains $name) "Missing field: $name"
    }
    Assert-That ($Response.Json.code -eq $Code) "Expected code $Code. $($Response.Text)"
    Assert-That (-not [string]::IsNullOrWhiteSpace([string]$Response.Json.message)) 'Missing message'
    if ($Code -ne 0) { Assert-That ($null -eq $Response.Json.data) "Error data should be null. $($Response.Text)" }
}

function Check {
    param([string]$Name, [scriptblock]$Action)
    $script:CurrentCase = $Name
    & $Action
    $script:Passed++
    Write-Host "PASS: $Name" -ForegroundColor Green
}

function Order-Body {
    param([long]$ProductId, [object]$Quantity, [string]$BuyerPhone = $script:TestPhone)
    return ([ordered]@{productId=$ProductId;quantity=$Quantity;buyerName='Test buyer';buyerPhone=$BuyerPhone;address='Test address'} | ConvertTo-Json -Compress)
}

function Get-Stock {
    param([long]$ProductId)
    $response = Send-Api 'GET' "/api/products/$ProductId"
    Assert-Response $response 200 0
    return [int]$response.Json.data.stock
}

function Get-OrderCount {
    $phone = [uri]::EscapeDataString($script:TestPhone)
    $response = Send-Api 'GET' "/api/orders?buyerPhone=$phone&page=1&pageSize=1"
    Assert-Response $response 200 0
    Assert-That ($null -ne $response.Json.data.total) 'Missing order total'
    return [long]$response.Json.data.total
}

function Reject-WithoutMutation {
    param([string]$Body, [int]$Status, [int]$Code, [string]$FieldName)
    $stocks = @((Get-Stock $script:ProductId), (Get-Stock $script:OffProductId), (Get-Stock $script:OverflowProductId))
    $count = Get-OrderCount
    $response = Send-Api 'POST' '/api/orders' $Body
    Assert-Response $response $Status $Code
    if ($FieldName) {
        Assert-That ([string]$response.Json.message -match [regex]::Escape($FieldName)) "Expected field '$FieldName'. $($response.Text)"
    }
    Assert-That ((Get-Stock $script:ProductId) -eq $stocks[0]) 'Main product stock changed after rejected order'
    Assert-That ((Get-Stock $script:OffProductId) -eq $stocks[1]) 'Off product stock changed after rejected order'
    Assert-That ((Get-Stock $script:OverflowProductId) -eq $stocks[2]) 'Overflow product stock changed after rejected order'
    Assert-That ((Get-OrderCount) -eq $count) 'Rejected request created an order'
}

try {
    Get-Command curl.exe -ErrorAction Stop | Out-Null
    Write-Host "Target: $BaseUrl"
    Write-Host "Test buyer identifier: $script:TestPhone"
    Write-Host 'Creates 3 products and 3 successful orders. Does not delete existing data.'

    Check 'health' { Assert-Response (Send-Api 'GET' '/api/health') 200 0 }

    $script:CurrentCase = 'prepare test products'
    $main = Send-Api 'POST' '/api/products' '{"title":"L1 test product","price":4990,"stock":5,"category":"Tests","sellerName":"Test seller"}'
    Assert-Response $main 201 0
    $script:ProductId = [long]$main.Json.data.id
    $off = Send-Api 'POST' '/api/products' '{"title":"L1 off product","price":1990,"stock":2,"category":"Tests","sellerName":"Test seller"}'
    Assert-Response $off 201 0
    $script:OffProductId = [long]$off.Json.data.id
    Assert-Response (Send-Api 'PATCH' "/api/products/$script:OffProductId/status" '{"status":"off"}') 200 0
    $overflow = Send-Api 'POST' '/api/products' '{"title":"L1 overflow product","price":9223372036854775807,"stock":2,"category":"Tests","sellerName":"Test seller"}'
    Assert-Response $overflow 201 0
    $script:OverflowProductId = [long]$overflow.Json.data.id
    Write-Host "Products: main=$script:ProductId off=$script:OffProductId overflow=$script:OverflowProductId"

    Check 'normal order: server price, normalized phone, stock deduction' {
        $response = Send-Api 'POST' '/api/orders' (Order-Body $script:ProductId 2 "  $script:TestPhone  ")
        Assert-Response $response 201 0
        $data = $response.Json.data
        Assert-That ($data.unitPrice -eq 4990 -and $data.totalAmount -eq 9980 -and $data.quantity -eq 2) "Wrong order amount. $($response.Text)"
        Assert-That ($data.status -ceq 'created' -and $null -eq $data.paidAt) 'Wrong initial order state'
        Assert-That ($data.buyerPhone -ceq $script:TestPhone) 'Phone was not normalized'
        Assert-That ([string]$data.orderNo -cmatch '^SO[0-9]{8}[A-F0-9]{32}$') 'Unexpected order number format'
        Assert-That ((Get-Stock $script:ProductId) -eq 3) 'Expected stock 3'
        Assert-That ((Get-OrderCount) -eq 1) 'Expected exactly 1 test order'
        $script:FirstOrderNo = [string]$data.orderNo
        Write-Host "  orderNo=$script:FirstOrderNo"
    }

    Check 'order detail' {
        $response = Send-Api 'GET' "/api/orders/$script:FirstOrderNo"
        Assert-Response $response 200 0
        Assert-That ($response.Json.data.orderNo -ceq $script:FirstOrderNo) 'Wrong order detail'
        Assert-That ($response.Json.data.totalAmount -eq 9980) 'Wrong detail amount'
    }

    foreach ($case in @(
        @{Name='quantity zero';Value=0},
        @{Name='quantity negative';Value=-1},
        @{Name='quantity string';Value='2'},
        @{Name='quantity decimal';Value=1.5},
        @{Name='quantity null';Value=$null}
    )) {
        Check $case.Name { Reject-WithoutMutation (Order-Body $script:ProductId $case.Value) 400 40001 'quantity' }
    }
    Check 'insufficient stock' { Reject-WithoutMutation (Order-Body $script:ProductId 99) 409 40901 }
    Check 'off product' { Reject-WithoutMutation (Order-Body $script:OffProductId 1) 409 40902 }
    Check 'missing product' { Reject-WithoutMutation (Order-Body ([long]::MaxValue) 1) 404 40401 }
    Check 'amount overflow' { Reject-WithoutMutation (Order-Body $script:OverflowProductId 2) 400 40001 }

    Check 'missing order' { Assert-Response (Send-Api 'GET' '/api/orders/NO-SUCH-L1-TEST-ORDER') 404 40401 }
    Check 'page zero' { Assert-Response (Send-Api 'GET' "/api/orders?buyerPhone=$script:TestPhone&page=0") 400 40001 }
    Check 'pageSize too large' { Assert-Response (Send-Api 'GET' "/api/orders?buyerPhone=$script:TestPhone&pageSize=51") 400 40001 }
    Check 'missing buyerPhone' { Assert-Response (Send-Api 'GET' '/api/orders') 400 40001 }

    Check 'second order' {
        $response = Send-Api 'POST' '/api/orders' (Order-Body $script:ProductId 1)
        Assert-Response $response 201 0
        $script:SecondOrderNo = [string]$response.Json.data.orderNo
        Assert-That ($script:SecondOrderNo -cne $script:FirstOrderNo) 'Duplicate order number'
        Assert-That ((Get-Stock $script:ProductId) -eq 2) 'Expected stock 2'
    }
    Check 'phone normalization and first page' {
        $phone = [uri]::EscapeDataString("  $script:TestPhone  ")
        $response = Send-Api 'GET' "/api/orders?buyerPhone=$phone&page=1&pageSize=1"
        Assert-Response $response 200 0
        $data = $response.Json.data
        Assert-That ($data.total -eq 2 -and $data.page -eq 1 -and $data.pageSize -eq 1) 'Wrong page metadata'
        Assert-That (@($data.items).Count -eq 1) 'Expected one page item'
        Assert-That ($data.items[0].orderNo -ceq $script:SecondOrderNo) 'Newest order should be first'
    }
    Check 'second page' {
        $response = Send-Api 'GET' "/api/orders?buyerPhone=$script:TestPhone&page=2&pageSize=1"
        Assert-Response $response 200 0
        Assert-That ($response.Json.data.total -eq 2) 'Wrong filtered total'
        Assert-That ($response.Json.data.items[0].orderNo -ceq $script:FirstOrderNo) 'Wrong second page'
    }
    Check 'page beyond end' {
        $response = Send-Api 'GET' "/api/orders?buyerPhone=$script:TestPhone&page=99&pageSize=1"
        Assert-Response $response 200 0
        Assert-That ($response.Json.data.total -eq 2 -and @($response.Json.data.items).Count -eq 0) 'Expected total 2 and empty items'
    }
    Check 'empty filtered list' {
        $response = Send-Api 'GET' "/api/orders?buyerPhone=$($script:TestPhone)X"
        Assert-Response $response 200 0
        Assert-That ($response.Json.data.total -eq 0 -and @($response.Json.data.items).Count -eq 0) 'Expected an empty list'
    }
    Check 'quantity equals remaining stock' {
        $response = Send-Api 'POST' '/api/orders' (Order-Body $script:ProductId 2)
        Assert-Response $response 201 0
        Assert-That ((Get-Stock $script:ProductId) -eq 0) 'Expected stock 0'
        Assert-That ((Get-OrderCount) -eq 3) 'Expected exactly 3 test orders'
    }

    Write-Host "RESULT: $script:Passed passed, 0 failed." -ForegroundColor Green
    Write-Host 'Database INSERT failure rollback and concurrent orders are verified by OrderIntegrationTest (run mvnw.cmd test).'
} catch {
    Write-Host "FAIL: $script:CurrentCase" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Yellow
    Write-Host "RESULT: $script:Passed passed, 1 failed; remaining checks were not run."
    exit 1
} finally {
    $OutputEncoding = $originalPipeEncoding
    [Console]::OutputEncoding = $originalConsoleEncoding
}
