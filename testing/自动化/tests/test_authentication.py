def test_login_success_returns_user_identity(api):
    """正确账号密码登录应返回有效的用户身份信息。"""
    response = api.post(
        "/api/auth/login",
        auth=False,
        json={"username": "alice", "password": "Alice@123"},
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["userId"] == 2
    assert body["data"]["username"] == "alice"
    assert body["data"]["role"] == "user"
    assert body["data"]["token"]


def test_logout_invalidates_original_token(api):
    """登出后，原 Token 应失效，不能继续访问受保护接口。"""
    login = api.post(
        "/api/auth/login",
        auth=False,
        json={"username": "alice", "password": "Alice@123"},
    )
    assert login.status_code == 200, login.text
    token = login.json()["data"]["token"]

    logout = api.post(
        "/api/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert logout.status_code == 200, logout.text
    assert logout.json()["code"] == 0

    after_logout = api.get(
        "/api/orders/mine",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert after_logout.status_code in (401, 403), (
        "登出后原 Token 仍可访问受保护接口："
        f"HTTP {after_logout.status_code}, {after_logout.text}"
    )
