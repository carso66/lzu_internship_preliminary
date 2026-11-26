
def test_order_for_nonexistent_product_returns_404(api):
    response = api.post(
        "/api/orders",
        json={"productId": 999999, "quantity": 1}
    )

    print("\n不存在商品下单:", response.status_code, response.text)
    assert response.status_code == 404


def test_unauthenticated_user_cannot_create_order(api):
    response = api.post(
        "/api/orders",
        auth=False,
        json={"productId": 1, "quantity": 1}
    )

    print("\n未登录创建订单:", response.status_code, response.text)
    assert response.status_code in (401, 403)



def test_zero_stock_product_cannot_be_purchased(api, sut, db):
    """零库存商品不得创建订单，且库存和订单记录保持不变。"""

    import sqlite3

    product_id = 5

    # 将隔离数据库中的商品库存设置为0
    conn = sqlite3.connect(sut["db_path"])
    try:
        conn.execute(
            "UPDATE products SET stock = 0 WHERE id = ?",
            (product_id,)
        )
        conn.commit()
    finally:
        conn.close()

    before_orders = db(
        "SELECT COUNT(*) AS n FROM orders WHERE product_id = ?",
        (product_id,)
    )[0]["n"]

    response = api.post(
        "/api/orders",
        json={"productId": product_id, "quantity": 1}
    )

    after_stock = db(
        "SELECT stock FROM products WHERE id = ?",
        (product_id,)
    )[0]["stock"]

    after_orders = db(
        "SELECT COUNT(*) AS n FROM orders WHERE product_id = ?",
        (product_id,)
    )[0]["n"]

    print("\n========== 零库存购买测试 ==========")
    print("HTTP:", response.status_code)
    print("响应:", response.text)
    print("剩余库存:", after_stock)
    print("订单数量变化:", before_orders, "->", after_orders)

    assert response.status_code == 409
    assert after_stock == 0
    assert after_orders == before_orders
