
def test_unsigned_fake_callback_must_not_mark_order_paid(api, db):
    """无签名的伪造支付回调不得将订单标记为已支付。"""

    # 1. 检查订单初始状态
    before = db(
        "SELECT status, paid_at FROM orders WHERE id = ?",
        (1,)
    )[0]

    assert before["status"] == "created"
    assert before["paid_at"] is None

    # 2. 不携带 Authorization 和签名发送伪造回调
    response = api.post(
        "/api/payments/callback",
        auth=False,
        json={
            "orderId": 1,
            "paymentId": "FAKE-PAY-AUTOMATION",
            "result": "success"
        },
    )

    # 3. 查询回调后的数据库状态
    after = db(
        "SELECT status, paid_at FROM orders WHERE id = ?",
        (1,)
    )[0]

    # 4. 同时检查 HTTP 响应和数据库状态
    rejected = response.status_code in (400, 401, 403)
    unchanged = (
        after["status"] == "created"
        and after["paid_at"] is None
    )

    assert rejected and unchanged, (
        "\n支付回调安全测试失败！"
        f"\nHTTP 状态码：{response.status_code}"
        f"\n接口响应：{response.text}"
        f"\n回调前订单：{before}"
        f"\n回调后订单：{after}"
        f"\n是否拒绝请求：{rejected}"
        f"\n订单状态是否保持不变：{unchanged}"
    )
