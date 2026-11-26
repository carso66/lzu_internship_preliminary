import concurrent.futures
import threading


def test_server_ignores_client_supplied_price_and_total(api, db):
    response = api.post("/api/orders", json={
        "productId": 1, "quantity": 1, "unitPrice": 1, "totalAmount": 1
    })
    assert response.status_code == 201, response.text
    order_id = response.json()["data"]["id"]
    row = db("SELECT unit_price, total_amount FROM orders WHERE id = ?", (order_id,))[0]
    assert row["unit_price"] == 1990
    assert row["total_amount"] == 1990


def test_quantity_must_be_positive_integer(api):
    for quantity in (-1, 0, 1.5):
        response = api.post("/api/orders", json={"productId": 1, "quantity": quantity})
        assert response.status_code in (400, 422), (
            f"quantity={quantity!r} 应被拒绝，实际 HTTP {response.status_code}: {response.text}"
        )


def test_concurrent_last_item_purchase_must_not_oversell(api, db):
    initial = db("SELECT stock FROM products WHERE id = 5")[0]["stock"]
    assert initial == 1, f"商品5初始库存应为1，实际为{initial}"
    barrier = threading.Barrier(2)

    def buy(_):
        barrier.wait(timeout=5)
        return api.post("/api/orders", json={"productId": 5, "quantity": 1})

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(buy, range(2)))

    statuses = [r.status_code for r in responses]
    assert statuses.count(201) == 1 and sum(s in (400, 409) for s in statuses) == 1, (
        f"应一单成功、一单库存不足；状态码={statuses}；响应={[r.text for r in responses]}"
    )
    final_stock = db("SELECT stock FROM products WHERE id = 5")[0]["stock"]
    assert final_stock == 0
