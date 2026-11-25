def test_product_search_keyword_is_not_sql_injection(api):
    response = api.get("/api/products", auth=False, params={"keyword": "' OR 1=1 --"})
    assert response.status_code == 200, response.text
    data = response.json()["data"]
    assert data["total"] < 12, "注入关键词不应绕过搜索条件并返回全部12件商品"
    assert all(item.get("status", "on") == "on" for item in data["items"])
