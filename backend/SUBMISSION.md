# 提交说明（SUBMISSION）

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 待填写 |
| 学号 | 220250946451 |
| 方向 | 后端 |
| 分支 | `220250946451` |
| 实际投入 | 待填写 |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Windows 11 |
| JDK | 19.0.2 |
| Maven | 3.6.1 |
| Spring Boot | 3.0.13 |
| MyBatis Spring Boot Starter | 3.0.2 |
| 数据库 | H2 内存模式，重启后数据清空 |

## 启动方式

在仓库根目录执行下面的命令。Maven 会在首次启动时下载依赖。应用启动后会执行 schema.sql，在 H2 中创建商品、订单和支付表。

```powershell
cd backend
mvn spring-boot:run
```

## 测试 / 自检

在 `backend/` 下执行 `mvn test` 运行接口测试，或执行 `mvn clean package` 同时验证测试与打包。当前完整测试套件共 75 条，覆盖商品发布与参数校验、列表筛选分页、详情查询、上下架状态更新、下单金额计算、订单详情与列表、商品不存在、库存不足、商品下架、并发不超卖、下单事务回滚，以及支付成功、重复支付、并发重复支付、唯一键兜底和支付记录查询。

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-19'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd backend
& mvn '-Dmaven.repo.local=C:\Users\18537\.m2\repository' test
& mvn '-Dmaven.repo.local=C:\Users\18537\.m2\repository' clean package
```

应用启动后在另一个终端执行以下命令。健康检查通过 MyBatis Mapper XML 在 H2 中执行 `SELECT 1`。

```powershell
curl.exe -i http://localhost:8080/api/health
```

已在 Windows 11 上通过构建和启动验证。健康检查实际返回 HTTP 200，响应体如下。

```json
{"code":0,"message":"ok","data":null}
```

## POST /api/orders 实现

请求体保持题目约定格式：

```json
{
  "productId": 3,
  "quantity": 2,
  "buyerName": "张三",
  "buyerPhone": "13800138000",
  "address": "兰州市城关区天水南路222号"
}
```

成功时返回 HTTP 201 和统一响应结构，金额由服务端按数据库中的商品价格计算，不接受客户端传入金额。错误映射如下：

| 场景 | HTTP | code |
|---|---|---|
| 参数缺失、类型错误、productId 非正数或数量小于 1 | 400 | `40001` |
| 商品不存在 | 404 | `40401` |
| 库存不足 | 409 | `40901` |
| 商品已下架 | 409 | `40902` |

扣库存使用单条条件更新，判断和扣减在数据库内原子完成：

```sql
UPDATE products
SET stock = stock - #{quantity}
WHERE id = #{id}
  AND status = 'on'
  AND stock >= #{quantity}
```

`OrderService.create` 使用 `READ_COMMITTED` 事务。先条件扣库存，再读取商品快照、插入订单并查询订单结果；任意步骤异常时，事务整体回滚，已经扣减的库存会恢复。

该接口没有实现服务端幂等，也没有引入 Redis。前端可以通过按钮防抖、请求进行中禁用按钮等方式降低重复提交概率，但不能替代服务端幂等保证；在“不改请求格式、不增加幂等键”的约束下，本实现明确接受这一限制。

## POST /api/payments 实现（L2-9）

请求体：

```json
{
  "orderNo": "ORD202611250001",
  "method": "alipay"
}
```

`method` 只接受 `alipay` 或 `wechat`。支付成功返回 HTTP 200，订单状态改为 `paid`，写入订单和支付记录共用的 `paid_at`，并返回 `status = "paid"`。支付单号格式为 `PAY + yyyyMMdd + 12 位大写十六进制随机串`。

另外提供 `GET /api/payments/{paymentId}` 查询成功支付记录，不存在时返回 404 + `40401`。

| 场景 | HTTP | code |
|---|---|---|
| 参数缺失、`method` 非法 | 400 | `40001` |
| 订单不存在 | 404 | `40401` |
| 支付记录不存在 | 404 | `40401` |
| 订单已支付、重复支付 | 409 | `40903` |

幂等由两层数据库约束保证：

1. `PaymentService.create` 在单个事务内先执行条件更新：

```sql
UPDATE orders
SET status = 'paid',
    paid_at = #{paidAt}
WHERE id = #{id}
  AND status = 'created'
```

只有成功把订单从 `created` 改为 `paid` 的请求才继续插入支付记录；其他并发请求更新 0 行，返回 409 + `40903`。

2. `payments.order_id` 有 `UNIQUE` 约束。若出现订单仍为 `created`、支付记录却已存在的数据不一致，插入会触发唯一键冲突，并被转换为同一个 409 + `40903`。条件更新和支付插入处于同一事务，冲突时订单状态与 `paid_at` 一并回滚。

自动化测试文件：`src/test/java/com/example/marketplace/controller/PaymentApiTest.java`，共 8 条，覆盖成功支付、订单状态与 `paid_at`、重复支付、按支付单号查询、支付记录不存在、唯一键冲突回滚、订单不存在、非法支付方式，以及 10 个线程同时支付同一订单时恰好 1 个成功、9 个 409、最终只有 1 条支付记录。

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-19'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd backend
& mvn '-Dmaven.repo.local=C:\Users\18537\.m2\repository' '-Dtest=PaymentApiTest' test
```
## 并发不超卖验证（L2-10）

自动化测试文件：`src/test/java/com/example/marketplace/controller/OrderConcurrencyApiTest.java`。

**准备数据**：测试会通过 `POST /api/products` 自动创建一个已上架、库存为 1 的商品，不需要手工准备数据库记录。

**并发请求**：用 10 个线程和 `CountDownLatch` 同时发起 10 个有效的 `POST /api/orders` 请求，每个请求购买 1 件。

**运行方式**：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-19'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd backend
& mvn '-Dmaven.repo.local=C:\Users\18537\.m2\repository' '-Dtest=OrderConcurrencyApiTest' test
```

**核对方式**：

- HTTP 201 恰好 1 个；
- HTTP 409 恰好 9 个，且响应中的 `code` 全部为 `40901`；
- 使用 `GET /api/products/{id}` 查询，最终库存恰好为 0；
- `orders` 表中该商品的订单数恰好为 1。

并发安全由数据库条件更新保证，不使用 Redis、`version` 乐观锁字段、`SELECT ... FOR UPDATE` 或应用层重试。测试使用独立 H2 数据库 `order-concurrency-test`，避免与其他 Spring 测试上下文共享数据库。

事务回滚另由 `OrderRollbackApiTest` 验证：固定订单号制造唯一约束冲突，第二次下单返回 500 后库存仍只减少第一次成功下单的数量，订单数仍为 1。

## 我做到了哪一步

目前已初始化工程并完成建表、健康检查、商品发布、商品列表、商品详情、商品上下架和 L1-6 下单接口。

| 层级 | 计划范围 | 完成情况 |
|---|---|---|
| L0 | 建表和商品接口 | L0-1 至 L0-5 已完成（发布、列表、详情、上下架） |
| L1 | 下单扣库存和订单查询 | L1-6 下单、L1-7 订单详情、L1-8 订单列表均已完成 |
| L2 | 支付链路、并发、鉴权 | L2-9 支付幂等与 L2-10 并发不超卖已完成；其余挑战待开发 |

## 自检清单

- [x] 执行干净构建并按上面的命令启动
- [x] 并发不超卖测试达到 1 个成功、9 个 409、库存 0、订单 1 条
- [x] 确认提交中没有 `target/`、`*.db` 或 `.env`
- [ ] 填写完整的 `DESIGN.md` 和 `AI_USAGE.md`
