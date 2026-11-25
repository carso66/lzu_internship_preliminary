# -*- coding: utf-8 -*-
"""pytest 公共配置。

base_url 支持通过环境变量 SUT_BASE 覆盖，换端口后无需改代码：
    SUT_BASE=http://127.0.0.1:8090 pytest -q
"""

import os

import pytest
import requests

DEFAULT_BASE = "http://127.0.0.1:8080"
ALICE = {"username": "alice", "password": "Alice@123"}


@pytest.fixture(scope="session")
def base_url():
    """被测服务地址，默认 8080，可通过 SUT_BASE 覆盖。"""
    return os.environ.get("SUT_BASE", DEFAULT_BASE).rstrip("/")


@pytest.fixture(scope="session")
def token(base_url):
    """登录 alice，返回 token 字符串。"""
    resp = requests.post(base_url + "/api/auth/login", json=ALICE, timeout=10)
    assert resp.status_code == 200, "登录失败，检查 SUT 是否已启动：%s" % resp.text
    return resp.json()["data"]["token"]


@pytest.fixture(scope="session")
def auth(token):
    """Authorization 请求头。"""
    return {"Authorization": "Bearer " + token}
