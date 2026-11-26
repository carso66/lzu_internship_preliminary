# -*- coding: utf-8 -*-
"""pytest 公共夹具：自动拉起被测服务、提供已登录 token。

SUT 每次启动都会删除并重建 `testing/sut/sut.db` 再写入种子数据，所以只要
拿到一个"刚启动"的实例，就能保证断言里的 12 个商品 / 11 个在售 / id=5 库存 1
等初始值成立。下单、发布商品这类会改数据的用例会破坏这份初始状态，因此
夹具按 **模块** 维度各起一个全新实例（scope="module"）：模块之间互不影响，
模块内部按文件顺序共享同一份数据。

端口不写死：向系统申请一个空闲端口，避免与手工启动的 8080 撞车。

如需对已经跑着的实例做测试，设置环境变量即可（此处夹具不再负责启停）：

    set SUT_BASE_URL=http://127.0.0.1:8080      # Windows
    export SUT_BASE_URL=http://127.0.0.1:8080   # macOS / Linux

只想指定端口号、仍由夹具负责启动：

    set SUT_PORT=8090

注意：所有实例共用同一个 `sut.db` 文件，因此**不要**在手工跑着 SUT 的同时
执行 pytest，否则两边会互相重置数据库，结论不可信。
"""

import os
import socket
import subprocess
import sys
import time
from pathlib import Path

import pytest
import requests

REPO_ROOT = Path(__file__).resolve().parents[2]
SUT_SCRIPT = REPO_ROOT / "testing" / "sut" / "app.py"

STARTUP_TIMEOUT = 30.0


def _free_port():
    """向系统要一个当前空闲的端口。"""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def _wait_until_up(url, proc):
    deadline = time.time() + STARTUP_TIMEOUT
    while time.time() < deadline:
        if proc.poll() is not None:
            raise RuntimeError("SUT 进程提前退出，退出码 %s" % proc.returncode)
        try:
            if requests.get(url + "/health", timeout=1).status_code == 200:
                return
        except requests.RequestException:
            pass
        time.sleep(0.2)
    raise RuntimeError("SUT 未能在 %.0f 秒内启动: %s" % (STARTUP_TIMEOUT, url))


@pytest.fixture(scope="module")
def base_url():
    """被测服务根地址。"""
    external = os.environ.get("SUT_BASE_URL")
    if external:
        yield external.rstrip("/")
        return

    port = int(os.environ.get("SUT_PORT") or _free_port())
    url = "http://127.0.0.1:%d" % port
    proc = subprocess.Popen(
        [sys.executable, str(SUT_SCRIPT), "--host", "127.0.0.1", "--port", str(port)],
        cwd=str(REPO_ROOT),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        _wait_until_up(url, proc)
        yield url
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=10)


@pytest.fixture(scope="module")
def login(base_url):
    """登录辅助函数：返回 (token, userId)。"""
    def _login(username="alice", password="Alice@123"):
        resp = requests.post(
            base_url + "/api/auth/login",
            json={"username": username, "password": password},
            timeout=5,
        )
        assert resp.status_code == 200, resp.text
        data = resp.json()["data"]
        return data["token"], data["userId"]

    return _login


@pytest.fixture(scope="module")
def alice_token(login):
    return login("alice", "Alice@123")[0]


@pytest.fixture(scope="module")
def bob_token(login):
    return login("bob", "Bob@123")[0]


@pytest.fixture(scope="module")
def admin_token(login):
    return login("admin", "Admin@123")[0]


@pytest.fixture
def auth(alice_token):
    """默认带 alice 身份的请求头。"""
    return {"Authorization": "Bearer " + alice_token}
