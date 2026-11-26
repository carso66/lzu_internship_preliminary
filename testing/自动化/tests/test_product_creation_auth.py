
def test_unauthenticated_user_cannot_create_product(api):
    """未登录用户不得创建商品。"""

    response = api.post(
        "/api/products",
        auth=False,
        json={
            "title": "自动化测试商品",
            "price": 100,
            "stock": 10,
            "category": "数码"
        }
    )

    print("\n========== 未登录创建商品测试 ==========")
    print("HTTP:", response.status_code)
    print("响应:", response.text)

    assert response.status_code in (401, 403)


def test_client_cannot_spoof_product_seller_id(api, db):
    """客户端不能伪造商品卖家身份。"""

    response = api.post(
        "/api/products",
        json={
            "title": "卖家身份伪造测试商品",
            "price": 100,
            "stock": 10,
            "category": "数码",
            "sellerId": 1,
            "seller_id": 1
        }
    )

    print("\n========== 商品卖家身份验证 ==========")
    print("HTTP:", response.status_code)
    print("响应:", response.text)

    assert response.status_code == 201, response.text

    product_id = response.json()["data"]["id"]

    product = db(
        "SELECT id, seller_id FROM products WHERE id = ?",
        (product_id,)
    )[0]

    print("实际数据库记录:", product)

    assert product["seller_id"] == 2
