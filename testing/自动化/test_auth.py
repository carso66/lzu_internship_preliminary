# -*- coding: utf-8 -*-
"""认证模块自动化用例。

约定：
- 断言依据 `testing/README.md` 的接口文档承诺（正确业务预期）。
- 因 SUT 缺陷导致必然失败的用例，用 `xfail(strict=True)` 标记并写明缺陷编号。
  `strict=True` 的意义：一旦 SUT 修复、用例转为通过，pytest 会报 XPASS 失败，
  提醒同步更新用例与缺陷报告，避免"缺陷修了但测试还挂着 xfail"。
"""
import pytest
import requests


# ---------------------------------------------------------------- 正常
def test_login_success(base_url):
    """TC-001 正确账号密码登录成功。"""
    r = requests.post(base_url + "/api/auth/login",
                      json={"username": "alice", "password": "Alice@123"}, timeout=10)
    assert r.status_code == 200
    body = r.json()
    assert body["code"] == 0
    data = body["data"]
    assert data["userId"] == 2
    assert data["username"] == "alice"
    assert data["role"] == "user"
    assert data["token"]


# ---------------------------------------------------------------- 异常
def test_login_wrong_password(base_url):
    """TC-002 错误密码应 401，且不返回 token。"""
    r = requests.post(base_url + "/api/auth/login",
                      json={"username": "alice", "password": "wrong-password"}, timeout=10)
    assert r.status_code == 401
    body = r.json()
    assert body["code"] == 40101
    assert body["data"] is None


def test_login_missing_password(base_url):
    """TC-004 缺少 password 字段应失败，不能登录成功。"""
    r = requests.post(base_url + "/api/auth/login", json={"username": "alice"}, timeout=10)
    assert r.status_code == 401
    assert r.json()["code"] == 40101


def test_login_unknown_user(base_url):
    """TC-003 不存在的用户应 401。"""
    r = requests.post(base_url + "/api/auth/login",
                      json={"username": "nobody", "password": "x"}, timeout=10)
    assert r.status_code == 401
    assert r.json()["code"] == 40101


def test_protected_endpoint_without_token(base_url):
    """TC-009 未登录访问受保护接口应 401。"""
    r = requests.get(base_url + "/api/users/2", timeout=10)
    assert r.status_code == 401
    assert r.json()["code"] == 40101


# ---------------------------------------------------------------- 缺陷关联
@pytest.mark.xfail(strict=True, reason="BUG-02：登出未使 token 失效，登出后仍可访问 /api/orders/mine")
def test_logout_invalidates_token(base_url):
    """TC-005 文档承诺：登出后原 token 应失效。"""
    # 用独立账号 token，避免影响 session 级 fixture
    login = requests.post(base_url + "/api/auth/login",
                          json={"username": "carol", "password": "Carol@123"}, timeout=10)
    assert login.status_code == 200, "种子账号 carol 登录失败"
    token = login.json()["data"]["token"]
    headers = {"Authorization": "Bearer " + token}

    out = requests.post(base_url + "/api/auth/logout", headers=headers, timeout=10)
    assert out.status_code == 200

    after = requests.get(base_url + "/api/orders/mine", headers=headers, timeout=10)
    assert after.status_code == 401


@pytest.mark.xfail(strict=True, reason="BUG-02：token 由用户 ID 直接拼接，伪造 tk_1 可冒充 admin")
def test_token_is_not_predictable(base_url):
    """TC-006 不能伪造 token：tk_1 不应通过认证。"""
    r = requests.get(base_url + "/api/users/1",
                     headers={"Authorization": "Bearer tk_1"}, timeout=10)
    assert r.status_code == 401


@pytest.mark.xfail(strict=True, reason="BUG-01：普通用户可读取他人 realName 与 phone")
def test_user_detail_cross_user_forbidden(base_url, alice_headers):
    """TC-008 文档承诺：只能看自己，看别人应 403。"""
    r = requests.get(base_url + "/api/users/3", headers=alice_headers, timeout=10)
    assert r.status_code == 403
