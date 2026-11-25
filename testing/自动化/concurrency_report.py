# -*- coding: utf-8 -*-
"""L2-8 并发测试：10 个请求同时购买库存为 1 的商品，验证库存是否变负。

用法（服务需已启动）：
    python testing/自动化/concurrency_report.py
    SUT_BASE_URL=http://127.0.0.1:8090 python testing/自动化/concurrency_report.py

与 test_order.py::test_concurrent_order_no_oversell 的区别：
- 那个是 pytest 断言用例（判定通过/失败）
- 这个是**取证脚本**，输出完整数字结论，可直接贴进测试报告
"""
import os
import sys
import threading

import requests

BASE = os.environ.get("SUT_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
PRODUCT_ID = 5          # 种子数据中库存为 1 的商品：轻薄防晒外套
CONCURRENCY = 10
ROUNDS = 3


def login(username="alice", password="Alice@123"):
    r = requests.post(BASE + "/api/auth/login",
                      json={"username": username, "password": password}, timeout=10)
    r.raise_for_status()
    return r.json()["data"]["token"]


def stock_of(pid):
    return requests.get("%s/api/products/%s" % (BASE, pid), timeout=10).json()["data"]["stock"]


def run_round(token, pid):
    """一轮并发：Barrier 对齐起跑点，确保请求真正同时发出。"""
    statuses = []
    bodies = []
    lock = threading.Lock()
    barrier = threading.Barrier(CONCURRENCY)

    def worker():
        barrier.wait()
        try:
            r = requests.post(BASE + "/api/orders", json={"productId": pid, "quantity": 1},
                              headers={"Authorization": "Bearer " + token}, timeout=30)
            with lock:
                statuses.append(r.status_code)
                bodies.append(r.text[:160])
        except Exception as exc:  # noqa: BLE001
            with lock:
                statuses.append("EXC")
                bodies.append(repr(exc))

    threads = [threading.Thread(target=worker) for _ in range(CONCURRENCY)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return statuses, bodies


def main():
    token = login()

    before = stock_of(PRODUCT_ID)
    print("商品 id=%s 初始库存：%s" % (PRODUCT_ID, before))
    if before <= 0:
        print("初始库存不是正数，请重启 SUT 恢复种子数据后重跑。")
        return 1

    print("并发数：%d　轮次：%d\n" % (CONCURRENCY, ROUNDS))

    worst_stock = before
    for i in range(1, ROUNDS + 1):
        statuses, bodies = run_round(token, PRODUCT_ID)
        after = stock_of(PRODUCT_ID)
        ok = statuses.count(201)
        conflict = statuses.count(409)
        print("第 %d 轮：成功下单 %d　409 拒绝 %d　库存 %d -> %d"
              % (i, ok, conflict, before, after))
        if after < worst_stock:
            worst_stock = after
        before = after

    # 落库核对：订单条数与数量合计
    orders = requests.get(BASE + "/api/orders/mine",
                          headers={"Authorization": "Bearer " + token}, timeout=10).json()["data"]
    hits = [o for o in orders["items"] if o["productId"] == PRODUCT_ID]

    print("\n" + "=" * 62)
    print("结论")
    print("=" * 62)
    print("商品 id=%s 的订单条数：%d" % (PRODUCT_ID, len(hits)))
    print("这些订单的 quantity 合计：%d" % sum(o["quantity"] for o in hits))
    print("当前库存：%d" % stock_of(PRODUCT_ID))
    print("库存最小值：%d" % worst_stock)

    if worst_stock < 0:
        print("\n[结论] 存在超卖：库存被扣成负数，最小 %d。" % worst_stock)
        print("       文档承诺「库存永远不能小于 0」，应为缺陷。对应 BUG-12。")
        return 1

    print("\n[结论] 未观察到超卖，库存始终非负。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
