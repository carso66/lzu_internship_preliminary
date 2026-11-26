# -*- coding: utf-8 -*-
"""订单接口：下单、我的订单、订单详情。

金额单位统一为「分」。文档 5.9 明确总价应由服务端用商品单价计算，
客户端传入的金额字段不应生效。库存与金额是最容易出问题的两块，
本文件里每条断言都尽量落到具体数值上。
"""

import threading

import pytest
import requests


def _create_order(base_url, token, **payload):
    return requests.post(
        base_url + "/api/orders",
        json=payload,
        headers={"Authorization": "Bearer " + token},
        timeout=5,
    )


def test_create_order_ok(base_url, alice_token):
    """TC-022 正常：下单返回 201，总价 = 单价 × 数量，库存同步扣减。"""
    resp = _create_order(base_url, alice_token, productId=10, quantity=2)

    assert resp.status_code == 201
    data = resp.json()["data"]
    assert data["productId"] == 10
    assert data["productTitle"] == "A5 硬壳笔记本"
    assert data["quantity"] == 2
    assert data["unitPrice"] == 2350
    assert data["totalAmount"] == 4700
    assert data["status"] == "created"
    assert data["orderNo"].startswith("SO")

    stock = requests.get(base_url + "/api/products/10", timeout=5).json()["data"]["stock"]
    assert stock == 38


def test_create_order_default_quantity(base_url, alice_token):
    """TC-023 边界：不传 quantity 时按 1 件下单。"""
    resp = _create_order(base_url, alice_token, productId=12)

    assert resp.status_code == 201
    data = resp.json()["data"]
    assert data["quantity"] == 1
    assert data["totalAmount"] == 3290


def test_create_order_stock_insufficient(base_url, alice_token):
    """TC-024 异常：购买数量超过库存必须拒绝，且不能扣库存。"""
    resp = _create_order(base_url, alice_token, productId=5, quantity=2)

    assert resp.status_code == 409
    assert resp.json()["code"] == 40901

    stock = requests.get(base_url + "/api/products/5", timeout=5).json()["data"]["stock"]
    assert stock == 1


def test_create_order_sold_out_boundary(base_url, alice_token):
    """TC-025 边界：数量恰好等于库存时允许下单，库存归零。"""
    resp = _create_order(base_url, alice_token, productId=11, quantity=6)

    assert resp.status_code == 201
    assert resp.json()["data"]["totalAmount"] == 9900 * 6

    stock = requests.get(base_url + "/api/products/11", timeout=5).json()["data"]["stock"]
    assert stock == 0


def test_create_order_product_not_found(base_url, alice_token):
    """TC-026 异常：商品不存在返回 404 + 40401。"""
    resp = _create_order(base_url, alice_token, productId=9999, quantity=1)

    assert resp.status_code == 404
    assert resp.json()["code"] == 40401


def test_create_order_requires_login(base_url):
    """TC-027 异常：未登录下单返回 401。"""
    resp = requests.post(base_url + "/api/orders", json={"productId": 1, "quantity": 1}, timeout=5)

    assert resp.status_code == 401
    assert resp.json()["code"] == 40101


@pytest.mark.xfail(strict=True, reason="BUG-07 金额先除 100 再乘 100，返回 1989.9999999999998")
def test_create_order_amount_is_integer_fen(base_url, alice_token):
    """TC-028 边界：单价 1990 分买 1 件，总价必须仍是 1990 分。

    文档规定金额单位为分、为整数。实际 `unit_price / 100 * quantity * 100`
    引入了二进制浮点误差，返回 `1989.9999999999998`。
    """
    resp = _create_order(base_url, alice_token, productId=1, quantity=1)

    assert resp.status_code == 201
    total = resp.json()["data"]["totalAmount"]
    assert total == 1990
    assert isinstance(total, int)


@pytest.mark.xfail(strict=True, reason="BUG-06 接受客户端传入的 totalAmount / unitPrice，可 1 分买单")
def test_create_order_ignores_client_amount(base_url, alice_token):
    """TC-029 异常：客户端伪造金额字段不应生效。

    文档 5.9 只要求 productId 与 quantity。实际把 `unitPrice` 与
    `totalAmount` 直接写库，等于允许买家自己定价。
    """
    resp = _create_order(
        base_url, alice_token, productId=1, quantity=1, unitPrice=1, totalAmount=1
    )

    assert resp.status_code == 201
    assert resp.json()["data"]["totalAmount"] == 1990


@pytest.mark.parametrize("quantity", [0, -1])
@pytest.mark.xfail(strict=True, reason="BUG-08 quantity 无正数校验，0 元成交或反向加库存")
def test_create_order_invalid_quantity(base_url, alice_token, quantity):
    """TC-030 异常：数量必须 ≥1，0 或负数应 400。

    实际 0 件能下单成功（总价 0）；-1 件因为 `stock < -1` 不成立，
    会走到 `stock = stock - (-1)`，反而把库存加回去。
    """
    resp = _create_order(base_url, alice_token, productId=1, quantity=quantity)

    assert resp.status_code == 400
    assert resp.json()["code"] == 40001


@pytest.mark.xfail(strict=True, reason="BUG-10 已下架商品仍可下单")
def test_create_order_off_shelf_product(base_url, alice_token):
    """TC-031 异常：已下架商品（id=7）不允许下单。"""
    resp = _create_order(base_url, alice_token, productId=7, quantity=1)

    assert resp.status_code in (400, 404, 409)


@pytest.mark.xfail(strict=True, reason="BUG-09 库存校验与扣减无锁，并发下单超卖")
def test_create_order_concurrent_oversell(base_url, alice_token):
    """TC-032 边界：库存 1 件，3 个并发请求最多只能成功 1 个。

    `handle_create_order` 先 SELECT 校验库存，再 `time.sleep(0.15)`，
    最后才 UPDATE 扣减，中间没有任何事务或行锁，多个请求会同时通过校验。
    """
    results = []

    def buy():
        resp = _create_order(base_url, alice_token, productId=5, quantity=1)
        results.append(resp.status_code)

    threads = [threading.Thread(target=buy) for _ in range(3)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert results.count(201) <= 1
    stock = requests.get(base_url + "/api/products/5", timeout=5).json()["data"]["stock"]
    assert stock >= 0


def test_my_orders_only_self(base_url, alice_token):
    """TC-033 正常：我的订单只包含当前用户的订单。"""
    resp = requests.get(
        base_url + "/api/orders/mine",
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["total"] >= 1
    assert all(item["userId"] == 2 for item in data["items"])
    assert all("userName" in item for item in data["items"])


def test_order_detail_ok(base_url, alice_token):
    """TC-034 正常：查看自己的订单详情。"""
    orders = requests.get(
        base_url + "/api/orders/mine",
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    ).json()["data"]["items"]
    order_id = orders[0]["id"]

    resp = requests.get(
        base_url + "/api/orders/%d" % order_id,
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 200
    assert resp.json()["data"]["id"] == order_id


def test_order_detail_not_found(base_url, alice_token):
    """TC-035 异常：订单不存在返回 404 + 40401。"""
    resp = requests.get(
        base_url + "/api/orders/999999",
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 404
    assert resp.json()["code"] == 40401


def test_order_detail_requires_login(base_url):
    """TC-036 异常：未登录查看订单返回 401。"""
    resp = requests.get(base_url + "/api/orders/1", timeout=5)

    assert resp.status_code == 401
    assert resp.json()["code"] == 40101
