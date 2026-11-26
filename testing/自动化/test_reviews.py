# -*- coding: utf-8 -*-
"""商品评价：服务端渲染页面与提交评价。"""

import pytest
import requests


def _post_review(base_url, token, **payload):
    return requests.post(
        base_url + "/api/product-reviews",
        json=payload,
        headers={"Authorization": "Bearer " + token},
        timeout=5,
    )


def test_reviews_page_ok(base_url):
    """TC-046 正常：商品 1 的种子评价共 2 条，返回 HTML 且含星级。"""
    resp = requests.get(base_url + "/api/product-reviews", params={"productId": 1}, timeout=5)

    assert resp.status_code == 200
    assert "text/html" in resp.headers["Content-Type"]
    html = resp.text
    assert "无线蓝牙耳机" in html
    assert "共 2 条评价" in html
    assert "★★★★★" in html
    assert "alice" in html


def test_reviews_page_empty(base_url):
    """TC-047 边界：没有评价的商品显示空态文案而不是 500。"""
    resp = requests.get(base_url + "/api/product-reviews", params={"productId": 9}, timeout=5)

    assert resp.status_code == 200
    assert "暂无评价" in resp.text


def test_reviews_page_unknown_product(base_url):
    """TC-048 异常：productId 不存在时页面提示「未知商品」而不是报错。"""
    resp = requests.get(base_url + "/api/product-reviews", params={"productId": 9999}, timeout=5)

    assert resp.status_code == 200
    assert "未知商品" in resp.text


def test_reviews_page_invalid_product_id(base_url):
    """TC-049 异常：productId 非数字时不应 500。"""
    resp = requests.get(base_url + "/api/product-reviews", params={"productId": "abc"}, timeout=5)

    assert resp.status_code == 200
    assert "未知商品" in resp.text


def test_create_review_ok(base_url, alice_token):
    """TC-050 正常：提交 5 星好评返回 201，记录 userId。"""
    resp = _post_review(base_url, alice_token, productId=9, content="笔很好写，速干不糊。", rating=5)

    assert resp.status_code == 201
    data = resp.json()["data"]
    assert data["productId"] == 9
    assert data["userId"] == 2
    assert data["rating"] == 5
    assert data["content"] == "笔很好写，速干不糊。"

    page = requests.get(base_url + "/api/product-reviews", params={"productId": 9}, timeout=5)
    assert "笔很好写，速干不糊。" in page.text


def test_create_review_requires_login(base_url):
    """TC-051 异常：未登录不能评价。"""
    resp = requests.post(
        base_url + "/api/product-reviews",
        json={"productId": 1, "content": "x", "rating": 5},
        timeout=5,
    )

    assert resp.status_code == 401
    assert resp.json()["code"] == 40101


@pytest.mark.parametrize(
    "content, case",
    [
        ("", "空字符串"),
        ("   ", "全空格"),
        ("字" * 201, "201 字"),
    ],
)
def test_create_review_invalid_content(base_url, alice_token, content, case):
    """TC-052 异常/边界：评价内容长度必须为 1~200 字符。"""
    resp = _post_review(base_url, alice_token, productId=1, content=content, rating=5)

    assert resp.status_code == 400, case
    assert resp.json()["code"] == 40001, case


def test_create_review_content_max_boundary(base_url, alice_token):
    """TC-053 边界：恰好 200 字的评价应当被接受。"""
    resp = _post_review(base_url, alice_token, productId=1, content="字" * 200, rating=5)

    assert resp.status_code == 201


@pytest.mark.parametrize("rating", [0, 6, -1])
def test_create_review_invalid_rating(base_url, alice_token, rating):
    """TC-054 异常：评分必须在 1~5 之间。"""
    resp = _post_review(base_url, alice_token, productId=1, content="评分越界", rating=rating)

    assert resp.status_code == 400
    assert resp.json()["code"] == 40001


def test_create_review_product_not_found(base_url, alice_token):
    """TC-055 异常：给不存在的商品评价返回 404。"""
    resp = _post_review(base_url, alice_token, productId=9999, content="x", rating=5)

    assert resp.status_code == 404
    assert resp.json()["code"] == 40401


def test_create_review_missing_param(base_url, alice_token):
    """TC-056 异常：三个必填字段缺任意一个都是 400。"""
    resp = requests.post(
        base_url + "/api/product-reviews",
        json={"productId": 1, "rating": 5},
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 400
    assert resp.json()["code"] == 40001


@pytest.mark.xfail(strict=True, reason="BUG-14 评价内容未转义，存在存储型 XSS")
def test_review_content_is_escaped(base_url, alice_token):
    """TC-057 安全：评价内容里的 HTML 应被转义后再输出。

    `render_reviews_page` 用 `'<div class="content">%s</div>' % row["content"]`
    直接拼进页面，提交 `<script>` 会被浏览器执行。
    """
    payload = "<script>alert('xss')</script>"
    created = _post_review(base_url, alice_token, productId=9, content=payload, rating=5)
    assert created.status_code == 201

    page = requests.get(base_url + "/api/product-reviews", params={"productId": 9}, timeout=5)

    assert "<script>" not in page.text
    assert "&lt;script&gt;" in page.text
