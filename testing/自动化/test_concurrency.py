from concurrent.futures import ThreadPoolExecutor

import requests
import pytest


def test_product_5_initial_stock_is_one(client, base_url):
    response = client.get(f"{base_url}/api/products/5")
    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["stock"] == 1


@pytest.mark.xfail(reason="BUG-14: concurrent purchase can oversell", strict=True)
def test_concurrent_purchase_product_5(client, base_url, alice_token):
    initial_stock = client.get(f"{base_url}/api/products/5").json()["data"]["stock"]
    assert initial_stock == 1

    payload = {"productId": 5, "quantity": 1}
    headers = {"Authorization": f"Bearer {alice_token}"}

    def post_order():
        with requests.Session() as session:
            return session.post(
                f"{base_url}/api/orders",
                json=payload,
                headers=headers,
            )

    with ThreadPoolExecutor(max_workers=10) as executor:
        responses = list(executor.map(lambda _: post_order(), range(10)))

    success_count = sum(1 for response in responses if response.status_code == 201)
    final_stock = client.get(f"{base_url}/api/products/5").json()["data"]["stock"]

    assert success_count == 1, f"success_count={success_count}, final_stock={final_stock}"
    assert final_stock == 0, f"success_count={success_count}, final_stock={final_stock}"


@pytest.mark.xfail(reason="BUG-10: duplicate payments are accepted", strict=True)
def test_concurrent_duplicate_payments_for_same_order(client, base_url, alice_token):
    order_response = client.post(
        f"{base_url}/api/orders",
        json={"productId": 3, "quantity": 1},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert order_response.status_code == 201
    order_id = order_response.json()["data"]["id"]
    payload = {"orderId": order_id, "method": "alipay"}

    def post_payment():
        with requests.Session() as session:
            return session.post(
                f"{base_url}/api/payments",
                json=payload,
                headers={"Authorization": f"Bearer {alice_token}"},
            )

    with ThreadPoolExecutor(max_workers=10) as executor:
        responses = list(executor.map(lambda _: post_payment(), range(10)))

    success_count = sum(1 for response in responses if response.status_code == 200)
    assert success_count <= 1, f"success_count={success_count}"


@pytest.mark.xfail(
    reason="BUG-10 / BUG-11: duplicate payments and forged callbacks are accepted",
    strict=True,
)
def test_payment_callback_mixed_race(client, base_url, alice_token):
    order_response = client.post(
        f"{base_url}/api/orders",
        json={"productId": 4, "quantity": 1},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert order_response.status_code == 201
    order_id = order_response.json()["data"]["id"]
    payment_payload = {"orderId": order_id, "method": "alipay"}
    callback_payload = {
        "orderId": order_id,
        "paymentId": "PAY202609999999",
        "result": "success",
    }

    def post_payment():
        with requests.Session() as session:
            return session.post(
                f"{base_url}/api/payments",
                json=payment_payload,
                headers={"Authorization": f"Bearer {alice_token}"},
            )

    def post_callback():
        with requests.Session() as session:
            return session.post(
                f"{base_url}/api/payments/callback",
                json=callback_payload,
            )

    with ThreadPoolExecutor(max_workers=10) as executor:
        payment_jobs = [executor.submit(post_payment) for _ in range(5)]
        callback_jobs = [executor.submit(post_callback) for _ in range(5)]
        payment_responses = [job.result() for job in payment_jobs]
        callback_responses = [job.result() for job in callback_jobs]

    payment_success_count = sum(1 for response in payment_responses if response.status_code == 200)
    callback_accepted_count = sum(1 for response in callback_responses if response.status_code == 200)

    order_status_response = client.get(
        f"{base_url}/api/orders/{order_id}",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    final_status = order_status_response.json()["data"]["status"]

    assert payment_success_count <= 1, (
        f"payment_success_count={payment_success_count}, "
        f"callback_accepted_count={callback_accepted_count}"
    )
    assert callback_accepted_count == 0, (
        f"payment_success_count={payment_success_count}, "
        f"callback_accepted_count={callback_accepted_count}, "
        f"final_status={final_status}"
    )
