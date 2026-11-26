import pytest


def test_create_payment_success(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/payments",
        json={"orderId": 1, "method": "alipay"},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["amount"] > 0


def test_create_payment_invalid_method(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/payments",
        json={"orderId": 1, "method": "bank"},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 400
    assert response.json()["code"] == 40001


@pytest.mark.xfail(reason="BUG-10: missing order can be paid", strict=True)
def test_create_payment_missing_order_not_found(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/payments",
        json={"orderId": 999999, "method": "alipay"},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 404
    assert response.json()["code"] == 40401


@pytest.mark.xfail(reason="BUG-10: duplicate payment is accepted", strict=True)
def test_duplicate_payment_rejected(client, base_url, alice_token):
    first = client.post(
        f"{base_url}/api/payments",
        json={"orderId": 1, "method": "alipay"},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert first.status_code == 200
    second = client.post(
        f"{base_url}/api/payments",
        json={"orderId": 1, "method": "alipay"},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert second.status_code == 409


@pytest.mark.xfail(reason="BUG-10: another user's order can be paid", strict=True)
def test_payment_other_user_forbidden(client, base_url, bob_token):
    response = client.post(
        f"{base_url}/api/payments",
        json={"orderId": 1, "method": "alipay"},
        headers={"Authorization": f"Bearer {bob_token}"},
    )
    assert response.status_code == 403
    assert response.json()["code"] != 0


@pytest.mark.xfail(reason="BUG-11: forged payment callback is accepted", strict=True)
def test_payment_callback_forged_rejected(client, base_url):
    response = client.post(
        f"{base_url}/api/payments/callback",
        json={"orderId": 2, "paymentId": "PAY202609200001", "result": "success"},
    )
    assert response.status_code == 401
    assert response.json()["code"] == 40101


@pytest.mark.xfail(reason="BUG-11: callback for missing order is accepted", strict=True)
def test_payment_callback_missing_order_not_found(client, base_url):
    response = client.post(
        f"{base_url}/api/payments/callback",
        json={"orderId": 999999, "paymentId": "PAY202609200001", "result": "success"},
    )
    assert response.status_code == 404
    assert response.json()["code"] == 40401
