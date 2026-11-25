# -*- coding: utf-8 -*-
"""pytest 公共配置与夹具。

base_url 支持通过环境变量 SUT_BASE 覆盖，换端口后无需改代码：
    SUT_BASE=http://127.0.0.1:8090 pytest -q

另外注册了 defect 标记，把用例和缺陷编号挂起来，可以按缺陷筛选：
    pytest -q -m defect              # 只看对照已知缺陷的 14 条
    pytest -q -m defect -k quantity # 再按关键字收窄，例如只看数量校验那几条
"""

import os

import pytest
import requests

DEFAULT_BASE = "http://127.0.0.1:8080"
ALICE = {"username": "alice", "password": "Alice@123"}


def pytest_configure(config):
    """注册自定义标记，避免 pytest 报 unknown marker 警告。"""
    config.addinivalue_line(
        "markers", "defect(id): 这条用例对照的是哪个已登记缺陷，如 defect('BUG-05')"
    )


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


@pytest.fixture
def new_order(base_url, auth):
    """现场创建一笔待支付订单，返回 orderId。

    用独立订单而不是复用种子订单，是为了让「重复支付」「回调」这类会改订单状态的
    用例互不干扰：每条用例动的是自己刚建的那笔。
    """
    resp = requests.post(
        base_url + "/api/orders",
        json={"productId": 1, "quantity": 1},
        headers=auth,
        timeout=10,
    )
    assert resp.status_code == 201, "创建订单失败，商品 1 可能库存不足：%s" % resp.text
    return resp.json()["data"]["id"]
