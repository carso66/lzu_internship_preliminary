# -*- coding: utf-8 -*-
"""pytest 公共配置：服务地址与账号 token fixture。

支持更换端口/主机执行：
    SUT_BASE_URL=http://127.0.0.1:8090 pytest -q
"""
import os

import pytest
import requests

BASE_URL = os.environ.get("SUT_BASE_URL", "http://127.0.0.1:8080").rstrip("/")

ACCOUNTS = {
    "admin": ("admin", "Admin@123"),
    "alice": ("alice", "Alice@123"),
    "bob": ("bob", "Bob@123"),
}


def pytest_configure(config):
    """会话开始时探活，服务没起就直接给出清晰提示，而不是 30 条连接错误。"""
    try:
        r = requests.get(BASE_URL + "/health", timeout=3)
        r.raise_for_status()
    except Exception as exc:  # noqa: BLE001
        pytest.exit(
            "SUT 未启动或不可达：%s\n请先运行 python testing/sut/app.py --port 8080\n原因：%r"
            % (BASE_URL, exc),
            returncode=2,
        )


@pytest.fixture(scope="session")
def base_url():
    return BASE_URL


@pytest.fixture(scope="session")
def tokens(base_url):
    """一次性登录三个账号，复用 token。"""
    result = {}
    for key, (username, password) in ACCOUNTS.items():
        r = requests.post(base_url + "/api/auth/login",
                          json={"username": username, "password": password}, timeout=10)
        assert r.status_code == 200, "种子账号 %s 登录失败：%s" % (username, r.text)
        result[key] = r.json()["data"]["token"]
    return result


@pytest.fixture(scope="session")
def alice_headers(tokens):
    return {"Authorization": "Bearer " + tokens["alice"]}


@pytest.fixture(scope="session")
def bob_headers(tokens):
    return {"Authorization": "Bearer " + tokens["bob"]}


@pytest.fixture(scope="session")
def admin_headers(tokens):
    return {"Authorization": "Bearer " + tokens["admin"]}


def login(base_url, username, password):
    """给需要独立 token 的用例用（例如登录失败用例）。"""
    return requests.post(base_url + "/api/auth/login",
                         json={"username": username, "password": password}, timeout=10)
