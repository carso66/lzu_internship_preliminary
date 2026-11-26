# -*- coding: utf-8 -*-
"""认证接口：登录与登出。

标记为 xfail(strict=True) 的用例断言的是**接口文档承诺的正确行为**，
当前 SUT 存在缺陷导致其不成立，详见 testing/缺陷报告.md 对应编号。
strict=True 意味着缺陷一旦修复，用例会变成 XPASS 从而报错，可用来确认修复。
"""

import pytest
import requests


def test_login_success(base_url):
    """TC-002 正常：alice 用正确口令登录，返回 token 与 userId=2。"""
    resp = requests.post(
        base_url + "/api/auth/login",
        json={"username": "alice", "password": "Alice@123"},
        timeout=5,
    )

    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0
    data = body["data"]
    assert data["userId"] == 2
    assert data["username"] == "alice"
    assert data["role"] == "user"
    assert data["token"] == "tk_2"
    assert data["expiresAt"]


def test_login_wrong_password(base_url):
    """TC-003 异常：口令错误必须失败，且不能返回 token。"""
    resp = requests.post(
        base_url + "/api/auth/login",
        json={"username": "alice", "password": "Alice@124"},
        timeout=5,
    )

    assert resp.status_code == 401
    body = resp.json()
    assert body["code"] == 40101
    assert body["data"] is None


def test_login_unknown_user(base_url):
    """TC-004 异常：不存在的用户名必须失败。"""
    resp = requests.post(
        base_url + "/api/auth/login",
        json={"username": "nobody", "password": "Whatever@123"},
        timeout=5,
    )

    assert resp.status_code == 401
    assert resp.json()["code"] == 40101


@pytest.mark.xfail(strict=True, reason="BUG-04 token 无签名，可猜出他人 token 并提权")
def test_forged_token_rejected(base_url):
    """TC-005 异常：未登录时用猜出的 `tk_1` 不应拿到 admin 身份。

    文档要求需登录才能访问用户资料，隐含 token 必须不可预测。
    实际 SUT 的 token 就是 `tk_` + 用户 id 的明文拼接（见 5.1 返回示例
    `tk_2`），任何人按 id 递增猜一遍就能以他人甚至管理员身份访问。
    """
    resp = requests.get(
        base_url + "/api/users/1",
        headers={"Authorization": "Bearer tk_1"},
        timeout=5,
    )

    assert resp.status_code == 401
    assert resp.json()["code"] == 40101


@pytest.mark.xfail(strict=True, reason="BUG-04 登出后原 token 仍然有效")
def test_logout_invalidates_token(base_url, bob_token):
    """TC-006 异常：文档 5.2 承诺「登出后原 token 应失效」。"""
    headers = {"Authorization": "Bearer " + bob_token}

    logout = requests.post(base_url + "/api/auth/logout", headers=headers, timeout=5)
    assert logout.status_code == 200
    assert logout.json()["data"]["message"] == "已退出登录"

    again = requests.get(base_url + "/api/users/3", headers=headers, timeout=5)

    assert again.status_code == 401
    assert again.json()["code"] == 40101
