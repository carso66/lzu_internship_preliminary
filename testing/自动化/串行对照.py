#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""串行对照实验：验证「计数逻辑与库存读取」本身没写错。

和 并发测试.py 打同一个接口、同一个商品、同样的请求次数，
唯一区别是 **一条一条发，不并发**。

预期（服务端正确时）：第 1 条 201，后 9 条 409，库存 0，新增订单 1 条。
如果串行也是 200 全过、库存变负，说明问题不在并发，而在库存校验本身。
如果串行结果完全正确，那就反过来证明：并发那次的 -6 只能由并发导致，
不是脚本把响应数错了、也不是脚本把库存读错了。

运行前提：数据重置，商品 5 库存为 1。
    python 串行对照.py
"""

import json
import sys
import urllib.error
import urllib.request

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://127.0.0.1:8080"
PRODUCT_ID = 5
ROUNDS = 10
USER = {"username": "alice", "password": "Alice@123"}


def call(method, path, data=None, token=None):
    body = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(BASE + path, data=body, method=method)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8")
        try:
            return exc.code, json.loads(raw)
        except ValueError:
            return exc.code, {"raw": raw}


def main():
    status, body = call("POST", "/api/auth/login", USER)
    if status != 200:
        print("登录失败：HTTP %s %s" % (status, body))
        return 1
    token = body["data"]["token"]

    _, detail = call("GET", "/api/products/%d" % PRODUCT_ID, token=token)
    print("串行实验开始前，商品 %d 库存 = %s" % (PRODUCT_ID, detail["data"]["stock"]))

    _, before = call("GET", "/api/orders/mine", token=token)
    orders_before = before["data"]["total"]

    results = []
    for i in range(ROUNDS):
        code, resp = call(
            "POST", "/api/orders",
            {"productId": PRODUCT_ID, "quantity": 1}, token=token,
        )
        results.append((code, resp.get("code"), resp.get("message")))
        print("  第 %2d 条：HTTP %s  code=%s  %s"
              % (i + 1, code, resp.get("code"), resp.get("message")))

    success = sum(1 for c, *_ in results if c == 201)
    conflict = sum(1 for c, *_ in results if c == 409)

    _, after_detail = call("GET", "/api/products/%d" % PRODUCT_ID, token=token)
    stock_after = after_detail["data"]["stock"]
    _, after = call("GET", "/api/orders/mine", token=token)
    added = after["data"]["total"] - orders_before

    print("")
    print("串行 %d 条：201 成功 %d 个，409 库存不足 %d 个" % (ROUNDS, success, conflict))
    print("商品 %d 结束后库存 = %s，新增订单 = %d 条"
          % (PRODUCT_ID, stock_after, added))
    ok = success == 1 and stock_after == 0 and added == 1
    print("结论：%s" % ("串行结果完全正确，库存没有被扣成负数" if ok
                       else "串行也会超卖，问题不在并发，而在库存校验本身"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
