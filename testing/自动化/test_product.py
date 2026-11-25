# -*- coding: utf-8 -*-
"""商品模块自动化用例（正常 / 异常 / 边界 / 安全）。

所有涉及列表总数的断言都不依赖"全站商品个数"——前面的用例会新建商品，
因此总数会增长。需要固定条数的地方一律用"先建 N 个隔离商品再筛选"的方式。
"""
import pytest
import requests


def _new_product(base_url, headers, price, stock, title):
    r = requests.post(base_url + "/api/products",
                      json={"title": title, "price": price, "stock": stock,
                            "category": "文具", "description": "自动化夹具"},
                      headers=headers, timeout=10)
    assert r.status_code == 201, r.text
    return r.json()["data"]["id"]


# ---------------------------------------------------------------- 正常
def test_product_list_default_only_on_sale(base_url):
    """TC-011 不传 status 时只返回在售商品（不校验具体条数，避免用例间相互影响）。"""
    r = requests.get(base_url + "/api/products", timeout=10)
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["total"] >= 11
    assert all(item["status"] == "on" for item in data["items"])


def test_product_list_with_status_off(base_url):
    """TC-011 补充：显式传 status=off 能查到已下架商品（种子 id=7）。"""
    r = requests.get(base_url + "/api/products", params={"status": "off"}, timeout=10)
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["total"] >= 1
    assert all(item["status"] == "off" for item in data["items"])


def test_product_detail(base_url):
    """TC-017 查询商品详情字段完整。"""
    r = requests.get(base_url + "/api/products/1", timeout=10)
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["id"] == 1
    assert data["price"] == 1990
    assert "title" in data and "stock" in data and "description" in data


def test_product_search_by_keyword(base_url, alice_headers):
    """TC-012 关键词搜索命中（用隔离商品，不依赖种子标题）。"""
    marker = "关键词检索夹具ABCD"
    pid = _new_product(base_url, alice_headers, 1000, 3, marker)

    r = requests.get(base_url + "/api/products", params={"keyword": marker}, timeout=10)
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["total"] == 1, "应精确命中 1 条，实际 %s" % data["total"]
    assert data["items"][0]["id"] == pid


def test_create_product_success(base_url, alice_headers):
    """TC-020 发布商品成功，返回 201 且 sellerId 为当前用户。"""
    payload = {"title": "自动化测试商品", "price": 1000, "stock": 5,
               "category": "文具", "description": "由 pytest 创建"}
    r = requests.post(base_url + "/api/products", json=payload,
                      headers=alice_headers, timeout=10)
    assert r.status_code == 201
    data = r.json()["data"]
    assert data["title"] == "自动化测试商品"
    assert data["price"] == 1000
    assert data["status"] == "on"
    assert data["sellerId"] == 2


# ---------------------------------------------------------------- 边界
def test_page_size_upper_bound(base_url):
    """TC-014 pageSize=50 是合法上边界，应正常返回。"""
    r = requests.get(base_url + "/api/products", params={"pageSize": 50}, timeout=10)
    assert r.status_code == 200
    assert r.json()["data"]["pageSize"] == 50


def test_product_price_zero_rejected(base_url, alice_headers):
    """TC-022 price=0 应拒绝（价格必须为正整数分）。"""
    r = requests.post(base_url + "/api/products",
                      json={"title": "零元购", "price": 0, "stock": 1, "category": "文具"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400
    assert r.json()["code"] == 40001


def test_product_stock_upper_bound_rejected(base_url, alice_headers):
    """TC-023 stock=1000 超过 0~999，应拒绝。"""
    r = requests.post(base_url + "/api/products",
                      json={"title": "库存超限", "price": 100, "stock": 1000, "category": "文具"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400
    assert r.json()["code"] == 40001


def test_product_stock_upper_bound_accepted(base_url, alice_headers):
    """TC-023 补充：stock=999 是合法上边界。"""
    r = requests.post(base_url + "/api/products",
                      json={"title": "库存上边界", "price": 100, "stock": 999, "category": "文具"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 201
    assert r.json()["data"]["stock"] == 999


def test_product_not_found(base_url):
    """TC-018 不存在的商品返回 404 + 40401。"""
    r = requests.get(base_url + "/api/products/999999", timeout=10)
    assert r.status_code == 404
    assert r.json()["code"] == 40401


# ---------------------------------------------------------------- 异常
def test_product_price_decimal_rejected(base_url, alice_headers):
    """TC-021 价格为小数（19.9 元）应拒绝，价格单位是整数分。"""
    r = requests.post(base_url + "/api/products",
                      json={"title": "小数价格", "price": 19.9, "stock": 1, "category": "文具"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400
    assert r.json()["code"] == 40001


def test_product_category_invalid(base_url, alice_headers):
    """TC-024 分类不在五选一范围内应拒绝。"""
    r = requests.post(base_url + "/api/products",
                      json={"title": "非法分类", "price": 100, "stock": 1, "category": "家电"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400
    assert r.json()["code"] == 40001


def test_product_title_too_long_rejected(base_url, alice_headers):
    """TC 补充：title 超过 50 字应拒绝。"""
    r = requests.post(base_url + "/api/products",
                      json={"title": "长" * 51, "price": 100, "stock": 1, "category": "文具"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400
    assert r.json()["code"] == 40001


def test_create_product_requires_login(base_url):
    """TC 补充：未登录发布商品应 401。"""
    r = requests.post(base_url + "/api/products",
                      json={"title": "匿名商品", "price": 100, "stock": 1, "category": "文具"},
                      timeout=10)
    assert r.status_code == 401
    assert r.json()["code"] == 40101


# ---------------------------------------------------------------- 缺陷关联
@pytest.mark.xfail(strict=True, reason="BUG-06：pageSize 未校验 1~50 范围，0/51/-1 均返回 200")
def test_page_size_out_of_range(base_url):
    """TC-015 文档承诺 pageSize 范围 1~50，超出应 400。"""
    for value in (0, 51, -1):
        r = requests.get(base_url + "/api/products", params={"pageSize": value}, timeout=10)
        assert r.status_code == 400, "pageSize=%s 返回 %s" % (value, r.status_code)


@pytest.mark.xfail(strict=True, reason="BUG-07：path 参数非数字时 int() 抛异常，返回 500 而非 400")
def test_product_detail_non_numeric_id(base_url):
    """TC-019 文档承诺 id 非数字返回 400，不是 500。"""
    r = requests.get(base_url + "/api/products/abc", timeout=10)
    assert r.status_code == 400
    assert r.json()["code"] == 40001


@pytest.mark.xfail(strict=True, reason="BUG-05：keyword 直接拼进 SQL，' OR '1'='1 使条件恒真")
def test_keyword_sql_injection(base_url):
    """TC-013 keyword 含单引号应按字面匹配，不得改变 SQL 结构。"""
    r = requests.get(base_url + "/api/products",
                     params={"keyword": "' OR '1'='1", "status": "on"}, timeout=10)
    assert r.status_code == 200
    assert r.json()["data"]["total"] == 0


@pytest.mark.xfail(strict=True, reason="BUG-04：未校验 seller_id，可下架他人商品")
def test_cannot_change_others_product_status(base_url, alice_headers):
    """TC-025 不能上下架他人商品（商品 1 的 sellerId=1）。"""
    r = requests.patch(base_url + "/api/products/1/status", json={"status": "off"},
                       headers=alice_headers, timeout=10)
    assert r.status_code == 403
