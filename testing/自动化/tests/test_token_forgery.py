def test_forged_token_cannot_impersonate_another_user(api, db):
    """未登录用户不得通过构造 tk_3 冒充用户3。"""

    forged_token = "tk_3"

    response = api.get(
        "/api/orders/mine",
        auth=False,
        headers={
            "Authorization": f"Bearer {forged_token}"
        }
    )

    print("\n========== Token 伪造测试 ==========")
    print("使用的伪造 Token:", forged_token)
    print("HTTP:", response.status_code)
    print("响应:", response.text)

    # 安全要求：未经登录签发的 Token 不得获得访问权限
    assert response.status_code in (401, 403), (
        f"Token 伪造成功，攻击者可能冒充用户3："
        f"HTTP {response.status_code}"
    )