# -*- coding: utf-8 -*-
"""越权访问：横向（同角色跨用户）与纵向（普通用户访问管理接口）。

这组用例断言的是文档「权限矩阵」一节承诺的行为，当前 SUT 除登录校验外
几乎不校验归属与角色，因此多数标了 xfail，对应缺陷报告中的 BUG-01/02/03/05。
"""

import pytest
import requests


def test_user_detail_self_ok(base_url, alice_token):
    """TC-058 正常：查看自己的资料允许通过。"""
    resp = requests.get(
        base_url + "/api/users/2",
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["id"] == 2
    assert data["username"] == "alice"


@pytest.mark.xfail(strict=True, reason="BUG-01 查看他人资料只校验登录、不校验属主")
def test_user_detail_other_forbidden(base_url, alice_token):
    """TC-059 异常：alice 查 bob（id=3）的资料应 403。

    资料含真实姓名与手机号，属于个人信息，越权读取即信息泄露。
    """
    resp = requests.get(
        base_url + "/api/users/3",
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 403


@pytest.mark.xfail(strict=True, reason="BUG-01 任意登录用户都能读取任意 userId 的手机号")
def test_user_detail_enumeration(base_url, alice_token):
    """TC-060 异常：遍历 userId 不应拿到全部用户信息。"""
    leaked = []
    for user_id in range(1, 6):
        resp = requests.get(
            base_url + "/api/users/%d" % user_id,
            headers={"Authorization": "Bearer " + alice_token},
            timeout=5,
        )
        if resp.status_code == 200 and resp.json()["data"].get("phone"):
            leaked.append(resp.json()["data"]["username"])

    assert leaked == []


@pytest.mark.xfail(strict=True, reason="BUG-03 订单详情不校验归属，可读他人订单的收货地址")
def test_order_detail_other_forbidden(base_url, alice_token):
    """TC-061 异常：alice 读 bob 的订单（id=2）应 403。"""
    resp = requests.get(
        base_url + "/api/orders/2",
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 403


@pytest.mark.xfail(strict=True, reason="BUG-02 /api/admin/orders 只校验登录，不校验 admin 角色")
def test_admin_orders_forbidden_for_user(base_url, alice_token):
    """TC-062 异常：普通用户访问管理端订单列表应 403。"""
    resp = requests.get(
        base_url + "/api/admin/orders",
        headers={"Authorization": "Bearer " + alice_token},
        timeout=5,
    )

    assert resp.status_code == 403


def test_admin_orders_ok_for_admin(base_url, admin_token):
    """TC-063 正常：管理员访问订单列表返回全部订单。"""
    resp = requests.get(
        base_url + "/api/admin/orders",
        headers={"Authorization": "Bearer " + admin_token},
        timeout=5,
    )

    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["total"] >= 2
    assert {"orderNo", "userName", "status"} <= set(data["items"][0])


def test_admin_orders_requires_login(base_url):
    """TC-064 异常：未登录访问管理端订单返回 401（不是 500）。"""
    resp = requests.get(base_url + "/api/admin/orders", timeout=5)

    assert resp.status_code == 401
    assert resp.json()["code"] == 40101


@pytest.mark.xfail(strict=True, reason="BUG-05 上下架不校验商品归属")
def test_product_status_other_forbidden(base_url, bob_token):
    """TC-065 异常：bob 不能下架 alice 发布的商品（id=1 seller_id=1 属 admin）。

    这里用 bob 去改 id=4（seller_id=1，属 admin），两个 id 都不是 bob。
    """
    resp = requests.patch(
        base_url + "/api/products/4/status",
        json={"status": "off"},
        headers={"Authorization": "Bearer " + bob_token},
        timeout=5,
    )

    assert resp.status_code == 403


@pytest.mark.xfail(strict=True, reason="BUG-04 token 可伪造，任意人可用 tk_1 冒充管理员")
def test_forged_admin_token(base_url, alice_token):
    """TC-066 异常：普通用户不能通过改 token 里的数字获得管理员权限。"""
    resp = requests.get(
        base_url + "/api/admin/orders",
        headers={"Authorization": "Bearer tk_1"},
        timeout=5,
    )

    assert resp.status_code == 401
