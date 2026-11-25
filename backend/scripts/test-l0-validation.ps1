param(
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
$originalConsoleEncoding = [Console]::OutputEncoding
$originalPipeEncoding = $OutputEncoding
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$BaseUrl = $BaseUrl.TrimEnd('/')
$script:passedCount = 0
$script:failedCount = 0

function Assert-Condition {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Invoke-TestRequest {
    param([string]$Method, [string]$Path, [string]$Body)

    $curlArgs = @(
        '--silent', '--show-error', '--max-time', '15',
        '--request', $Method, '--url', ($BaseUrl + $Path),
        '--header', 'Accept: application/json',
        '--write-out', '\n__HTTP_META__:%{http_code}|%{content_type}'
    )

    if ($Method -eq 'POST') {
        $curlArgs += @('--header', 'Content-Type: application/json', '--data-binary', '@-')
        $raw = ($Body | & curl.exe @curlArgs) -join "`n"
    } else {
        $raw = (& curl.exe @curlArgs) -join "`n"
    }

    if ($LASTEXITCODE -ne 0) {
        throw "curl failed (exit $LASTEXITCODE). Check that the service is running."
    }

    $match = [regex]::Match($raw, '(?s)^(.*)\r?\n__HTTP_META__:(\d{3})\|([^\r\n]*)\s*$')
    if (-not $match.Success) { throw "Cannot parse curl response: $raw" }

    $textBody = $match.Groups[1].Value.Trim()
    try {
        $json = $textBody | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw "Response is not valid JSON: $textBody"
    }

    return [pscustomobject]@{
        Status = [int]$match.Groups[2].Value
        ContentType = $match.Groups[3].Value
        Json = $json
        Text = $textBody
    }
}

function Assert-Response {
    param($Response, [int]$Status, [int]$Code, [string]$Message)

    Assert-Condition ($Response.Status -eq $Status) "Expected HTTP $Status; actual $($Response.Status). Body: $($Response.Text)"
    Assert-Condition ($Response.ContentType -match '^application/json(?:;|$)') "Unexpected Content-Type: $($Response.ContentType)"

    foreach ($field in @('code', 'message', 'data')) {
        Assert-Condition ($Response.Json.PSObject.Properties.Name -contains $field) "Missing response field: $field"
    }

    Assert-Condition ($Response.Json.code -eq $Code) "Expected code $Code. Body: $($Response.Text)"
    Assert-Condition ($Response.Json.message -ceq $Message) "Expected message '$Message'. Body: $($Response.Text)"

    if ($Code -ne 0) {
        Assert-Condition ($null -eq $Response.Json.data) "Error response data should be null. Body: $($Response.Text)"
    }
}

function Get-ProductCount {
    $response = Invoke-TestRequest -Method 'GET' -Path '/api/products?page=1&pageSize=1'
    Assert-Response -Response $response -Status 200 -Code 0 -Message 'ok'
    Assert-Condition ($null -ne $response.Json.data.total) 'Product list does not contain data.total.'
    return [long]$response.Json.data.total
}

function Invoke-Check {
    param([string]$Name, [scriptblock]$Action)
    try {
        & $Action
        $script:passedCount++
        Write-Host "PASS: $Name" -ForegroundColor Green
    } catch {
        $script:failedCount++
        Write-Host "FAIL: $Name" -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Yellow
    }
}

try {
    Get-Command curl.exe -ErrorAction Stop | Out-Null
    Write-Host "Target: $BaseUrl"
    Write-Host 'This script creates one valid test product. Keep other clients idle during the test.'
    $initialCount = Get-ProductCount
    Write-Host "Initial product count: $initialCount"

    $cases = @(
        @{ Name='stock as string'; Method='POST'; Path='/api/products'; Message='stock 字段类型错误'; Body='{"title":"Notebook","price":1990,"stock":"10","category":"Stationery","sellerName":"Test seller"}' },
        @{ Name='price as decimal'; Method='POST'; Path='/api/products'; Message='price 字段类型错误'; Body='{"title":"Notebook","price":19.9,"stock":10,"category":"Stationery","sellerName":"Test seller"}' },
        @{ Name='malformed JSON'; Method='POST'; Path='/api/products'; Message='请求体格式或字段类型错误'; Body='{"title":"Notebook","price":1990,"stock":10,' },
        @{ Name='invalid path parameter'; Method='GET'; Path='/api/products/abc'; Message='参数类型错误：id'; Body=$null },
        @{ Name='invalid page'; Method='GET'; Path='/api/products?page=0&pageSize=10'; Message='page 必须大于等于 1'; Body=$null }
    )

    foreach ($case in $cases) {
        Invoke-Check -Name $case.Name -Action {
            $response = Invoke-TestRequest -Method $case.Method -Path $case.Path -Body $case.Body
            Assert-Response -Response $response -Status 400 -Code 40001 -Message $case.Message
            Write-Host "  HTTP $($response.Status) | $($response.Text)"
        }
    }

    Invoke-Check -Name 'invalid requests did not insert products' -Action {
        $countAfterInvalid = Get-ProductCount
        Assert-Condition ($countAfterInvalid -eq $initialCount) "Product count changed: $initialCount -> $countAfterInvalid"
    }

    Invoke-Check -Name 'valid product creation' -Action {
        $body = '{"title":"NotebookValidation","price":1990,"stock":10,"category":"Stationery","sellerName":"Test seller"}'
        $response = Invoke-TestRequest -Method 'POST' -Path '/api/products' -Body $body
        Assert-Response -Response $response -Status 201 -Code 0 -Message 'ok'
        Assert-Condition ($response.Json.data.id -gt 0) 'Expected a generated product ID.'
        Assert-Condition ($response.Json.data.title -ceq 'NotebookValidation') 'Unexpected product title.'
        Assert-Condition ($response.Json.data.price -eq 1990) 'Unexpected product price.'
        Assert-Condition ($response.Json.data.stock -eq 10) 'Unexpected product stock.'
        Assert-Condition ($response.Json.data.status -ceq 'on') 'Expected status on.'
        Write-Host "  HTTP $($response.Status) | $($response.Text)"
    }

    Invoke-Check -Name 'valid request inserted exactly one product' -Action {
        $finalCount = Get-ProductCount
        Assert-Condition ($finalCount -eq ($initialCount + 1)) "Expected count $($initialCount + 1); actual $finalCount"
    }

    Write-Host "RESULT: $($script:passedCount) passed, $($script:failedCount) failed."
    if ($script:failedCount -gt 0) { exit 1 }
} catch {
    Write-Host "SETUP FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 2
} finally {
    $OutputEncoding = $originalPipeEncoding
    [Console]::OutputEncoding = $originalConsoleEncoding
}
