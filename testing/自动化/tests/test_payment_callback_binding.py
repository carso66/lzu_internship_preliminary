
def test_callback_with_nonexistent_payment_id(api, db):
    """不存在的 paymentId 不得使订单变为已支付。"""

    order_id = 1
    fake_payment_id = "NONEXISTENT-PAYMENT-999"

    before = db(
        "SELECT status, paid_at FROM orders WHERE id = ?",
        (order_id,)
    )[0]

    assert before["status"] == "created"
    assert before["paid_at"] is None

    # 确认伪造的 paymentId 不存在
    payment = db(
        "SELECT id FROM payments WHERE payment_no = ?",
        (fake_payment_id,)
    )
    assert payment == []

    response = api.post(
        "/api/payments/callback",
        auth=False,
        json={
            "orderId": order_id,
            "paymentId": fake_payment_id,
            "result": "success"
        }
    )

    after = db(
        "SELECT status, paid_at FROM orders WHERE id = ?",
        (order_id,)
    )[0]

    print("\n========== 支付回调关联验证 ==========")
    print("HTTP:", response.status_code)
    print("响应:", response.text)
    print("回调前:", before)
    print("回调后:", after)

    # 安全要求：拒绝伪造回调，订单状态保持不变
    assert response.status_code in (400, 401, 403), (
        f"伪造支付回调未被拒绝：HTTP {response.status_code}"
    )

    assert after["status"] == "created"
    assert after["paid_at"] is None
