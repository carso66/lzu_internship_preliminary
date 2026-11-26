param([string]$BaseUrl = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'
$oldConsoleEncoding = [Console]::OutputEncoding
$oldPipeEncoding = $OutputEncoding
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$BaseUrl = $BaseUrl.TrimEnd('/')
$script:Passed = 0
$script:CurrentCase = 'setup'
$script:Authorization = ''
$script:Phone = 'L2TEST' + [guid]::NewGuid().ToString('N').Substring(0, 12)

function Require {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Send-Api {
    param([string]$Method, [string]$Path, [object]$Body = $null,
          [string]$Authorization = $script:Authorization)
    $curlArgs = @('--silent', '--show-error', '--max-time', '15',
        '--request', $Method, '--url', ($BaseUrl + $Path),
        '--header', 'Accept: application/json',
        '--write-out', '\n__HTTP_META__:%{http_code}|%{content_type}')
    if ($Authorization) { $curlArgs += @('--header', ('Authorization: ' + $Authorization)) }
    if ($Method -ne 'GET') {
        $json = if ($null -eq $Body) { 'null' } else { $Body | ConvertTo-Json -Compress }
        $curlArgs += @('--header', 'Content-Type: application/json', '--data-binary', '@-')
        $raw = ($json | & curl.exe @curlArgs) -join "`n"
    } else { $raw = (& curl.exe @curlArgs) -join "`n" }
    if ($LASTEXITCODE -ne 0) { throw "curl failed ($LASTEXITCODE)" }
    $match = [regex]::Match($raw, '(?s)^(.*)\r?\n__HTTP_META__:(\d{3})\|([^\r\n]*)\s*$')
    if (-not $match.Success) { throw "Cannot parse response: $raw" }
    $text = $match.Groups[1].Value.Trim()
    return [pscustomobject]@{ Status=[int]$match.Groups[2].Value; ContentType=$match.Groups[3].Value
        Json=($text | ConvertFrom-Json); Text=$text }
}

function Expect {
    param($Response, [int]$Status, [int]$Code)
    Require ($Response.Status -eq $Status) "Expected HTTP $Status. $($Response.Text)"
    Require ($Response.ContentType -match '^application/json(?:;|$)') 'Expected JSON response'
    foreach ($field in @('code', 'message', 'data')) {
        Require ($Response.Json.PSObject.Properties.Name -contains $field) "Missing $field"
    }
    Require ($Response.Json.code -eq $Code) "Expected code $Code. $($Response.Text)"
    Require (-not [string]::IsNullOrWhiteSpace([string]$Response.Json.message)) 'Missing message'
    if ($Code -ne 0) { Require ($null -eq $Response.Json.data) 'Error data must be null' }
}

function Check {
    param([string]$Name, [scriptblock]$Action)
    $script:CurrentCase = $Name
    & $Action
    $script:Passed++
    Write-Output "PASS: $Name"
}

function New-OrderBody {
    return @{ productId=$script:ProductId; quantity=1; buyerName='L2 buyer'
        buyerPhone=$script:Phone; address='Test address' }
}

try {
    Get-Command curl.exe -ErrorAction Stop | Out-Null
    Write-Output "Target: $BaseUrl"
    Write-Output 'Creates 1 product and 1 order. Does not delete existing data.'
    $product = @{ title='L2 test product'; price=1990; stock=3; category='Tests'; sellerName='Test seller' }
    Check 'public health' { Expect (Send-Api 'GET' '/api/health' $null '') 200 0 }
    Check 'login' {
        $login = Send-Api 'POST' '/api/auth/login' @{username='demo';password='demo123'} ''
        Expect $login 200 0
        Require (-not [string]::IsNullOrWhiteSpace([string]$login.Json.data.token)) 'Missing token'
        $script:Authorization = 'Bearer ' + $login.Json.data.token
    }
    Check 'publish without token' { Expect (Send-Api 'POST' '/api/products' $product '') 401 40101 }
    Check 'publish with unknown token' { Expect (Send-Api 'POST' '/api/products' $product 'Bearer unknown') 401 40101 }
    Check 'publish with valid token' {
        $result = Send-Api 'POST' '/api/products' $product
        Expect $result 201 0
        $script:ProductId = [long]$result.Json.data.id
    }
    Check 'public product detail' { Expect (Send-Api 'GET' "/api/products/$script:ProductId" $null '') 200 0 }
    Check 'status without token' {
        Expect (Send-Api 'PATCH' "/api/products/$script:ProductId/status" @{status='off'} '') 401 40101
        $detail = Send-Api 'GET' "/api/products/$script:ProductId" $null ''
        Expect $detail 200 0
        Require ($detail.Json.data.status -ceq 'on') 'Rejected PATCH changed status'
    }
    Check 'valid token takes product off' {
        $result = Send-Api 'PATCH' "/api/products/$script:ProductId/status" @{status='off'}
        Expect $result 200 0
        Require ($result.Json.data.status -ceq 'off') 'Expected off'
    }
    Check 'off product still rejects orders' { Expect (Send-Api 'POST' '/api/orders' (New-OrderBody) '') 409 40902 }
    Check 'valid token puts product on' {
        $result = Send-Api 'PATCH' "/api/products/$script:ProductId/status" @{status='on'}
        Expect $result 200 0
        Require ($result.Json.data.status -ceq 'on') 'Expected on'
    }
    Check 'create order without token' {
        $result = Send-Api 'POST' '/api/orders' (New-OrderBody) ''
        Expect $result 201 0
        $script:OrderNo = [string]$result.Json.data.orderNo
    }
    Write-Output "RESULT: $script:Passed passed, 0 failed."
    Write-Output 'Run mvnw.cmd test for the complete JUnit suite.'
} catch {
    Write-Output "FAIL: $script:CurrentCase"
    Write-Output $_.Exception.Message
    Write-Output "RESULT: $script:Passed passed, 1 failed; remaining checks were not run."
    exit 1
} finally {
    $OutputEncoding = $oldPipeEncoding
    [Console]::OutputEncoding = $oldConsoleEncoding
}
