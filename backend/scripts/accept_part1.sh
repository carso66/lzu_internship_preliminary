#!/usr/bin/env bash
# SUBMISSION.md 的验收脚本，全部 ASCII，避免终端编码干扰
BASE=http://localhost:8080
J='Content-Type: application/json'
hit() { curl -s -w '\n<<HTTP %{http_code}>>\n' "$@"; }

echo "===== 0. health ====="
hit "$BASE/api/health"

echo
echo "===== L0-1 schema / empty list ====="
hit "$BASE/api/products?page=1&pageSize=1"

echo
echo "===== L0-2 create product -> 201 ====="
hit -X POST "$BASE/api/products" -H "$J" -d '{"title":"Math Review Book","price":4990,"stock":10,"category":"books","description":"good","sellerName":"LZU Bookstore"}'

echo
echo "----- L0-2 validation: blank title / negative price / string price / float price -----"
hit -X POST "$BASE/api/products" -H "$J" -d '{"title":"","price":100,"stock":1,"category":"books","sellerName":"x"}'
hit -X POST "$BASE/api/products" -H "$J" -d '{"title":"x","price":-1,"stock":1,"category":"books","sellerName":"x"}'
hit -X POST "$BASE/api/products" -H "$J" -d '{"title":"x","price":"1990","stock":1,"category":"books","sellerName":"x"}'
hit -X POST "$BASE/api/products" -H "$J" -d '{"title":"x","price":1990.5,"stock":1,"category":"books","sellerName":"x"}'

echo
echo "===== L0-3 list / filter / pageSize=51 ====="
hit "$BASE/api/products?page=1&pageSize=10"
hit "$BASE/api/products?keyword=Math&status=on&category=books&page=1&pageSize=10"
hit "$BASE/api/products?page=1&pageSize=51"

echo
echo "===== L0-4 detail / 404 ====="
hit "$BASE/api/products/1"
hit "$BASE/api/products/999999"

echo
echo "===== L0-5 on-off ====="
hit -X PATCH "$BASE/api/products/1/status" -H "$J" -d '{"status":"off"}'
hit -X PATCH "$BASE/api/products/1/status" -H "$J" -d '{"status":"ON"}'
hit -X PATCH "$BASE/api/products/1/status" -H "$J" -d '{"status":"on"}'

echo
echo "===== L1-6 create order -> 201 (server-side price) ====="
hit -X POST "$BASE/api/orders" -H "$J" -d '{"productId":1,"quantity":2,"buyerName":"Zhang San","buyerPhone":"13800138000","address":"Lanzhou Chengguan Tianshui South Rd 222"}'
