
def test_payment_for_nonexistent_order(api):
    response = api.post(
        "/api/payments",
        json={"orderId": 999999, "method": "alipay"}
    )

    print("\n不存在订单支付:", response.status_code, response.text)

    assert response.status_code == 404


def test_unsupported_payment_method_is_rejected(api):
    response = api.post(
        "/api/payments",
        json={"orderId": 1, "method": "cash"}
    )

    print("\n不支持的支付方式:", response.status_code, response.text)

    assert response.status_code == 400
