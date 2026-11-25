# -*- coding: utf-8 -*-
"""支付与评价模块自动化用例。"""
import pytest
import requests


def _new_product(base_url, headers, price, stock, title):
    r = requests.post(base_url + "/api/products",
                      json={"title": title, "price": price, "stock": stock,
                            "category": "文具", "description": "自动化夹具"},
                      headers=headers, timeout=10)
    assert r.status_code == 201, r.text
    return r.json()["data"]["id"]


def _new_order(base_url, headers, product_id, quantity=1):
    r = requests.post(base_url + "/api/orders",
                      json={"productId": product_id, "quantity": quantity},
                      headers=headers, timeout=10)
    assert r.status_code == 201, r.text
    return r.json()["data"]["id"]


# ================================================================ 支付 · 正常
def test_payment_success(base_url, alice_headers):
    """TC-042 正常支付自己的订单：返回 paid，订单状态同步更新。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "支付正常用例商品")
    oid = _new_order(base_url, alice_headers, pid)

    r = requests.post(base_url + "/api/payments", json={"orderId": oid, "method": "alipay"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["status"] == "paid"
    assert data["orderId"] == oid
    assert data["paymentId"].startswith("PAY")

    order = requests.get(base_url + "/api/orders/%s" % oid, headers=alice_headers,
                         timeout=10).json()["data"]
    assert order["status"] == "paid"
    assert order["paidAt"] is not None


def test_payment_wechat_success(base_url, alice_headers):
    """TC 补充：wechat 支付方式同样可用。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "微信支付用例商品")
    oid = _new_order(base_url, alice_headers, pid)
    r = requests.post(base_url + "/api/payments", json={"orderId": oid, "method": "wechat"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 200
    assert r.json()["data"]["status"] == "paid"


# ================================================================ 支付 · 异常
def test_payment_unsupported_method(base_url, alice_headers):
    """TC-046 不支持的支付方式应 400。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "支付方式用例商品")
    oid = _new_order(base_url, alice_headers, pid)
    r = requests.post(base_url + "/api/payments", json={"orderId": oid, "method": "paypal"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400
    assert r.json()["code"] == 40001


def test_payment_missing_params(base_url, alice_headers):
    """TC 补充：缺少 method 或 orderId 应 400。"""
    r = requests.post(base_url + "/api/payments", json={"orderId": 1},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400
    assert r.json()["code"] == 40001

    r2 = requests.post(base_url + "/api/payments", json={"method": "alipay"},
                       headers=alice_headers, timeout=10)
    assert r2.status_code == 400
    assert r2.json()["code"] == 40001


def test_payment_requires_login(base_url):
    """TC 补充：未登录支付应 401。"""
    r = requests.post(base_url + "/api/payments", json={"orderId": 1, "method": "alipay"},
                      timeout=10)
    assert r.status_code == 401
    assert r.json()["code"] == 40101


# ================================================================ 支付 · 缺陷关联
@pytest.mark.xfail(strict=True, reason="BUG-13：未校验订单状态，同一订单可重复支付并生成多条流水")
def test_payment_duplicate_rejected(base_url, alice_headers):
    """TC-043 同一订单只能支付一次，重复支付应 409。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "重复支付用例商品")
    oid = _new_order(base_url, alice_headers, pid)

    first = requests.post(base_url + "/api/payments",
                          json={"orderId": oid, "method": "alipay"},
                          headers=alice_headers, timeout=10)
    assert first.status_code == 200

    second = requests.post(base_url + "/api/payments",
                           json={"orderId": oid, "method": "alipay"},
                           headers=alice_headers, timeout=10)
    assert second.status_code == 409


@pytest.mark.xfail(strict=True, reason="BUG-14：未校验订单归属，可为他人订单付款")
def test_payment_other_users_order_forbidden(base_url, alice_headers, bob_headers):
    """TC-044 不能为他人订单付款。"""
    pid = _new_product(base_url, bob_headers, 1000, 3, "跨用户支付用例商品")
    oid = _new_order(base_url, bob_headers, pid)

    r = requests.post(base_url + "/api/payments", json={"orderId": oid, "method": "wechat"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 403


@pytest.mark.xfail(strict=True, reason="BUG-14：订单不存在时 amount 兜底为 0，仍插入已支付流水")
def test_payment_nonexistent_order(base_url, alice_headers):
    """TC-045 不存在的订单付款应 404，不能凭空生成支付记录。"""
    r = requests.post(base_url + "/api/payments",
                      json={"orderId": 999999, "method": "wechat"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 404


@pytest.mark.xfail(strict=True, reason="BUG-15：回调公开且无验签，伪造 result=success 即置为已支付")
def test_payment_callback_forgery_rejected(base_url, alice_headers):
    """TC-047 支付回调必须验签，伪造请求应被拒绝。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "回调伪造用例商品")
    oid = _new_order(base_url, alice_headers, pid)

    forged = requests.post(base_url + "/api/payments/callback",
                           json={"orderId": oid, "paymentId": "PAY_FAKE_999",
                                 "result": "success"}, timeout=10)
    assert forged.status_code in (401, 403), "伪造回调返回 %s" % forged.status_code

    order = requests.get(base_url + "/api/orders/%s" % oid, headers=alice_headers,
                         timeout=10).json()["data"]
    assert order["status"] == "created", "伪造回调使订单变为 %s" % order["status"]


# ================================================================ 评价 · 正常
def test_create_review_success(base_url, alice_headers):
    """TC-048 发布评价成功。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "评价用例商品")
    r = requests.post(base_url + "/api/product-reviews",
                      json={"productId": pid, "content": "自动化测试评价，很好用",
                            "rating": 5}, headers=alice_headers, timeout=10)
    assert r.status_code == 201
    data = r.json()["data"]
    assert data["rating"] == 5
    assert data["userId"] == 2
    assert data["productId"] == pid


def test_review_requires_login(base_url):
    """TC 补充：未登录发布评价应 401。"""
    r = requests.post(base_url + "/api/product-reviews",
                      json={"productId": 1, "content": "匿名评价", "rating": 5}, timeout=10)
    assert r.status_code == 401
    assert r.json()["code"] == 40101


# ================================================================ 评价 · 边界
def test_review_rating_boundary(base_url, alice_headers):
    """TC-049 评分边界 1 与 5 均合法。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "评分边界用例商品")
    for rating in (1, 5):
        r = requests.post(base_url + "/api/product-reviews",
                          json={"productId": pid, "content": "边界评分", "rating": rating},
                          headers=alice_headers, timeout=10)
        assert r.status_code == 201, "rating=%s 应被接受" % rating


def test_review_rating_out_of_range(base_url, alice_headers):
    """TC-050 评分 0 与 6 越界应 400。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "评分越界用例商品")
    for rating in (0, 6):
        r = requests.post(base_url + "/api/product-reviews",
                          json={"productId": pid, "content": "越界评分", "rating": rating},
                          headers=alice_headers, timeout=10)
        assert r.status_code == 400, "rating=%s 应被拒绝" % rating
        assert r.json()["code"] == 40001


def test_review_content_length_boundary(base_url, alice_headers):
    """TC-052/053 内容 200 字合法、201 字拒绝。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "内容长度用例商品")

    ok = requests.post(base_url + "/api/product-reviews",
                       json={"productId": pid, "content": "字" * 200, "rating": 5},
                       headers=alice_headers, timeout=10)
    assert ok.status_code == 201, "200 字应为合法上边界"

    bad = requests.post(base_url + "/api/product-reviews",
                        json={"productId": pid, "content": "字" * 201, "rating": 5},
                        headers=alice_headers, timeout=10)
    assert bad.status_code == 400
    assert bad.json()["code"] == 40001


def test_review_empty_content_rejected(base_url, alice_headers):
    """TC-054 空内容（含纯空白）应拒绝。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "空内容用例商品")
    r = requests.post(base_url + "/api/product-reviews",
                      json={"productId": pid, "content": "   ", "rating": 5},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400
    assert r.json()["code"] == 40001


def test_review_product_not_found(base_url, alice_headers):
    """TC 补充：评价不存在的商品应 404。"""
    r = requests.post(base_url + "/api/product-reviews",
                      json={"productId": 999999, "content": "幽灵商品", "rating": 5},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 404
    assert r.json()["code"] == 40401


def test_review_page_renders(base_url):
    """TC 补充：评价页返回 HTML，包含商品标题与评价条数。"""
    r = requests.get(base_url + "/api/product-reviews", params={"productId": 1}, timeout=10)
    assert r.status_code == 200
    assert "text/html" in r.headers.get("Content-Type", "")
    assert "商品评价" in r.text


# ================================================================ 评价 · 缺陷关联
@pytest.mark.xfail(strict=True, reason="BUG-07：rating 非数字触发 int() 异常，返回 500")
def test_review_rating_not_numeric(base_url, alice_headers):
    """TC-051 rating 非数字应 400，不应 500。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "评分类型用例商品")
    r = requests.post(base_url + "/api/product-reviews",
                      json={"productId": pid, "content": "评分类型", "rating": "五星"},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 400


@pytest.mark.xfail(strict=True, reason="BUG-16：render_reviews_page 直接拼接 content，未做 HTML 转义")
def test_review_content_html_escaped(base_url, alice_headers):
    """TC-055 用户内容必须 HTML 转义，页面不得出现可执行的 script 标签。"""
    pid = _new_product(base_url, alice_headers, 1000, 3, "XSS用例商品")
    payload = "<script>alert('xss')</script>"
    r = requests.post(base_url + "/api/product-reviews",
                      json={"productId": pid, "content": payload, "rating": 5},
                      headers=alice_headers, timeout=10)
    assert r.status_code == 201

    page = requests.get(base_url + "/api/product-reviews",
                        params={"productId": pid}, timeout=10).text
    assert payload not in page, "评价内容未转义，原样输出到 HTML"
    assert "&lt;script&gt;" in page, "期望出现转义后的 &lt;script&gt;"
