import pytest


def test_unauthenticated_access_denied(client, base_url):
    response = client.get(f"{base_url}/api/users/2")
    assert response.status_code == 401
    assert response.json()["code"] == 40101


@pytest.mark.xfail(reason="BUG-01: predictable token is accepted", strict=True)
def test_predictable_token_rejected(client, base_url):
    response = client.get(
        f"{base_url}/api/users/2",
        headers={"Authorization": "Bearer tk_2"},
    )
    assert response.status_code == 401
    assert response.json()["code"] == 40101


@pytest.mark.xfail(reason="BUG-02: horizontal privilege escalation", strict=True)
def test_horizontal_privilege_escalation(client, base_url, alice_token):
    response = client.get(
        f"{base_url}/api/users/3",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 403
    assert response.json()["code"] != 0


@pytest.mark.xfail(reason="BUG-03: vertical privilege escalation", strict=True)
def test_vertical_privilege_escalation(client, base_url, alice_token):
    response = client.get(
        f"{base_url}/api/admin/orders",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 403
    assert response.json()["code"] != 0


@pytest.mark.xfail(reason="BUG-06: SQL injection", strict=True)
def test_sql_injection_neutralized(client, base_url):
    response = client.get(
        f"{base_url}/api/products",
        params={"keyword": "' OR 1=1 --"},
    )
    assert response.status_code == 200
    assert response.json()["data"]["total"] == 0


@pytest.mark.xfail(reason="BUG-08: amount tampering", strict=True)
def test_amount_tampering_ignored(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/orders",
        json={
            "productId": 1,
            "quantity": 2,
            "unitPrice": 1,
            "totalAmount": 1,
        },
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 201
    assert response.json()["data"]["totalAmount"] == 3980


@pytest.mark.xfail(reason="BUG-11: forged payment callback", strict=True)
def test_forged_payment_callback_rejected(client, base_url):
    response = client.post(
        f"{base_url}/api/payments/callback",
        json={
            "orderId": 2,
            "paymentId": "PAY202609200001",
            "result": "success",
        },
    )
    assert response.status_code == 401
    assert response.json()["code"] == 40101


@pytest.mark.xfail(reason="BUG-12: stored XSS in review page", strict=True)
def test_review_xss_escaped(client, base_url, alice_token):
    client.post(
        f"{base_url}/api/product-reviews",
        json={
            "productId": 1,
            "content": "<img src=x onerror=alert(1)>",
            "rating": 5,
        },
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    response = client.get(
        f"{base_url}/api/product-reviews",
        params={"productId": 1},
    )
    assert response.status_code == 200
    assert "&lt;img" in response.text


@pytest.mark.xfail(reason="BUG-08: negative quantity is accepted", strict=True)
def test_negative_quantity_order_rejected(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/orders",
        json={
            "productId": 3,
            "quantity": -1,
        },
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 400
    assert response.json()["code"] == 40001


@pytest.mark.xfail(reason="BUG-12: product title XSS is not escaped", strict=True)
def test_product_title_xss_escaped(client, base_url, alice_token):
    title = "<img src=x onerror=alert('title-xss')>"
    product_response = client.post(
        f"{base_url}/api/products",
        json={
            "title": title,
            "price": 1990,
            "stock": 99,
            "category": "数码",
            "description": "自动创建",
        },
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert product_response.status_code == 201
    product_id = product_response.json()["data"]["id"]

    page_response = client.get(
        f"{base_url}/api/product-reviews",
        params={"productId": product_id},
    )
    assert page_response.status_code == 200
    assert "&lt;img" in page_response.text


@pytest.mark.xfail(reason="BUG-11: forged paymentId is accepted", strict=True)
def test_payment_callback_mismatched_payment_id_rejected(client, base_url):
    response = client.post(
        f"{base_url}/api/payments/callback",
        json={
            "orderId": 2,
            "paymentId": "PAY202609999999",
            "result": "success",
        },
    )
    assert response.status_code in (401, 403)
    assert response.json()["data"] is None
