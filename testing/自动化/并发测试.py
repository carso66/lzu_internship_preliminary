#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""L2-8 并发不超卖测试 —— 可复跑脚本。

准备数据：
    停掉 SUT -> 删除 sut/sut.db -> 重新 `python app.py --port 8080`
    商品 id=5（轻薄防晒外套）库存为 1，可直接用于本测试。

运行：
    python 并发测试.py
    python 并发测试.py --base http://127.0.0.1:8090 --threads 10

核对两项结果：
    1) 响应：恰好 1 个请求返回 201，其余 9 个返回 409「库存不足」
    2) 数据：结束后商品 5 库存恰好为 0，本轮新增订单恰好 1 条

说明：本脚本仅用 Python 标准库，不依赖 requests。
"""

import argparse
import json
import sys
import threading
import urllib.error
import urllib.request

PRODUCT_ID = 5          # 库存为 1 的商品
USER = {"username": "alice", "password": "Alice@123"}


def request_json(base, method, path, data=None, token=None, timeout=15):
    body = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(base + path, data=body, method=method)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8")
        try:
            return exc.code, json.loads(raw)
        except ValueError:
            return exc.code, {"raw": raw}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--threads", type=int, default=10)
    args = parser.parse_args()
    base = args.base.rstrip("/")

    status, body = request_json(base, "POST", "/api/auth/login", USER)
    if status != 200:
        print("登录失败，检查 SUT 是否已启动：HTTP %s %s" % (status, body))
        return 1
    token = body["data"]["token"]
    print("登录成功，token = %s" % token)

    _, detail = request_json(base, "GET", "/api/products/%d" % PRODUCT_ID, token=token)
    stock_before = detail["data"]["stock"]
    print("商品 %d 下单前库存 = %s" % (PRODUCT_ID, stock_before))

    _, before = request_json(base, "GET", "/api/orders/mine", token=token)
    orders_before = before["data"]["total"]
    print("下单前该用户订单数 = %d" % orders_before)

    barrier = threading.Barrier(args.threads)
    results = []
    lock = threading.Lock()

    def buy():
        barrier.wait()
        code, resp = request_json(
            base, "POST", "/api/orders",
            {"productId": PRODUCT_ID, "quantity": 1}, token=token,
        )
        with lock:
            results.append((code, resp.get("code"), resp.get("message")))

    threads = [threading.Thread(target=buy) for _ in range(args.threads)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    success = sum(1 for code, *_ in results if code == 201)
    conflict = sum(1 for code, *_ in results if code == 409)
    print("")
    print("并发 %d 个请求：201 成功 %d 个，409 库存不足 %d 个，其他 %d 个"
          % (args.threads, success, conflict, args.threads - success - conflict))
    for code, biz, msg in sorted(results):
        print("    HTTP %s  code=%s  %s" % (code, biz, msg))

    _, after_detail = request_json(base, "GET", "/api/products/%d" % PRODUCT_ID, token=token)
    stock_after = after_detail["data"]["stock"]
    _, after = request_json(base, "GET", "/api/orders/mine", token=token)
    orders_after = after["data"]["total"]

    print("")
    print("商品 %d 下单后库存 = %s" % (PRODUCT_ID, stock_after))
    print("新增订单数 = %d" % (orders_after - orders_before))
    print("")
    expected_ok = 1
    ok_pass = success == expected_ok
    ok_stock = stock_after == 0
    ok_order = (orders_after - orders_before) == 1
    print("期望：成功 1 个 / 库存为 0 / 新增订单 1 条")
    print("判定：成功数 %s，库存 %s，订单数 %s"
          % ("符合" if ok_pass else "不符合",
             "符合" if ok_stock else "不符合",
             "符合" if ok_order else "不符合"))
    if not (ok_pass and ok_stock and ok_order):
        print("结论：存在超卖缺陷，库存被扣成 %s，实际卖出 %d 件而库存只有 %s 件"
              % (stock_after, success, stock_before))
    return 0


if __name__ == "__main__":
    sys.exit(main())
