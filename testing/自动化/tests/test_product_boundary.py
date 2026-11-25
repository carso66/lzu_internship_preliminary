
def test_product_detail_for_nonexistent_id_returns_404(api):
    response = api.get("/api/products/999999", auth=False)

    print("\n不存在商品详情:", response.status_code, response.text)

    assert response.status_code == 404


def test_product_search_returns_only_matching_products(api):
    response = api.get(
        "/api/products",
        auth=False,
        params={"keyword": "无线蓝牙耳机"}
    )

    assert response.status_code == 200

    data = response.json()["data"]
    items = data["items"]

    print("\n商品搜索结果:", items)

    assert all("无线蓝牙耳机" in item["title"] for item in items)


def test_invalid_product_price_is_rejected(api):
    response = api.post(
        "/api/products",
        json={
            "title": "价格边界测试商品",
            "price": -100,
            "stock": 10,
            "category": "数码"
        }
    )

    print("\n非法商品价格:", response.status_code, response.text)

    assert response.status_code == 400
