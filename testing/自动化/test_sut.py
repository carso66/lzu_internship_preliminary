# -*- coding: utf-8 -*-
"""SUT 接口自动化测试（23 条：正常 5 / 异常 11 / 边界 7）。

运行方式（在 testing/自动化 目录下）：
    pytest -q
换端口运行：
    SUT_BASE=http://127.0.0.1:8090 pytest -q
只看对照缺陷的用例：
    pytest -q -m defect
再按关键字收窄：
    pytest -q -m defect -k quantity

断言依据是接口文档（testing/README.md）里的承诺，不是服务的实际行为。
因此有 14 条会失败——每一条都对应缺陷报告里的一个已登记缺陷，测试本身没有写错。
失败的用例都用 @pytest.mark.defect 标了编号，可以单独筛出来复核：

    test_create_order_normal          BUG-11  总额 1989.9999999999998，应为整数 1990
    test_client_price_ignored         BUG-04  客户端传 unitPrice 被采信
    test_user_detail_other_forbidden  BUG-01  读他人资料返回 200，应 403
    test_order_detail_other_forbidden BUG-01  读他人订单返回 200，应 403
    test_admin_orders_forbidden       BUG-02  普通用户读管理端返回 200，应 403
    test_logout_invalidates_token     BUG-03  登出后令牌仍可用
    test_duplicate_payment_rejected   BUG-08  第二次支付返回 200，应 409
    test_callback_requires_signature  BUG-07  无签名回调被接受
    test_order_off_shelf_product      X-03    下架商品仍可下单
    test_order_quantity_zero          BUG-06  quantity=0 返回 201，应 400
    test_order_quantity_negative      BUG-06  quantity=-5 返回 201，应 400
    test_order_quantity_float         BUG-06  quantity=1.5 被截断成 1，应 400
    test_product_pagesize_over_limit  X-01    pageSize=999 返回 200，应 400
    test_product_keyword_injection    BUG-10  keyword 注入让下架商品重新出现

注意：用例会真实下单、消耗库存。反复跑若干轮后商品 1 的库存会被扣光，
完整回归前重启一次服务即可回到初始数据（app.py 启动时会删库重建）。
"""

import requests
import pytest


# ---------------- 正常 ----------------

def test_health_ok(base_url):
    """正常：健康检查返回 up。"""
    resp = requests.get(base_url + "/health", timeout=10)
    assert resp.status_code == 200
    assert resp.json()["data"]["status"] == "up"


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

    依据 testing/README.md「种子数据」：初始 12 个商品，id=7 已下架，
    不传 status 时默认只返回 11 个在售商品。
    """
    resp = requests.get(base_url + "/api/products", timeout=10)
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0
    assert body["data"]["total"] == 11
    assert all(item["status"] == "on" for item in body["data"]["items"])


def test_product_detail_ok(base_url):
    """正常：商品详情返回完整字段，价格单位为整数分。"""
    resp = requests.get(base_url + "/api/products/1", timeout=10)
    assert resp.status_code == 200
    body = resp.json()["data"]
    assert body["id"] == 1
    assert body["price"] == 1990
    assert body["status"] == "on"


@pytest.mark.defect("BUG-11")
def test_create_order_normal(base_url, auth):
    """正常：下单成功，金额由服务端按商品单价计算，且为整数分。

    对应缺陷 BUG-11：unitPrice 为 1990 时，totalAmount 返回 1989.9999999999998。
    原因是 app.py 先把「分」换算成「元」再换算回「分」，浮点往返把误差固定了下来。
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


@pytest.mark.defect("BUG-04")
def test_client_price_ignored(base_url, auth):
    """异常：金额必须由服务端按商品单价计算，客户端传的 unitPrice 应被忽略。

    文档 5.9 节写明下单请求只有 productId 与 quantity 两个字段，
    单价只能来自数据库。对应缺陷 BUG-04：传 unitPrice=1 时，
    返回的 totalAmount 就是 1.0，等于 1 分钱买下 19.90 元的商品。
    """
    resp = requests.post(
        base_url + "/api/orders",
        json={"productId": 1, "quantity": 1, "unitPrice": 1},
        headers=auth,
        timeout=10,
    )
    assert resp.status_code in (201, 400)
    if resp.status_code == 201:
        amount = resp.json()["data"]["totalAmount"]
        assert amount == 1990, "客户端传的 unitPrice 不得被采信，实际总额为 %r" % (amount,)


# ---------------- 异常 ----------------

def test_login_wrong_password(base_url):
    """异常：密码错误返回 401 + 40101。"""
    resp = requests.post(
        base_url + "/api/auth/login",
        json={"username": "alice", "password": "wrong"},
        timeout=10,
    )
    assert resp.status_code == 401
    assert resp.json()["code"] == 40101


def test_user_detail_requires_token(base_url):
    """异常：不带 token 访问用户资料，应返回 401。"""
    resp = requests.get(base_url + "/api/users/2", timeout=10)
    assert resp.status_code == 401
    assert resp.json()["code"] == 40101


@pytest.mark.defect("BUG-01")
def test_user_detail_other_forbidden(base_url, auth):
    """异常：不能查看他人资料。文档承诺「只能看自己，看别人应 403」。

    对应缺陷 BUG-01：实际返回 200 与 bob 的姓名、手机号。
    """
    resp = requests.get(base_url + "/api/users/3", headers=auth, timeout=10)
    assert resp.status_code == 403, "读他人资料应 403，实际返回 %s" % resp.status_code
    assert "phone" not in resp.text, "响应体不应包含他人手机号"


@pytest.mark.defect("BUG-01")
def test_order_detail_other_forbidden(base_url, auth):
    """异常：不能查看他人订单。订单 2 属于 bob（userId=3）。

    对应缺陷 BUG-01：实际返回 200，含收货人手机号与地址。
    """
    resp = requests.get(base_url + "/api/orders/2", headers=auth, timeout=10)
    assert resp.status_code == 403, "读他人订单应 403，实际返回 %s" % resp.status_code


@pytest.mark.defect("BUG-02")
def test_admin_orders_forbidden(base_url, auth):
    """异常：普通用户不能访问管理端订单接口，应 403。

    对应缺陷 BUG-02：实际返回 200 与全站订单。
    """
    resp = requests.get(base_url + "/api/admin/orders", headers=auth, timeout=10)
    assert resp.status_code == 403, "普通用户访问管理端应 403，实际返回 %s" % resp.status_code


@pytest.mark.defect("BUG-03")
def test_logout_invalidates_token(base_url):
    """异常：登出后原 token 应失效。

    对应缺陷 BUG-03：登出接口只返回提示，服务端无任何状态变更，token 仍可用。

    这里刻意在用例内部重新登录取一个 token，不用会话级 fixture——
    否则在「实现正确」的情况下，登出会把后续所有用例一起带挂。
    """
    login = requests.post(
        base_url + "/api/auth/login",
        json={"username": "alice", "password": "Alice@123"},
        timeout=10,
    )
    fresh = login.json()["data"]["token"]
    headers = {"Authorization": "Bearer " + fresh}

    requests.post(base_url + "/api/auth/logout", headers=headers, timeout=10)
    resp = requests.get(base_url + "/api/users/2", headers=headers, timeout=10)
    assert resp.status_code == 401, "登出后令牌应失效（401），实际返回 %s" % resp.status_code


@pytest.mark.defect("X-03")
def test_order_off_shelf_product(base_url, auth):
    """异常：已下架商品不能下单，应 409。商品 7 的 status 为 off。

    对应缺陷 X-03：服务端未校验商品 status，实际返回 201。
    """
    resp = requests.post(
        base_url + "/api/orders",
        json={"productId": 7, "quantity": 1},
        headers=auth,
        timeout=10,
    )
    assert resp.status_code == 409, "下架商品应拒绝下单（409），实际返回 %s" % resp.status_code


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


@pytest.mark.defect("BUG-08")
def test_duplicate_payment_rejected(base_url, auth, new_order):
    """异常：同一订单只能支付一次，第二次应 409 + 40903。

    对应缺陷 BUG-08：两次都返回 200，各生成一条支付流水。
    用 new_order 夹具现场建单，避免动到种子订单、也不与其他用例互相污染。
    """
    first = requests.post(
        base_url + "/api/payments",
        json={"orderId": new_order, "method": "alipay"},
        headers=auth,
        timeout=10,
    )
    assert first.status_code == 200

    second = requests.post(
        base_url + "/api/payments",
        json={"orderId": new_order, "method": "alipay"},
        headers=auth,
        timeout=10,
    )
    assert second.status_code == 409, "重复支付应 409，实际返回 %s" % second.status_code
    assert second.json()["code"] == 40903


@pytest.mark.defect("BUG-07")
def test_callback_requires_signature(base_url, new_order):
    """异常：支付回调必须验签，无签名请求应 401/403。

    对应缺陷 BUG-07：无签名回调返回 200，并把订单改成 paid。
    回调是公开接口，所以这里刻意不带 Authorization 头。
    """
    resp = requests.post(
        base_url + "/api/payments/callback",
        json={"orderId": new_order, "paymentId": "FAKE0001", "result": "success"},
        timeout=10,
    )
    assert resp.status_code in (400, 401, 403), (
        "无签名回调应被拒绝（400/401/403），实际返回 %s" % resp.status_code
    )


# ---------------- 边界 ----------------

@pytest.mark.defect("X-01")
def test_product_pagesize_over_limit(base_url):
    """边界：pageSize 超出 1~50 范围应返回 400。

    对应缺陷 X-01：实际返回 200 并原样回显 pageSize=999。
    """
    resp = requests.get(base_url + "/api/products", params={"pageSize": 999}, timeout=10)
    assert resp.status_code == 400, "pageSize 超上限应报参数错误，实际返回 %s" % resp.status_code
    assert resp.json()["code"] == 40001


def test_product_not_found(base_url):
    """边界：商品不存在应 404 + 40401。"""
    resp = requests.get(base_url + "/api/products/99999", timeout=10)
    assert resp.status_code == 404
    assert resp.json()["code"] == 40401


@pytest.mark.defect("BUG-06")
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


@pytest.mark.defect("BUG-06")
def test_order_quantity_negative(base_url, auth):
    """边界：下单数量为负数应返回 400。

    对应缺陷 BUG-06：实际返回 201，且库存反向增加。
    """
    resp = requests.post(
        base_url + "/api/orders",
        json={"productId": 1, "quantity": -5},
        headers=auth,
        timeout=10,
    )
    assert resp.status_code == 400, "quantity=-5 应报参数错误，实际返回 %s" % resp.status_code


@pytest.mark.defect("BUG-06")
def test_order_quantity_float(base_url, auth):
    """边界：下单数量传小数应返回 400，不能静默截断。

    对应缺陷 BUG-06：1.5 被 int() 截断成 1，订单按 1 件创建，返回里没有任何提示。
    """
    resp = requests.post(
        base_url + "/api/orders",
        json={"productId": 1, "quantity": 1.5},
        headers=auth,
        timeout=10,
    )
    assert resp.status_code == 400, "quantity=1.5 应报参数错误，实际返回 %s" % resp.status_code


def test_create_product_invalid_price(base_url, auth):
    """边界：发布商品时 price 传两位小数应返回 400，price 必须是正整数分。"""
    resp = requests.post(
        base_url + "/api/products",
        json={"title": "小数价格", "price": 19.9, "stock": 5, "category": "文具"},
        headers=auth,
        timeout=10,
    )
    assert resp.status_code == 400
    assert resp.json()["code"] == 40001


@pytest.mark.defect("BUG-10")
def test_product_keyword_injection(base_url):
    """边界：keyword 应按字面量匹配，不得拼接 SQL。

    对应缺陷 BUG-10：注入串 `' OR '1'='1` 会让 WHERE 恒真，
    已下架的商品 7 被重新查出来。

    断言从「total 等于几」改成「返回的每一条都必须是在售状态」——
    前者依赖种子数据条数，后者直接对应业务承诺，更稳。
    """
    resp = requests.get(
        base_url + "/api/products",
        params={"keyword": "' OR '1'='1", "pageSize": 50},
        timeout=10,
    )
    assert resp.status_code == 200
    items = resp.json()["data"]["items"]
    off_shelf = [i["id"] for i in items if i["status"] != "on"]
    assert not off_shelf, "注入后返回了已下架商品：%s" % off_shelf
