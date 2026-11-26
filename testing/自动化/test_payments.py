# -*- coding: utf-8 -*-
"""支付接口：发起支付与支付回调。"""

import pytest
import requests


def _pay(base_url, token, **payload):
    return requests.post(
        base_url + "/api/payments",
        json=payload,
        headers={"Authorization": "Bearer " + token},
        timeout=5,
    )


def test_create_payment_ok(base_url, alice_token):
    """TC-037 正常：为待支付订单发起支付宝支付，金额取自订单。"""
    order = requests.post(
        base_url + "/api/orders",
        json={"productId": 8, "quantity": 1},
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    ).json()["data"]

    resp = _pay(base_url, alice_token, orderId=order["id"], method="alipay")

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["orderId"] == order["id"]
    assert data["amount"] == order["totalAmount"]
    assert data["status"] == "paid"
    assert data["paymentId"].startswith("PAY")
    assert data["paidAt"]


def test_create_payment_marks_order_paid(base_url, alice_token):
    """TC-038 正常：支付成功后订单状态变为 paid 并写入 paidAt。"""
    order = requests.post(
        base_url + "/api/orders",
        json={"productId": 6, "quantity": 1},
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    ).json()["data"]

    _pay(base_url, alice_token, orderId=order["id"], method="wechat")

    detail = requests.get(
        base_url + "/api/orders/%d" % order["id"],
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    ).json()["data"]
    assert detail["status"] == "paid"
    assert detail["paidAt"]


@pytest.mark.parametrize("method", ["paypal", "ALIPAY", ""])
def test_create_payment_invalid_method(base_url, alice_token, method):
    """TC-039 异常：支付方式必须在 alipay / wechat 白名单内。"""
    resp = _pay(base_url, alice_token, orderId=1, method=method)

    assert resp.status_code == 400
    assert resp.json()["code"] == 40001


@pytest.mark.parametrize("payload", [{"method": "alipay"}, {"orderId": 1}])
def test_create_payment_missing_param(base_url, alice_token, payload):
    """TC-040 异常：orderId 与 method 缺一不可，均为 400。"""
    resp = requests.post(
        base_url + "/api/payments",
        json=payload,
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 400
    assert resp.json()["code"] == 40001


def test_create_payment_requires_login(base_url):
    """TC-041 异常：未登录发起支付返回 401。"""
    resp = requests.post(base_url + "/api/payments", json={"orderId": 1, "method": "alipay"}, timeout=5)

    assert resp.status_code == 401
    assert resp.json()["code"] == 40101


@pytest.mark.xfail(strict=True, reason="BUG-11 同一订单可重复支付，无幂等校验")
def test_create_payment_duplicate(base_url, alice_token):
    """TC-042 异常：已支付订单再次发起支付应被拒绝。

    文档语义是一单一次支付。实际每次都 INSERT 一条新支付记录并覆盖订单，
    用户会被重复扣款。
    """
    order = requests.post(
        base_url + "/api/orders",
        json={"productId": 4, "quantity": 1},
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    ).json()["data"]

    first = _pay(base_url, alice_token, orderId=order["id"], method="alipay")
    assert first.status_code == 200

    second = _pay(base_url, alice_token, orderId=order["id"], method="alipay")

    assert second.status_code in (400, 409)


@pytest.mark.xfail(strict=True, reason="BUG-11 订单不存在也能建支付单，金额记为 0")
def test_create_payment_order_not_found(base_url, alice_token):
    """TC-043 异常：为不存在的订单发起支付应返回 404。"""
    resp = _pay(base_url, alice_token, orderId=999999, method="alipay")

    assert resp.status_code == 404
    assert resp.json()["code"] == 40401


@pytest.mark.xfail(strict=True, reason="BUG-12 回调无签名，可篡改任意订单状态")
def test_payment_callback_cannot_flip_arbitrary_order(base_url, bob_token):
    """TC-044 异常：回调不应能把订单状态改掉，更不该改他人订单。

    种子订单 1（SO202609180001）属于 alice(userId=2)、初始状态 created。
    匿名 POST 一个 `{"orderId":1,"result":"success"}` 就把它变成了 paid。
    """
    before = requests.get(
        base_url + "/api/orders/1",
        headers={"Authorization": "Bearer " + bob_token},
        timeout=5,
    ).json()["data"]
    assert before["status"] == "created"

    requests.post(
        base_url + "/api/payments/callback",
        json={"orderId": 1, "result": "success"},
        timeout=5,
    )

    after = requests.get(
        base_url + "/api/orders/1",
        headers={"Authorization": "Bearer " + bob_token},
        timeout=5,
    ).json()["data"]

    assert after["status"] == before["status"]


@pytest.mark.xfail(strict=True, reason="BUG-12 支付回调零验签，匿名请求即可把订单改成已支付")
def test_payment_callback_requires_signature(base_url):
    """TC-045 异常：回调必须验签，未带签名的伪造请求应被拒绝。

    实际 `handle_payment_callback` 不读任何签名参数，任何人 POST
    `{"orderId":1,"result":"success"}` 就能把订单置为已支付。
    """
    resp = requests.post(
        base_url + "/api/payments/callback",
        json={"orderId": 1, "paymentId": "PAY-FAKE", "result": "success"},
        timeout=5,
    )

    assert resp.status_code in (400, 401, 403)
