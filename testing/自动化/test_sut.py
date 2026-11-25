# -*- coding: utf-8 -*-
"""SUT 接口自动化测试（8 条）。

运行方式（在 testing/自动化 目录下）：
    pytest -q
换端口运行：
    SUT_BASE=http://127.0.0.1:8090 pytest -q

注意：用例会真实下单，会消耗商品库存。完整回归前建议重置数据库
（停服务 → 删 sut.db → 重启），否则商品 1 的库存跑若干轮后会被扣光，
导致 test_create_order_normal 提前因 409 库存不足而失败。

断言依据是接口文档中的承诺，不是服务的实际行为。
其中 4 条会失败，原因是 SUT 存在已确认的缺陷，测试本身没有写错：
    - test_create_order_normal        对应缺陷 BUG-11（总额 1989.9999999999998）
    - test_order_off_shelf_product    对应缺陷 X-03
    - test_product_pagesize_over_limit 对应缺陷 X-01
    - test_order_quantity_zero        对应缺陷 BUG-06
实测：4 failed, 4 passed。
"""

import requests


def test_login_success(base_url):
    """正常：正确账号密码登录，返回 token 与 userId。"""
    resp = requests.post(
        base_url + "/api/auth/login",
        json={"username": "alice", "password": "Alice@123"},
        timeout=10,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0
    assert body["data"]["token"] == "tk_2"
    assert body["data"]["userId"] == 2


def test_product_list_default(base_url):
    """正常：默认商品列表只返回在售商品，共 11 条。

    依据 testing/README.md「种子数据」一节：初始 12 个商品，id=7 已下架，
    不传 status 时默认只返回 11 个在售商品。
    （注意 README 5.5 节的示例 JSON 里写的是 total=12，那是格式示意，
    与「种子数据」一节的明文承诺冲突，以「种子数据」为准。）
    """
    resp = requests.get(base_url + "/api/products", timeout=10)
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0
    assert body["data"]["total"] == 11
    # 返回的每一条都应当是在售状态
    assert all(item["status"] == "on" for item in body["data"]["items"])


def test_create_order_normal(base_url, auth):
    """正常：下单成功，金额由服务端按商品单价计算。

    对应缺陷 BUG-11：unitPrice 为 1990 时，totalAmount 返回 1989.9999999999998，
    不是整数 1990。原因是 app.py 先把「分」换算成「元」再换算回「分」，
    浮点往返把误差固定了下来。
    """
    resp = requests.post(
        base_url + "/api/orders",
        json={"productId": 1, "quantity": 1},
        headers=auth,
        timeout=10,
    )
    assert resp.status_code == 201
    body = resp.json()["data"]
    assert body["unitPrice"] == 1990
    assert body["totalAmount"] == 1990, "总额应为整数分 1990，实际返回 %r" % (
        body["totalAmount"],
    )
    assert body["status"] == "created"


def test_user_detail_requires_token(base_url):
    """异常：不带 token 访问用户资料，应返回 401。"""
    resp = requests.get(base_url + "/api/users/2", timeout=10)
    assert resp.status_code == 401
    assert resp.json()["code"] == 40101


def test_order_insufficient_stock(base_url, auth):
    """异常：库存不足时下单应返回 409。商品 5 库存为 1，买 2 件。"""
    resp = requests.post(
        base_url + "/api/orders",
        json={"productId": 5, "quantity": 2},
        headers=auth,
        timeout=10,
    )
    assert resp.status_code == 409
    assert resp.json()["code"] == 40901


def test_order_off_shelf_product(base_url, auth):
    """异常：对已下架商品下单应返回 409。商品 7 在种子数据中为下架状态。

    对应缺陷 X-03：服务端未校验商品 status，实际会返回 201。
    """
    resp = requests.post(
        base_url + "/api/orders",
        json={"productId": 7, "quantity": 1},
        headers=auth,
        timeout=10,
    )
    assert resp.status_code == 409, "商品已下架时应拒绝下单，实际返回 %s" % resp.status_code
    assert resp.json()["code"] == 40902


def test_product_pagesize_over_limit(base_url):
    """边界：pageSize 超出 1~50 范围应返回 400。

    对应缺陷 X-01：实际返回 200 并原样回显 pageSize。
    """
    resp = requests.get(base_url + "/api/products", params={"pageSize": 999}, timeout=10)
    assert resp.status_code == 400, "pageSize 超上限应报参数错误，实际返回 %s" % resp.status_code
    assert resp.json()["code"] == 40001


def test_order_quantity_zero(base_url, auth):
    """边界：下单数量为 0 应返回 400，quantity 必须是正整数。

    对应缺陷 BUG-06：实际返回 201 并生成订单。
    """
    resp = requests.post(
        base_url + "/api/orders",
        json={"productId": 1, "quantity": 0},
        headers=auth,
        timeout=10,
    )
    assert resp.status_code == 400, "quantity=0 应报参数错误，实际返回 %s" % resp.status_code
    assert resp.json()["code"] == 40001
