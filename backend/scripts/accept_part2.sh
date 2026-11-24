#!/usr/bin/env bash
# 承接 part1 的状态：商品 1 = price 4990, stock 8；订单 SO20261124000001
BASE=http://localhost:8080
J='Content-Type: application/json'
hit() { curl -s -w '\n<<HTTP %{http_code}>>\n' "$@"; }

echo "===== L1-6 order errors ====="
echo "--- quantity=0 ---"
hit -X POST "$BASE/api/orders" -H "$J" -d '{"productId":1,"quantity":0,"buyerName":"Li Si","buyerPhone":"13800138001","address":"addr"}'
echo "--- product 999999 ---"
hit -X POST "$BASE/api/orders" -H "$J" -d '{"productId":999999,"quantity":1,"buyerName":"Li Si","buyerPhone":"13800138001","address":"addr"}'
echo "--- stock not enough (qty 999) ---"
hit -X POST "$BASE/api/orders" -H "$J" -d '{"productId":1,"quantity":999,"buyerName":"Li Si","buyerPhone":"13800138001","address":"addr"}'
echo "--- off shelf ---"
hit -X PATCH "$BASE/api/products/1/status" -H "$J" -d '{"status":"off"}' > /dev/null
hit -X POST "$BASE/api/orders" -H "$J" -d '{"productId":1,"quantity":1,"buyerName":"Li Si","buyerPhone":"13800138001","address":"addr"}'
hit -X PATCH "$BASE/api/products/1/status" -H "$J" -d '{"status":"on"}' > /dev/null
echo "--- stock still 8 after all failures ---"
hit "$BASE/api/products/1"

echo
echo "===== L1-7 order detail ====="
hit "$BASE/api/orders/SO20261124000001"
hit "$BASE/api/orders/SO20261124999999"

echo
echo "===== L1-8 order list by phone ====="
hit "$BASE/api/orders?buyerPhone=13800138000&page=1&pageSize=10"
hit "$BASE/api/orders?page=1&pageSize=10"

echo
echo "===== L2-9 pay ====="
echo "--- first pay -> 200 ---"
hit -X POST "$BASE/api/payments" -H "$J" -d '{"orderNo":"SO20261124000001","method":"alipay"}'
echo "--- second pay same order -> 409/40903 ---"
hit -X POST "$BASE/api/payments" -H "$J" -d '{"orderNo":"SO20261124000001","method":"wechat"}'
echo "--- order now paid ---"
hit "$BASE/api/orders/SO20261124000001"
echo "--- order not found ---"
hit -X POST "$BASE/api/payments" -H "$J" -d '{"orderNo":"SO20261124999999","method":"alipay"}'
echo "--- bad method ---"
hit -X POST "$BASE/api/payments" -H "$J" -d '{"orderNo":"SO20261124000001","method":"paypal"}'
