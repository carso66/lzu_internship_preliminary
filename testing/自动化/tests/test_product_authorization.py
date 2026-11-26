def test_normal_user_cannot_change_another_sellers_product_status(api, db):
    """普通用户不得修改其他卖家的商品状态。"""
    products = db(
        "SELECT id, seller_id, status FROM products WHERE seller_id != 2 LIMIT 1"
    )
    assert products, "没有找到其他卖家的测试商品"

    product = products[0]
    before = product["status"]
    target_status = "off" if before == "on" else "on"

    response = api.request(
        "PATCH",
        f"/api/products/{product['id']}/status",
        json={"status": target_status},
    )

    after = db(
        "SELECT status FROM products WHERE id = ?",
        (product["id"],),
    )[0]["status"]

    assert response.status_code == 403, (
        f"普通用户不应修改其他卖家的商品，实际 HTTP "
        f"{response.status_code}: {response.text}"
    )
    assert after == before, (
        f"越权请求不应改变商品状态，原状态={before}，当前状态={after}"
    )
