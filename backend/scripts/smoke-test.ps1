param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(20)
$script:checks = 0

function Send-Api([string]$Method, [string]$Path, $Body, [int]$Status, [int]$Code = 0) {
    $request = New-Object System.Net.Http.HttpRequestMessage
    $request.Method = New-Object System.Net.Http.HttpMethod($Method)
    $request.RequestUri = [Uri]($BaseUrl.TrimEnd('/') + $Path)
    if ($null -ne $Body) {
        $json = ConvertTo-Json -InputObject $Body -Depth 10 -Compress
        $request.Content = New-Object System.Net.Http.StringContent($json, [Text.Encoding]::UTF8, 'application/json')
    }
    $response = $null
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $result = $text | ConvertFrom-Json
        if ([int]$response.StatusCode -ne $Status -or $result.code -ne $Code) {
            throw "$Method $Path : expected HTTP $Status / code $Code; got $([int]$response.StatusCode): $text"
        }
        if ($null -eq $result.PSObject.Properties['data'] -or $null -eq $result.PSObject.Properties['message']) {
            throw "Missing unified response fields: $text"
        }
        $script:checks++
        return $result.data
    } finally {
        if ($null -ne $response) { $response.Dispose() }
        $request.Dispose()
    }
}

try {
    $null = Send-Api GET '/api/health' $null 200
    $title = 'Smoke-' + [Guid]::NewGuid().ToString('N')
    $product = Send-Api POST '/api/products' @{title=$title;price=1990;stock=3;category='Smoke';sellerName='Test seller'} 201
    $id = $product.id
    $null = Send-Api GET "/api/products/$id" $null 200
    $list = Send-Api GET ('/api/products?keyword=' + [Uri]::EscapeDataString($title) + '&category=Smoke&status=on&pageSize=1') $null 200
    if ($list.total -ne 1 -or $list.items[0].id -ne $id) { throw 'Product filtering failed' }
    $null = Send-Api GET '/api/products?page=0' $null 400 40001
    $null = Send-Api GET '/api/products/9223372036854775807' $null 404 40401
    $buyer = @{productId=$id;quantity=0;buyerName='Test buyer';buyerPhone='00000000000';address='Test address'}
    $null = Send-Api POST '/api/orders' $buyer 400 40001
    $buyer.quantity = 2
    $order = Send-Api POST '/api/orders' $buyer 201
    if ($order.totalAmount -ne 3980 -or $order.unitPrice -ne 1990) { throw 'Server-side pricing failed' }
    $number = $order.orderNo
    $null = Send-Api POST '/api/orders' $buyer 409 40901
    $product = Send-Api GET "/api/products/$id" $null 200
    if ($product.stock -ne 1) { throw 'Stock deduction failed' }
    $null = Send-Api GET "/api/orders/$number" $null 200
    $orders = Send-Api GET '/api/orders?buyerPhone=00000000000&pageSize=50' $null 200
    if ($number -notin @($orders.items | ForEach-Object { $_.orderNo })) { throw 'Buyer order query failed' }
    $payment = Send-Api POST '/api/payments' @{orderNo=$number;method='alipay'} 200
    if ($payment.amount -ne 3980 -or $payment.status -ne 'paid') { throw 'Payment response failed' }
    $null = Send-Api POST '/api/payments' @{orderNo=$number;method='wechat'} 409 40903
    $paid = Send-Api GET "/api/orders/$number" $null 200
    if ($paid.status -ne 'paid' -or $null -eq $paid.paidAt) { throw 'Order payment state failed' }
    $null = Send-Api PATCH "/api/products/$id/status" @{status='off'} 200
    $buyer.quantity = 1
    $null = Send-Api POST '/api/orders' $buyer 409 40902
    $null = Send-Api PATCH "/api/products/$id/status" @{status='on'} 200
    [pscustomobject]@{Result='PASS';Checks=$script:checks;ProductId=$id;OrderNo=$number;Amount=$payment.amount}
} finally {
    $client.Dispose()
}
