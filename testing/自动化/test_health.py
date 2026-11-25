# -*- coding: utf-8 -*-
"""运维接口与全局健壮性用例。"""
import requests


def test_health(base_url):
    """TC-056 健康检查：status=up，uptime≥0。"""
    r = requests.get(base_url + "/health", timeout=10)
    assert r.status_code == 200
    body = r.json()
    assert body["code"] == 0
    data = body["data"]
    assert data["status"] == "up"
    assert data["uptime"] >= 0


def test_unknown_route_returns_404(base_url):
    """未定义路由应返回 404 + 40401，不应 500。"""
    r = requests.get(base_url + "/api/not-exist", timeout=10)
    assert r.status_code == 404
    assert r.json()["code"] == 40401


def test_malformed_json_body(base_url, alice_headers):
    """请求体不是合法 JSON 时应 400/401 之一，不能 500。"""
    r = requests.post(base_url + "/api/orders", data="{not-json",
                      headers={**alice_headers, "Content-Type": "application/json"},
                      timeout=10)
    assert r.status_code in (400, 401), "非法 JSON 返回 %s" % r.status_code
    assert r.status_code != 500


def test_json_array_body(base_url, alice_headers):
    """顶层是数组而非对象时应被拒绝，不能 500。"""
    r = requests.post(base_url + "/api/orders", json=[1, 2, 3],
                      headers=alice_headers, timeout=10)
    assert r.status_code in (400, 401), "数组请求体返回 %s" % r.status_code
