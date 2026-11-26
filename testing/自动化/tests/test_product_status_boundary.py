
def test_update_nonexistent_product_returns_404(api):
    """修改不存在的商品应返回404。"""

    response = api.request(
        "PATCH",
        "/api/products/999999/status",
        json={"status": "off"}
    )

    print("\n========== 不存在商品测试 ==========")
    print("HTTP:", response.status_code)
    print("响应:", response.text)

    assert response.status_code == 404


def test_unauthenticated_user_cannot_update_product(api, db):
    """未登录用户不能修改商品状态。"""

    before = db(
        "SELECT status FROM products WHERE id = 1"
    )[0]["status"]

    response = api.request(
        "PATCH",
        "/api/products/1/status",
        auth=False,
        json={"status": "off"}
    )

    after = db(
        "SELECT status FROM products WHERE id = 1"
    )[0]["status"]

    print("\n========== 未登录修改商品测试 ==========")
    print("HTTP:", response.status_code)
    print("修改前:", before)
    print("修改后:", after)

    assert response.status_code in (401, 403)
    assert after == before


def test_other_seller_product_status_cannot_be_changed(api, db):
    """普通用户不得修改其他卖家的商品状态。"""

    product = db(
        "SELECT id, seller_id, status FROM products WHERE id = 1"
    )[0]

    assert product["seller_id"] != 2
    original_status = product["status"]

    results = []

    for status in ("off", "on"):
        response = api.request(
            "PATCH",
            f"/api/products/{product['id']}/status",
            json={"status": status}
        )
        results.append(response.status_code)

    final_status = db(
        "SELECT status FROM products WHERE id = ?",
        (product["id"],)
    )[0]["status"]

    print("\n========== 商品越权修改安全测试 ==========")
    print("HTTP状态码:", results)
    print("原始状态:", original_status)
    print("最终状态:", final_status)

    assert results == [403, 403]
    assert final_status == original_status
