from concurrent.futures import ThreadPoolExecutor
import threading

import requests


def test_l2_ten_concurrent_requests_cannot_oversell_last_item(sut, db):
    """
    L2-8：10个请求同时购买商品5（初始库存1）。
    正确结果应为1笔订单成功、9笔库存不足，最终库存为0。
    """
    product_id = 5
    initial = db(
        "SELECT stock FROM products WHERE id = ?",
        (product_id,),
    )[0]["stock"]
    assert initial == 1, f"商品{product_id}初始库存应为1，实际为{initial}"

    before_orders = db(
        "SELECT COUNT(*) AS n FROM orders WHERE product_id = ?",
        (product_id,),
    )[0]["n"]

    barrier = threading.Barrier(10)
    url = sut["base_url"] + "/api/orders"
    headers = {"Authorization": f"Bearer {sut['token']}"}

    def submit_order(_):
        barrier.wait(timeout=10)
        return requests.post(
            url,
            headers=headers,
            json={"productId": product_id, "quantity": 1},
            timeout=15,
        )

    with ThreadPoolExecutor(max_workers=10) as pool:
        responses = list(pool.map(submit_order, range(10)))

    statuses = [r.status_code for r in responses]
    success_count = statuses.count(201)
    rejected_count = statuses.count(409)

    final_stock = db(
        "SELECT stock FROM products WHERE id = ?",
        (product_id,),
    )[0]["stock"]
    after_orders = db(
        "SELECT COUNT(*) AS n FROM orders WHERE product_id = ?",
        (product_id,),
    )[0]["n"]
    new_orders = after_orders - before_orders

    print("\\n========== L2-8 十并发库存竞争测试 ==========")
    print("并发请求数：10")
    print("初始库存：", initial)
    print("HTTP状态码：", statuses)
    print("成功订单数：", success_count)
    print("库存不足拒绝数：", rejected_count)
    print("最终库存：", final_stock)
    print("新增订单数：", new_orders)

    assert all(status in (201, 409) for status in statuses), (
        f"出现非预期 HTTP 状态：{statuses}"
    )
    assert success_count == 1, (
        f"库存为1时应仅有1个请求成功，实际成功{success_count}个；"
        f"状态码={statuses}"
    )
    assert rejected_count == 9, (
        f"其余9个请求应因库存不足返回409，实际拒绝{rejected_count}个"
    )
    assert final_stock == 0, f"最终库存应为0，实际为{final_stock}"
    assert new_orders == 1, f"应只新增1笔订单，实际新增{new_orders}笔"
