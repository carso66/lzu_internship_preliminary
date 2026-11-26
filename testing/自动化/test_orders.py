import pytest


@pytest.mark.xfail(reason="BUG-13: order amount has floating point precision error", strict=True)
def test_create_order_success(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/orders",
        json={"productId": 1, "quantity": 2},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 201
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["totalAmount"] == 3980


@pytest.mark.xfail(reason="BUG-08: quantity=0 is accepted", strict=True)
def test_create_order_quantity_zero_rejected(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/orders",
        json={"productId": 1, "quantity": 0},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 400
    assert response.json()["code"] == 40001


def test_create_order_stock_exhausted(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/orders",
        json={"productId": 5, "quantity": 2},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 409
    assert response.json()["code"] == 40901


@pytest.mark.xfail(reason="BUG-08: client supplied amount is accepted", strict=True)
def test_create_order_amount_from_client_ignored(client, base_url, alice_token):
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


@pytest.mark.xfail(reason="BUG-08: off-shelf product can be ordered", strict=True)
def test_create_order_off_shelf_product_rejected(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/orders",
        json={"productId": 7, "quantity": 1},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 409
    assert response.json()["code"] == 40901


def test_get_order_detail_success(client, base_url, alice_token):
    response = client.get(
        f"{base_url}/api/orders/1",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["id"] == 1


@pytest.mark.xfail(reason="BUG-09: order detail has horizontal authorization flaw", strict=True)
def test_get_other_user_order_forbidden(client, base_url, bob_token):
    response = client.get(
        f"{base_url}/api/orders/1",
        headers={"Authorization": f"Bearer {bob_token}"},
    )
    assert response.status_code == 403
    assert response.json()["code"] != 0


def test_get_missing_order_not_found(client, base_url, alice_token):
    response = client.get(
        f"{base_url}/api/orders/999999",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 404
    assert response.json()["code"] == 40401


def test_my_orders_success(client, base_url, alice_token):
    response = client.get(
        f"{base_url}/api/orders/mine",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["total"] >= 1
