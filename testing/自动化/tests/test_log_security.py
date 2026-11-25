def test_login_password_must_not_appear_in_logs(api, db, capfd):
    """登录密码不得以明文形式输出到日志。"""

    user = db(
        "SELECT username, password FROM users WHERE id = 2"
    )[0]

    response = api.post(
        "/api/auth/login",
        auth=False,
        json={
            "username": user["username"],
            "password": user["password"]
        }
    )

    logs = capfd.readouterr().out

    print("\n========== 登录密码日志泄露测试 ==========")
    print("登录 HTTP:", response.status_code)
    print("是否在日志中发现密码:", user["password"] in logs)

    assert response.status_code == 200
    assert user["password"] not in logs, (
        "安全缺陷：用户登录密码以明文形式出现在控制台日志中"
    )


def test_access_token_must_not_appear_in_logs(api, capfd):
    """访问日志不得记录完整 Token。"""

    response = api.get("/api/orders/mine")

    logs = capfd.readouterr().out

    print("\n========== Token 日志泄露测试 ==========")
    print("请求 HTTP:", response.status_code)
    print("是否在日志中发现完整 Token:", "Bearer tk_2" in logs)

    assert response.status_code == 200
    assert "Bearer tk_2" not in logs, (
        "安全缺陷：访问日志记录了完整的 Authorization Token"
    )