#!/usr/bin/env bash
# testing/自动化 的一键入口（macOS / Linux）。
#
# 与 run.ps1 行为一致：检查依赖 -> 跑 pytest -> 汇总结果。
# 未识别的参数原样转给 pytest，所以 -k / -m / -x / --lf 可以直接写。
#
# 三种运行模式：
#   1. 默认            夹具自己申请空闲端口，拉起 testing/sut/app.py，跑完终止。
#   2. -Port 8090      仍由夹具启停，但端口固定。
#   3. -BaseUrl <url>  复用已跑着的实例，夹具不启停任何进程。
#
# 用法：
#   ./run.sh
#   ./run.sh -Install
#   ./run.sh -Port 8090 test_orders.py -k oversell
#   ./run.sh -BaseUrl http://127.0.0.1:8080 test_health.py
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$here"

base_url=""
port=""
install_deps=0
passthrough=()

while [ $# -gt 0 ]; do
  case "$1" in
    -Install) install_deps=1; shift ;;
    -BaseUrl)
      [ $# -ge 2 ] || { echo "-BaseUrl 后面需要一个地址，例如 -BaseUrl http://127.0.0.1:8080" >&2; exit 2; }
      base_url="$2"; shift 2 ;;
    -Port)
      [ $# -ge 2 ] || { echo "-Port 后面需要一个端口号，例如 -Port 8090" >&2; exit 2; }
      port="$2"; shift 2 ;;
    -h|--help|-help) sed -n '2,20p' "$0"; exit 0 ;;
    *) passthrough+=("$1"); shift ;;
  esac
done

if [ -n "$base_url" ] && [ -n "$port" ]; then
  echo "-BaseUrl 与 -Port 只能选一个：前者复用已有实例，后者仍由夹具启动。" >&2
  exit 2
fi

# 选解释器：优先"能 import pytest 和 requests"的那个。
# 只按名字挑会踩坑——Windows 上 python3 可能指向应用商店占位符，
# 与装了依赖的 conda / pyenv 环境不是同一个。
python_bin=""
python_fallback=""
for candidate in python3 python; do
  command -v "$candidate" >/dev/null 2>&1 || continue
  "$candidate" -c "import sys" >/dev/null 2>&1 || continue
  [ -n "$python_fallback" ] || python_fallback="$candidate"
  if "$candidate" -c "import pytest, requests" >/dev/null 2>&1; then
    python_bin="$candidate"
    break
  fi
done

if [ -z "$python_bin" ] && [ -n "$python_fallback" ]; then
  # 没有解释器带齐依赖：仍用这个，让 -Install 有明确的安装目标。
  python_bin="$python_fallback"
fi
[ -n "$python_bin" ] || { echo "找不到 Python。请先安装 Python 3.9+ 并把它加入 PATH。" >&2; exit 2; }

if [ "$install_deps" -eq 1 ]; then
  echo "==> 安装依赖（$python_bin）"
  "$python_bin" -m pip install -r "$here/requirements.txt"
fi

# 依赖缺失时 pytest 会以 import 错误退出、信息不直观，这里提前说清缺什么。
missing=()
"$python_bin" -c "import pytest" >/dev/null 2>&1 || missing+=("pytest")
"$python_bin" -c "import requests" >/dev/null 2>&1 || missing+=("requests")
if [ ${#missing[@]} -gt 0 ]; then
  echo "缺少依赖：${missing[*]}。执行 ./run.sh -Install，或 $python_bin -m pip install -r requirements.txt" >&2
  exit 2
fi

if [ -n "$base_url" ]; then
  export SUT_BASE_URL="$base_url"
  unset SUT_PORT || true
  echo "==> 复用已有实例 $base_url（夹具不启停进程）"
elif [ -n "$port" ]; then
  export SUT_PORT="$port"
  unset SUT_BASE_URL || true
  echo "==> 夹具启动 SUT，端口固定 $port"
else
  unset SUT_BASE_URL || true
  unset SUT_PORT || true
  echo "==> 夹具自动申请空闲端口并启停 SUT"
fi

echo "==> $python_bin -m pytest ${passthrough[*]:-}"

set +e
"$python_bin" -m pytest ${passthrough[@]+"${passthrough[@]}"}
code=$?
set -e

echo
case "$code" in
  0) echo "==> 通过（退出码 0）。xfail 对应缺陷报告里的 BUG-xx，属预期结果。" ;;
  5) echo "==> 没有收集到用例（退出码 5），检查路径与 -k 是否写错。" ;;
  *) echo "==> 存在真实失败（退出码 $code）。failed 才是要查的，xfail 是预期。" ;;
esac
exit "$code"