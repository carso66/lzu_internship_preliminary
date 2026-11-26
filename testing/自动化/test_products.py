# -*- coding: utf-8 -*-
"""商品接口：列表、详情、筛选、分页、发布、上下架。"""

import pytest
import requests


def test_product_list_default(base_url):
    """TC-007 正常：不传 status 时只返回在售商品，共 11 个。"""
    resp = requests.get(base_url + "/api/products", timeout=5)

    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0
    data = body["data"]
    assert data["total"] == 11
    assert data["page"] == 1
    assert data["pageSize"] == 10
    assert len(data["items"]) == 10
    assert all(item["status"] == "on" for item in data["items"])


def test_product_list_page_size(base_url):
    """TC-008 正常：pageSize=3 时每页 3 条，total 仍为总数。"""
    resp = requests.get(base_url + "/api/products", params={"page": 1, "pageSize": 3}, timeout=5)

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["total"] == 11
    assert data["pageSize"] == 3
    assert len(data["items"]) == 3
    assert [item["id"] for item in data["items"]] == [1, 2, 3]


def test_product_list_filter_off(base_url):
    """TC-009 正常：status=off 只返回已下架商品，种子库下仅 id=7。"""
    resp = requests.get(base_url + "/api/products", params={"status": "off"}, timeout=5)

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["total"] == 1
    assert data["items"][0]["id"] == 7
    assert data["items"][0]["status"] == "off"


def test_product_list_keyword(base_url):
    """TC-010 正常：keyword 命中商品标题，返回唯一一条。"""
    resp = requests.get(base_url + "/api/products", params={"keyword": "耳机"}, timeout=5)

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["total"] == 1
    assert data["items"][0]["id"] == 1
    assert "耳机" in data["items"][0]["title"]


def test_product_detail_ok(base_url):
    """TC-011 正常：查询存在的商品，字段完整且价格单位为分。"""
    resp = requests.get(base_url + "/api/products/1", timeout=5)

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["id"] == 1
    assert data["title"] == "无线蓝牙耳机"
    assert data["price"] == 1990
    assert data["stock"] == 12
    assert data["category"] == "数码"
    assert data["sellerPhone"] == "13800138001"
    assert data["description"]


@pytest.mark.parametrize("product_id", [13, 99999])
def test_product_detail_not_found(base_url, product_id):
    """TC-012 异常：商品不存在返回 404 + 40401，不能是 200 或 500。"""
    resp = requests.get(base_url + "/api/products/%d" % product_id, timeout=5)

    assert resp.status_code == 404
    assert resp.json()["code"] == 40401


@pytest.mark.xfail(strict=True, reason="BUG-15 id 非数字返回 500 而非文档承诺的 400")
def test_product_detail_non_numeric_id(base_url):
    """TC-013 异常：文档 5.6 承诺 id 不是数字时返回 400。

    实际 int("abc") 抛 ValueError 被兜底成 500 50000「服务内部错误」。
    """
    resp = requests.get(base_url + "/api/products/abc", timeout=5)

    assert resp.status_code == 400
    assert resp.json()["code"] not in (0, 50000)


@pytest.mark.parametrize("page_size", [0, 51])
@pytest.mark.xfail(strict=True, reason="BUG-16 pageSize 无 1~50 校验，0 与 51 均返回 200")
def test_product_list_page_size_out_of_range(base_url, page_size):
    """TC-014 边界：文档 5.5 承诺 pageSize 范围 1~50，超出应 400。"""
    resp = requests.get(base_url + "/api/products", params={"pageSize": page_size}, timeout=5)

    assert resp.status_code == 400


@pytest.mark.xfail(strict=True, reason="BUG-16 page=0 返回 200，负偏移量未校验")
def test_product_list_page_zero(base_url):
    """TC-015 边界：page 从 1 开始，page=0 应被拒绝而不是返回第一页。"""
    resp = requests.get(base_url + "/api/products", params={"page": 0, "pageSize": 3}, timeout=5)

    assert resp.status_code == 400


@pytest.mark.xfail(strict=True, reason="BUG-13 keyword 直接拼接进 SQL，可注入绕过在售过滤")
def test_product_list_keyword_injection(base_url):
    """TC-016 异常：keyword 里的 SQL 片段不应改变过滤条件。

    `' OR 1=1 -- ` 会让 WHERE 变成恒真，连已下架的 id=7 一起返回（total=12），
    证明 keyword 未参数化。
    """
    resp = requests.get(base_url + "/api/products", params={"keyword": "' OR 1=1 -- "}, timeout=5)

    assert resp.status_code == 200
    data = resp.json()["data"]
    # 注入生效时 `-- ` 会注释掉后面的 p.status = 'on'，已下架的 id=7 混进结果
    assert all(item["status"] == "on" for item in data["items"])
    assert data["total"] <= 11


def test_create_product_ok(base_url, alice_token):
    """TC-017 正常：合法参数发布商品返回 201，且 sellerId 为当前用户。"""
    payload = {
        "title": "测试商品-自动化",
        "price": 1990,
        "stock": 10,
        "category": "文具",
        "description": "由 pytest 创建",
    }
    resp = requests.post(
        base_url + "/api/products",
        json=payload,
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 201
    data = resp.json()["data"]
    assert data["title"] == payload["title"]
    assert data["price"] == 1990
    assert data["stock"] == 10
    assert data["status"] == "on"
    assert data["sellerId"] == 2


def test_create_product_without_token(base_url):
    """TC-018 异常：未登录发布商品必须 401。"""
    resp = requests.post(
        base_url + "/api/products",
        json={"title": "x", "price": 100, "stock": 1, "category": "文具"},
        timeout=5,
    )

    assert resp.status_code == 401
    assert resp.json()["code"] == 40101


@pytest.mark.parametrize(
    "payload, case",
    [
        ({"title": "", "price": 100, "stock": 1, "category": "文具"}, "title 为空"),
        ({"title": "x" * 51, "price": 100, "stock": 1, "category": "文具"}, "title 51 字"),
        ({"title": "x", "price": 0, "stock": 1, "category": "文具"}, "price 为 0"),
        ({"title": "x", "price": -1, "stock": 1, "category": "文具"}, "price 为负"),
        ({"title": "x", "price": 19.9, "stock": 1, "category": "文具"}, "price 为小数"),
        ({"title": "x", "price": 100, "stock": -1, "category": "文具"}, "stock 为负"),
        ({"title": "x", "price": 100, "stock": 1000, "category": "文具"}, "stock 1000"),
        ({"title": "x", "price": 100, "stock": 1, "category": "电器"}, "分类不在白名单"),
    ],
)
def test_create_product_invalid(base_url, alice_token, payload, case):
    """TC-019 异常/边界：发布商品的字段校验（8 组参数化）。"""
    resp = requests.post(
        base_url + "/api/products",
        json=payload,
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 400, case
    assert resp.json()["code"] == 40001, case


def test_product_status_owner_ok(base_url, alice_token):
    """TC-020 正常：卖家上下架自己发布的商品。"""
    created = requests.post(
        base_url + "/api/products",
        json={"title": "待下架商品", "price": 500, "stock": 3, "category": "文具"},
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    ).json()["data"]

    resp = requests.patch(
        base_url + "/api/products/%d/status" % created["id"],
        json={"status": "off"},
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 200
    assert resp.json()["data"] == {"id": created["id"], "status": "off"}


@pytest.mark.xfail(strict=True, reason="BUG-05 未校验商品归属，可上下架别人的商品")
def test_product_status_not_owner_forbidden(base_url, alice_token):
    """TC-021 异常：文档 5.8 承诺只能上下架自己发布的商品。

    alice 的 userId 是 2，种子商品 id=3《金字塔原理》的 seller_id 是 3（bob），
    与 alice 无关，用 alice 的 token 直接改它应当被拒绝。
    """
    resp = requests.patch(
        base_url + "/api/products/3/status",
        json={"status": "off"},
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 403
