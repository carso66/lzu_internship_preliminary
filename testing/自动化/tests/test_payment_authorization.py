def test_user_cannot_create_payment_for_another_users_order(api, db):
    """普通用户不得为其他用户的订单发起支付。"""
    order_id = 1
    order = db(
        "SELECT user_id, status FROM orders WHERE id = ?",
        (order_id,),
    )[0]
    assert order["user_id"] != 3, "测试订单应不属于 Bob"

    before = db(
        "SELECT COUNT(*) AS n FROM payments WHERE order_id = ?",
        (order_id,),
    )[0]["n"]

    response = api.post(
        "/api/payments",
        headers={"Authorization": "Bearer tk_3"},
        json={"orderId": order_id, "method": "alipay"},
    )

    after = db(
        "SELECT COUNT(*) AS n FROM payments WHERE order_id = ?",
        (order_id,),
    )[0]["n"]

    assert response.status_code == 403, (
        f"Bob 不得支付 Alice 的订单，实际 HTTP {response.status_code}: {response.text}"
    )
    assert after == before, "越权支付请求不应新增支付记录"
