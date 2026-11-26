# -*- coding: utf-8 -*-
"""健康检查接口。"""

import requests


def test_health_ok(base_url):
    """TC-001 正常：服务存活，返回 status=up 与 uptime。"""
    resp = requests.get(base_url + "/health", timeout=5)

    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0
    assert body["data"]["status"] == "up"
    assert isinstance(body["data"]["uptime"], (int, float))
    assert body["data"]["uptime"] >= 0
