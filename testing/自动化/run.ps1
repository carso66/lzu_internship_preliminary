<#
.SYNOPSIS
    testing/自动化 的一键入口：检查依赖 -> 跑 pytest -> 汇总结果。

.DESCRIPTION
    脚本自己解析参数，所以 -k / -m / -x / --lf 这类 pytest 选项可以和本脚本的
    选项混着写，全部原样转给 pytest，不必额外加 `--` 分隔。

    三种运行模式：
      1. 默认            夹具自己向系统申请空闲端口，拉起 testing/sut/app.py，跑完终止。
      2. -Port 8090      仍由夹具启停，但端口固定（方便同时看服务日志或抓包）。
      3. -BaseUrl <url>  直接用已经跑着的实例，夹具不启停任何进程。

    注意：所有实例共用同一个 testing/sut/sut.db，所以模式 3 不要和手工启动的
    实例同时用另一个端口跑，否则两边会互相重置数据库。

.PARAMETER -Install
    先按 requirements.txt 安装/校对依赖，再跑测试。

.PARAMETER -BaseUrl <url>
    已运行实例的地址，会写入 SUT_BASE_URL。与 -Port 互斥。

.PARAMETER -Port <port>
    固定端口，会写入 SUT_PORT。与 -BaseUrl 互斥。

.PARAMETER -h
    打印本帮助后退出。

.EXAMPLE
    .\run.ps1
    跑全部 83 个测试项。

.EXAMPLE
    .\run.ps1 -Install
    先装依赖再跑。

.EXAMPLE
    .\run.ps1 test_orders.py -k oversell -x
    只跑 test_orders.py 里名字含 oversell 的用例，遇到失败即停。
#>

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $here

# ---- 参数解析 ----------------------------------------------------------------
# 不用 param() 块：pytest 的选项（-k / -x / -m ...）在 param() 下会被当成
# 未知具名参数直接报错，手工解析才能做到"透明的透传"。
$BaseUrl = $null
$Port = 0
$Install = $false
$showHelp = $false
$passthrough = @()

$i = 0
while ($i -lt $args.Count) {
    $a = [string] $args[$i]
    switch ($a) {
        '-Install' { $Install = $true }
        '-BaseUrl' {
            $i++
            if ($i -ge $args.Count) { throw '-BaseUrl 后面需要一个地址，例如 -BaseUrl http://127.0.0.1:8080' }
            $BaseUrl = [string] $args[$i]
        }
        '-Port' {
            $i++
            if ($i -ge $args.Count) { throw '-Port 后面需要一个端口号，例如 -Port 8090' }
            $Port = [int] $args[$i]
        }
        { $_ -in @('-h', '-help', '--help') } { $showHelp = $true }
        default { $passthrough += $a }
    }
    $i++
}

if ($showHelp) {
    Get-Help $MyInvocation.MyCommand.Path -Detailed
    exit 0
}

if ($BaseUrl -and $Port -gt 0) {
    throw '-BaseUrl 与 -Port 只能选一个：前者复用已有实例，后者仍由夹具启动。'
}

# ---- 环境检查 ----------------------------------------------------------------
# 优先 python，其次 py -3；两者都不在 PATH 时给出可照抄的提示。
if (Get-Command python -ErrorAction SilentlyContinue) {
    $exe = 'python'; $prefix = @()
} elseif (Get-Command py -ErrorAction SilentlyContinue) {
    $exe = 'py'; $prefix = @('-3')
} else {
    throw '找不到 Python。请先安装 Python 3.9+ 并把它加入 PATH。'
}

function Invoke-Python {
    param([string[]] $PyArgs)
    & $exe @prefix @PyArgs
}

if ($Install) {
    Write-Host '==> 安装依赖' -ForegroundColor Cyan
    Invoke-Python @('-m', 'pip', 'install', '-r', (Join-Path $here 'requirements.txt'))
    if ($LASTEXITCODE -ne 0) { throw 'pip install 失败。' }
}

# 依赖缺失时 pytest 会以 import 错误退出、信息不直观，这里提前说清缺什么。
$missing = @()
foreach ($mod in @('pytest', 'requests')) {
    Invoke-Python @('-c', "import $mod") 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { $missing += $mod }
}
if ($missing.Count -gt 0) {
    throw ('缺少依赖：' + ($missing -join ', ') + '。执行 .\run.ps1 -Install，或 python -m pip install -r requirements.txt')
}

if ($BaseUrl) {
    $env:SUT_BASE_URL = $BaseUrl
    Remove-Item Env:\SUT_PORT -ErrorAction SilentlyContinue
    Write-Host "==> 复用已有实例 $BaseUrl（夹具不启停进程）" -ForegroundColor Cyan
} elseif ($Port -gt 0) {
    $env:SUT_PORT = "$Port"
    Remove-Item Env:\SUT_BASE_URL -ErrorAction SilentlyContinue
    Write-Host "==> 夹具启动 SUT，端口固定 $Port" -ForegroundColor Cyan
} else {
    Remove-Item Env:\SUT_BASE_URL -ErrorAction SilentlyContinue
    Remove-Item Env:\SUT_PORT -ErrorAction SilentlyContinue
    Write-Host '==> 夹具自动申请空闲端口并启停 SUT' -ForegroundColor Cyan
}

# ---- 执行 --------------------------------------------------------------------
$pyArgs = @('-m', 'pytest') + $passthrough
Write-Host ('==> ' + $exe + ' ' + (($prefix + $pyArgs) -join ' ')) -ForegroundColor DarkGray

Invoke-Python $pyArgs
$code = $LASTEXITCODE

Write-Host ''
switch ($code) {
    0 {
        # 退出码 0 也包含"只有 xfail"的情况：xfail 是预期失败，不是问题。
        Write-Host '==> 通过（退出码 0）。xfail 对应缺陷报告里的 BUG-xx，属预期结果。' -ForegroundColor Green
    }
    5 {
        Write-Host '==> 没有收集到用例（退出码 5），检查路径与 -k 是否写错。' -ForegroundColor Yellow
    }
    default {
        Write-Host "==> 存在真实失败（退出码 $code）。failed 才是要查的，xfail 是预期。" -ForegroundColor Red
    }
}
exit $code