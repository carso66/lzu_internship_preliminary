# -*- coding: utf-8 -*-
"""订单模块自动化用例：金额、库存、归属、并发。

因 SUT 缺陷必然失败的用例统一用 `xfail(strict=True)` 并注明缺陷编号。
"""
import threading

import pytest
import requests


def _new_product(base_url, headers, price, stock, title):
    """辅助：新建隔离商品，避免用例之间抢同一个库存。"""
    r = requests.post(base_url + "/api/products",
                      json={"title": title, "price": price, "stock": stock,
                            "category": "文具", "description": "自动化夹具"},
                      headers=headers, timeout=10)
    assert r.status_code == 201, r.text
    return r.json()["data"]["id"]


# ---------------------------------------------------------------- 正常
def test_create_order_success(base_url, alice_headers):
    """TC-026 正常下单：库存扣减正确、返回 201、金额为整数分。"""
    pid = _new_product(base_url, alice_headers, 1990, 10, "下单正常用例商品")
    r = requests.post(base_url + "/api/orders", json={"productId": pid, "quantity": 2},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 201
    data = r.json()["data"]
    assert data["quantity"] == 2
    assert data["unitPrice"] == 1990
    assert data["status"] == "created"
    assert data["productId"] == pid

    detail = requests.get(base_url + "/api/products/%s" % pid, timeout=10).json()["data"]
    assert detail["stock"] == 8, "库存应 10 - 2 = 8，实际 %s" % detail["stock"]


def test_my_orders_only_mine(base_url, alice_headers):
    """TC-038 我的订单列表只包含自己的订单，按 id 倒序。"""
    r = requests.get(base_url + "/api/orders/mine", headers=alice_headers, timeout=10)
    assert r.status_code == 200
    items = r.json()["data"]["items"]
    assert items, "alice 应有种子订单"
    assert all(item["userId"] == 2 for item in items)
    ids = [item["id"] for item in items]
    assert ids == sorted(ids, reverse=True)


def test_order_detail_own_order(base_url, alice_headers):
    """TC 补充：能查看自己的订单详情。"""
    pid = _new_product(base_url, alice_headers, 1000, 5, "订单详情用例商品")
    oid = requests.post(base_url + "/api/orders", json={"productId": pid, "quantity": 1},
                        headers=alice_headers, timeout=10).json()["data"]["id"]

    r = requests.get(base_url + "/api/orders/%s" % oid, headers=alice_headers, timeout=10)
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["id"] == oid
    assert data["userId"] == 2
    assert data["paidAt"] is None


# ---------------------------------------------------------------- 异常
def test_order_stock_insufficient(base_url, alice_headers):
    """TC-032 库存不足应 409 + 40901，且库存不被扣减。"""
    pid = _new_product(base_url, alice_headers, 500, 1, "库存不足用例商品")
    r = requests.post(base_url + "/api/orders", json={"productId": pid, "quantity": 2},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 409
    assert r.json()["code"] == 40901

    stock = requests.get(base_url + "/api/products/%s" % pid, timeout=10).json()["data"]["stock"]
    assert stock == 1, "库存不足时不应扣减，实际 %s" % stock


def test_order_product_not_found(base_url, alice_headers):
    """TC-033 商品不存在应 404。"""
    r = requests.post(base_url + "/api/orders", json={"productId": 999999, "quantity": 1},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 404
    assert r.json()["code"] == 40401


def test_order_missing_product_id(base_url, alice_headers):
    """TC-035 缺少 productId 应 400。"""
    r = requests.post(base_url + "/api/orders", json={"quantity": 1},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400
    assert r.json()["code"] == 40001


def test_order_requires_login(base_url):
    """TC-037 未登录下单应 401。"""
    r = requests.post(base_url + "/api/orders",
                      json={"productId": 1, "quantity": 1}, timeout=10)
    assert r.status_code == 401
    assert r.json()["code"] == 40101


def test_order_quantity_missing_defaults_to_one(base_url, alice_headers):
    """TC 补充：不传 quantity 时按文档示例应能成功（服务端默认 1）。"""
    pid = _new_product(base_url, alice_headers, 1000, 5, "默认数量用例商品")
    r = requests.post(base_url + "/api/orders", json={"productId": pid},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 201
    assert r.json()["data"]["quantity"] == 1


# ---------------------------------------------------------------- 边界
def test_order_buy_out_stock(base_url, alice_headers):
    """TC-031 买光库存应成功，库存归零（不是负数）。"""
    pid = _new_product(base_url, alice_headers, 100, 1, "买光库存用例商品")
    r = requests.post(base_url + "/api/orders", json={"productId": pid, "quantity": 1},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 201
    detail = requests.get(base_url + "/api/products/%s" % pid, timeout=10).json()["data"]
    assert detail["stock"] == 0


def test_order_stock_exact_boundary(base_url, alice_headers):
    """TC 补充：quantity 恰好等于库存应成功。"""
    pid = _new_product(base_url, alice_headers, 100, 3, "库存精确边界用例商品")
    r = requests.post(base_url + "/api/orders", json={"productId": pid, "quantity": 3},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 201
    stock = requests.get(base_url + "/api/products/%s" % pid, timeout=10).json()["data"]["stock"]
    assert stock == 0


# ---------------------------------------------------------------- 缺陷关联
@pytest.mark.xfail(strict=True, reason="BUG-08：totalAmount 用浮点计算，返回 3980.0 而非整数 3980 分")
def test_order_amount_is_integer(base_url, alice_headers):
    """TC-026 金额单位是分，应为整数。`1990/100*2` 再 `*100` 会引入浮点。"""
    pid = _new_product(base_url, alice_headers, 1990, 10, "金额类型用例商品")
    r = requests.post(base_url + "/api/orders", json={"productId": pid, "quantity": 2},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 201
    total = r.json()["data"]["totalAmount"]
    assert isinstance(total, int), "金额应为整数分，实际 %r (%s)" % (total, type(total).__name__)


@pytest.mark.xfail(strict=True, reason="BUG-07：quantity 非数字触发 int() 异常，返回 500")
def test_order_quantity_not_numeric(base_url, alice_headers):
    """TC-036 quantity 非数字应 400，不应 500。"""
    r = requests.post(base_url + "/api/orders", json={"productId": 1, "quantity": "两件"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400


@pytest.mark.xfail(strict=True, reason="BUG-09：quantity 未校验正整数，0 被接受")
def test_order_quantity_zero_rejected(base_url, alice_headers):
    """TC-029 quantity=0 应 400（必须是正整数）。"""
    r = requests.post(base_url + "/api/orders", json={"productId": 1, "quantity": 0},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400


@pytest.mark.xfail(strict=True, reason="BUG-09：quantity 为负使 stock - (-5) 变加法，库存反增且金额为负")
def test_order_quantity_negative_rejected(base_url, alice_headers):
    """TC-030 quantity=-5 应 400，且库存不能被越扣越多。"""
    pid = _new_product(base_url, alice_headers, 100, 10, "负数量用例商品")
    before = requests.get(base_url + "/api/products/%s" % pid,
                          timeout=10).json()["data"]["stock"]

    r = requests.post(base_url + "/api/orders", json={"productId": pid, "quantity": -5},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400, "quantity=-5 被接受，返回 %s" % r.status_code

    after = requests.get(base_url + "/api/products/%s" % pid,
                         timeout=10).json()["data"]["stock"]
    assert after == before, "负数量导致库存由 %s 变为 %s" % (before, after)


@pytest.mark.xfail(strict=True, reason="BUG-10：下单未校验商品 status，已下架商品仍可下单")
def test_order_off_shelf_product_rejected(base_url, alice_headers):
    """TC-034 已下架商品（种子 id=7）不可下单，应 409。"""
    r = requests.post(base_url + "/api/orders", json={"productId": 7, "quantity": 1},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 409


@pytest.mark.xfail(strict=True, reason="BUG-08：服务端读取客户端传入的 unitPrice，可 1 分钱下单")
def test_order_ignore_client_unit_price(base_url, alice_headers):
    """TC-027 金额必须由服务端计算，忽略客户端传入的 unitPrice。"""
    pid = _new_product(base_url, alice_headers, 12900, 5, "金额篡改用例商品")
    r = requests.post(base_url + "/api/orders",
                      json={"productId": pid, "quantity": 1, "unitPrice": 1},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 201
    data = r.json()["data"]
    assert data["unitPrice"] == 12900, "unitPrice 应取商品价，实际 %r" % data["unitPrice"]
    assert data["totalAmount"] == 12900, "金额应 12900，实际 %r" % data["totalAmount"]


@pytest.mark.xfail(strict=True, reason="BUG-08：服务端直接采用客户端传入的 totalAmount")
def test_order_ignore_client_total_amount(base_url, alice_headers):
    """TC-028 忽略客户端传入的 totalAmount。"""
    pid = _new_product(base_url, alice_headers, 12900, 5, "总额篡改用例商品")
    r = requests.post(base_url + "/api/orders",
                      json={"productId": pid, "quantity": 1, "totalAmount": 1},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 201
    assert r.json()["data"]["totalAmount"] == 12900


@pytest.mark.xfail(strict=True, reason="BUG-11：订单详情只校验登录不校验归属，可查看他人订单")
def test_order_detail_cross_user_forbidden(base_url, bob_headers):
    """TC-039 不能查看他人订单（种子订单 1 属于 alice）。"""
    r = requests.get(base_url + "/api/orders/1", headers=bob_headers, timeout=10)
    assert r.status_code == 403


@pytest.mark.xfail(strict=True, reason="BUG-03：管理端接口未校验 role，普通用户可拿到全站订单")
def test_admin_orders_forbidden_for_normal_user(base_url, alice_headers):
    """TC-040 管理端订单列表仅 admin 可访问。"""
    r = requests.get(base_url + "/api/admin/orders", headers=alice_headers, timeout=10)
    assert r.status_code == 403


@pytest.mark.xfail(strict=True,
                   reason="BUG-12：检查与扣减非原子，中间隔 150ms，10 并发成功 8 单、库存 -7")
def test_concurrent_order_no_oversell(base_url, alice_headers):
    """TC-041 并发下单不超卖：库存 1 的商品，10 个请求同时下单。"""
    pid = _new_product(base_url, alice_headers, 8990, 1, "并发超卖用例商品")
    n = 10
    results = []
    lock = threading.Lock()
    barrier = threading.Barrier(n)

    def worker():
        barrier.wait()  # 对齐起跑点，确保真并发
        r = requests.post(base_url + "/api/orders",
                          json={"productId": pid, "quantity": 1},
                          headers=alice_headers, timeout=30)
        with lock:
            results.append(r.status_code)

    threads = [threading.Thread(target=worker) for _ in range(n)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    stock = requests.get(base_url + "/api/products/%s" % pid,
                         timeout=10).json()["data"]["stock"]
    success = results.count(201)
    assert stock >= 0, "并发超卖：成功 %d 单，库存 %d（初始 1）" % (success, stock)
    assert success <= 1, "库存 1 的商品被成功下单 %d 次" % success


# ---------------------------------------------------------------- 管理端正向校验
def test_admin_orders_allowed_for_admin(base_url, admin_headers):
    """TC-040 补充：admin 访问管理端应成功（确认不是"一律拦截"）。"""
    r = requests.get(base_url + "/api/admin/orders", headers=admin_headers, timeout=10)
    assert r.status_code == 200
    assert r.json()["code"] == 0
    assert "items" in r.json()["data"]
