#!/usr/bin/env python3
"""L2-10 acceptance: N parallel orders against a product with limited stock.

Verifies the "no oversell" guarantee end to end over real HTTP:
  * exactly `stock` requests should succeed (201)
  * the rest must fail with 409 / 40901 (stock not enough)
  * the product stock must end at 0 (never negative)
  * the number of persisted orders must equal the number of successes

Only the Python standard library is used, so no pip install is needed.

Usage:
    python3 backend/scripts/concurrent_order.py
    python3 backend/scripts/concurrent_order.py --concurrency 20 --stock 3
    python3 backend/scripts/concurrent_order.py --base-url http://localhost:8080

Exit code is 0 when every check passes, 1 otherwise.
"""

import argparse
import json
import random
import sys
import threading
import urllib.error
import urllib.request


def _request(method, url, body=None):
    """Return (http_status, parsed_json). Never raises on 4xx/5xx."""
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8")
        try:
            return exc.code, json.loads(raw)
        except json.JSONDecodeError:
            return exc.code, {"raw": raw}


def create_product(base_url, stock):
    status, body = _request(
        "POST",
        base_url + "/api/products",
        {
            "title": "concurrency test item",
            "price": 1999,
            "stock": stock,
            "category": "test",
            "description": "created by concurrent_order.py",
            "sellerName": "ci",
        },
    )
    if status != 201:
        sys.exit("failed to create product: HTTP %s %s" % (status, body))
    return body["data"]["id"]


def fire(base_url, product_id, quantity, results, index, barrier, phone):
    """One client thread: wait at the barrier, then race for the stock."""
    barrier.wait()
    status, body = _request(
        "POST",
        base_url + "/api/orders",
        {
            "productId": product_id,
            "quantity": quantity,
            "buyerName": "concurrency",
            "buyerPhone": phone,
            "address": "Lanzhou University",
        },
    )
    code = body.get("code") if isinstance(body, dict) else None
    results[index] = (status, code, body.get("message", ""))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--concurrency", type=int, default=10)
    parser.add_argument("--stock", type=int, default=1)
    args = parser.parse_args()

    base = args.base_url.rstrip("/")
    try:
        _request("GET", base + "/api/health")
    except OSError as exc:
        sys.exit("cannot reach %s (%s). Start the service first:\n"
                 "  cd backend && mvn spring-boot:run" % (base, exc))

    product_id = create_product(base, args.stock)
    # A fresh phone number per run keeps the order-count query from picking up
    # orders left behind by an earlier run (the order list filters by phone).
    phone = "138%08d" % random.randint(0, 99999999)
    print("product id=%d stock=%d, buyerPhone=%s, firing %d concurrent orders..."
          % (product_id, args.stock, phone, args.concurrency))

    results = [None] * args.concurrency
    barrier = threading.Barrier(args.concurrency)
    threads = [
        threading.Thread(target=fire,
                         args=(base, product_id, 1, results, i, barrier, phone))
        for i in range(args.concurrency)
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    created = sum(1 for r in results if r[0] == 201)
    conflicts = sum(1 for r in results if r[0] == 409)
    others = [r for r in results if r[0] not in (201, 409)]

    print("\n--- HTTP status distribution ---")
    buckets = {}
    for status, code, _ in results:
        buckets[(status, code)] = buckets.get((status, code), 0) + 1
    for (status, code), count in sorted(buckets.items()):
        print("  HTTP %-3s code=%-6s x%d" % (status, code, count))

    status, body = _request("GET", "%s/api/products/%d" % (base, product_id))
    final_stock = body["data"]["stock"]
    status, body = _request(
        "GET", "%s/api/orders?buyerPhone=%s&pageSize=50" % (base, phone))
    order_total = body["data"]["total"]

    print("\n--- resulting state ---")
    print("  product stock left : %d (started at %d)" % (final_stock, args.stock))
    print("  orders persisted   : %d" % order_total)
    print("  201 / 409 / other  : %d / %d / %d"
          % (created, conflicts, len(others)))

    expected_success = min(args.stock, args.concurrency)
    checks = [
        ("successes == min(stock, concurrency) = %d" % expected_success,
         created == expected_success),
        ("every failure is 409/40901",
         all(code == 40901 for status, code, _ in others) if others else True),
        ("final stock == %d" % (args.stock - expected_success),
         final_stock == args.stock - expected_success),
        ("final stock never negative", final_stock >= 0),
        ("orders persisted == successes", order_total == created),
    ]

    print("\n--- checks ---")
    failed = False
    for label, ok in checks:
        print("  [%s] %s" % ("PASS" if ok else "FAIL", label))
        failed = failed or not ok

    if others:
        print("\nunexpected responses:")
        for status, code, message in others:
            print("  HTTP %s code=%s %s" % (status, code, message))

    print("\nRESULT: %s" % ("OVERSOLD / MISMATCH" if failed else "NO OVERSELL"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
