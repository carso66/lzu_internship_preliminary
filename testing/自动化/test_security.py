from __future__ import annotations

import sqlite3
import threading
from concurrent.futures import ThreadPoolExecutor

import requests


def db_rows(db_path, sql, params=()):
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    try:
        return [dict(row) for row in conn.execute(sql, params).fetchall()]
    finally:
        conn.close()


def test_user_cannot_read_another_users_profile(sut_server):
    r = requests.get(
        sut_server["base_url"] + "/api/users/3",
        headers={"Authorization": "Bearer tk_2"},
        timeout=5,
    )
    assert r.status_code == 403


def test_normal_user_cannot_read_admin_order_list(sut_server):
    r = requests.get(
        sut_server["base_url"] + "/api/admin/orders",
        headers={"Authorization": "Bearer tk_2"},
        timeout=5,
    )
    assert r.status_code == 403


def test_user_cannot_read_another_users_order(sut_server):
    r = requests.get(
        sut_server["base_url"] + "/api/orders/2",
        headers={"Authorization": "Bearer tk_2"},
        timeout=5,
    )
    assert r.status_code == 403


def test_unsigned_payment_callback_must_not_mark_order_paid(sut_server):
    r = requests.post(
        sut_server["base_url"] + "/api/payments/callback",
        json={"orderId": 1, "paymentId": "FAKE-AUTO-001", "result": "success"},
        timeout=5,
    )
    assert r.status_code in (400, 401, 403)
    rows = db_rows(
        sut_server["db_path"],
        "SELECT status, paid_at FROM orders WHERE id = 1",
    )
    assert rows and rows[0]["status"] == "created"
    assert rows[0]["paid_at"] is None


def test_order_price_must_be_calculated_from_database(sut_server):
    r = requests.post(
        sut_server["base_url"] + "/api/orders",
        headers={"Authorization": "Bearer tk_2"},
        json={"productId": 1, "quantity": 1, "unitPrice": 1, "totalAmount": 1},
        timeout=5,
    )
    assert r.status_code == 201
    data = r.json()["data"]
    assert data["unitPrice"] == 1990
    assert data["totalAmount"] == 1990


def test_order_quantity_must_be_positive_integer(sut_server):
    for quantity in (0, -1):
        r = requests.post(
            sut_server["base_url"] + "/api/orders",
            headers={"Authorization": "Bearer tk_2"},
            json={"productId": 1, "quantity": quantity},
            timeout=5,
        )
        assert r.status_code == 400


def test_concurrent_purchase_must_not_oversell(sut_server):
    url = sut_server["base_url"] + "/api/orders"
    headers = {"Authorization": "Bearer tk_2"}
    barrier = threading.Barrier(2)

    def buy():
        barrier.wait(timeout=5)
        return requests.post(
            url, headers=headers,
            json={"productId": 5, "quantity": 1},
            timeout=10,
        )

    with ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(lambda _: buy(), range(2)))

    assert sorted(r.status_code for r in responses) == [201, 409]

    product = db_rows(
        sut_server["db_path"], "SELECT stock FROM products WHERE id = 5"
    )[0]
    orders = db_rows(
        sut_server["db_path"], "SELECT id FROM orders WHERE product_id = 5"
    )
    assert product["stock"] == 0
    assert len(orders) == 1


def test_product_search_input_must_not_change_sql_logic(sut_server):
    r = requests.get(
        sut_server["base_url"] + "/api/products",
        params={"keyword": "' OR 1=1 --"},
        timeout=5,
    )
    assert r.status_code == 200
    items = r.json()["data"]["items"]
    assert all(item.get("status") != "off" for item in items)


def test_review_html_must_escape_user_content(sut_server):
    created = requests.post(
        sut_server["base_url"] + "/api/product-reviews",
        headers={"Authorization": "Bearer tk_2"},
        json={
            "productId": 1,
            "content": "<script>alert(1)</script>",
            "rating": 5,
        },
        timeout=5,
    )
    assert created.status_code == 201

    page = requests.get(
        sut_server["base_url"] + "/api/product-reviews",
        params={"productId": 1},
        timeout=5,
    )
    assert page.status_code == 200
    assert "<script>alert(1)</script>" not in page.text
    assert "&lt;script&gt;alert(1)&lt;/script&gt;" in page.text


def test_user_cannot_create_payment_for_another_users_order(sut_server):
    r = requests.post(
        sut_server["base_url"] + "/api/payments",
        headers={"Authorization": "Bearer tk_3"},
        json={"orderId": 1, "method": "alipay"},
        timeout=5,
    )
    assert r.status_code == 403
    payments = db_rows(
        sut_server["db_path"], "SELECT id FROM payments WHERE order_id = 1"
    )
    assert payments == []
