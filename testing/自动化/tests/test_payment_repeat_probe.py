
def test_repeated_payment_creation_probe(api, db):
    """同一订单重复发起支付，不得创建多笔独立支付记录。"""

    order_id = 1

    before = db(
        "SELECT COUNT(*) AS n FROM payments WHERE order_id = ?",
        (order_id,)
    )[0]["n"]

    results = []

    # 连续发起两次相同支付请求
    for _ in range(2):
        response = api.post(
            "/api/payments",
            json={
                "orderId": order_id,
                "method": "alipay"
            }
        )
        results.append(response)

    after_rows = db(
        "SELECT id, order_id, amount, status, payment_no "
        "FROM payments WHERE order_id = ? ORDER BY id",
        (order_id,)
    )

    new_count = len(after_rows) - before

    print("\n========== 重复支付安全验证 ==========")

    for i, response in enumerate(results, 1):
        print(f"第{i}次请求 HTTP: {response.status_code}")
        print(f"响应内容: {response.text}")

    print("测试前支付记录数:", before)
    print("测试后支付记录数:", len(after_rows))
    print("新增支付记录数:", new_count)
    print("支付记录详情:", after_rows)

    # 安全要求：两次请求最多只能产生一笔支付记录
    assert new_count == 1, (
        f"重复支付产生了异常记录："
        f"测试前 {before} 笔，测试后 {len(after_rows)} 笔，"
        f"新增 {new_count} 笔，预期新增 1 笔"
    )
