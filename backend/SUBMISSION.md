# 提交说明（SUBMISSION）

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 任宇航 |
| 学号 | `320250945481` |
| 方向 | 后端 |
| 分支 | `320250945481` |
| 实际投入 | 约 12h |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Linux（本次验证环境为 WSL2 / Ubuntu） |
| 运行时 | JDK 17 或更高；本次使用 JDK 21.0.13，编译目标为 Java 17 |
| 框架 | Spring Boot 3.5.16、Spring MVC、JdbcTemplate、Flyway |
| 数据库 | H2（运行时文件库，测试时内存库），无需安装数据库服务 |
| 包管理器 | Maven Wrapper 3.10.1（`mvnw`），首次运行会下载依赖 |

## 启动方式（必填，要能照抄）

在项目根目录执行：

```bash
cd backend
./mvnw spring-boot:run
```

Windows 可使用 `mvnw.cmd spring-boot:run`。如果本机已安装 Maven，也可将 `./mvnw` 换成 `mvn`。

启动后访问：

```bash
curl http://localhost:8080/api/health
# {"code":0,"message":"ok","data":null}
```

- 默认端口为 **8080**，服务地址为 `http://localhost:8080`。
- 首次启动时 Flyway 自动执行 `src/main/resources/db/migration/` 中的 V1、V2、V3 迁移并建表，**不需要手工执行 `schema.sql`**。`schema.sql` 是与当前迁移一致的手写完整结构快照。
- 运行数据保存在 `backend/data/marketplace.mv.db`，重启不会自动清空。仓库不预置业务数据，商品需通过 `POST /api/products` 创建。
- `GET /h2-console` 提供 H2 控制台；JDBC URL 为 `jdbc:h2:file:./data/marketplace`，用户名 `sa`，密码为空。该配置用于本地作业，不建议暴露到公网。

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | **5 / 5**：建表、商品发布、组合筛选分页列表、商品详情、商品上下架 |
| L1 | **4 / 4**：下单（事务 + 服务端算价）、订单详情、买家手机号订单列表、参数校验 |
| L2 | 完成 **L2-9 支付链路**、**L2-10 并发不超卖**、**L2-12 自动化测试**|

已实现接口：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| POST | `/api/products` | 发布商品，成功 201 |
| GET | `/api/products` | `keyword`、`status`、`category` 可组合，默认 `page=1&pageSize=10` |
| GET | `/api/products/{id}` | 商品详情 |
| PATCH | `/api/products/{id}/status` | 商品上下架 |
| POST | `/api/orders` | 下单，成功 201 |
| GET | `/api/orders/{orderNo}` | 按订单号查询详情 |
| GET | `/api/orders` | 必须传 `buyerPhone`，支持分页，按创建时间及 ID 倒序 |
| POST | `/api/payments` | 同步支付，请求为 `orderNo` 和 `method`（`alipay` / `wechat`），成功 200；重复支付返回 409 / 40903 |

所有已实现接口的成功及业务失败响应均包含 `code`、`message`、`data`。金额全程为整数分。未实现鉴权。

## 测试 / 自检

在 `backend/` 下运行全部测试：

```bash
./mvnw clean test
```

本次实际结果：

```text
Tests run: 137, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

其中商品接口 77 条、订单接口 31 条、支付接口 18 条、真实 HTTP 并发 2 条（下单、支付各 1 条）、事务回滚 3 条（下单 1 条、支付 2 条）、数据库迁移 4 条、健康检查和应用上下文各 1 条。测试使用独立 H2 内存库，不读写 `backend/data/` 中的运行数据。

核心覆盖包括：

- 正常下单后库存减少，单价和总额取数据库商品价格；客户端附带的价格、状态、订单号不会覆盖服务端结果。
- `quantity=0`、负数、字符串、小数、布尔值和缺失字段返回 400 / 40001。
- 商品或订单不存在返回 404 / 40401；库存不足返回 409 / 40901；商品下架返回 409 / 40902。
- 商品筛选叠加、模糊匹配通配符转义、分页默认值与范围、订单手机号精确匹配和稳定倒序。
- 订单插入被强制异常时，已扣库存真实回滚。
- 支付成功后写入 `payments` 记录，订单更新为 `paid` 并写入 `paid_at`；金额取订单服务端快照，客户端附带的金额、状态和支付编号不会生效。
- 重复支付返回 409 / 40903，且不会新增支付记录或覆盖首次支付记录；`orderNo` 不存在返回 404 / 40401，`method` 非 `alipay` / `wechat` 返回 400 / 40001。
- 支付记录插入被强制异常时，订单状态和支付时间真实回滚为未支付。
- 支付记录插入成功后读取失败时，订单更新和支付记录一起回滚；插入故障解除后同一订单可以重试成功。
- 商品改价、下架后支付仍使用订单金额快照，不重复扣库存；订单、支付记录和成功响应的支付时间一致。

### L2-9 支付测试：准备、运行、核对

**数据准备**：无需启动应用或手工建数据。`PaymentControllerTest` 通过 MockMvc 调用下单接口创建待支付订单，`PaymentConcurrencyTest` 在独立 H2 内存库中准备一个 `created` 订单。

```bash
cd backend
./mvnw '-Dtest=PaymentControllerTest,PaymentTransactionTest,PaymentConcurrencyTest' test
```

**核对方式**：

1. `PaymentControllerTest` 断言成功响应包含 `paymentId`、`orderNo`、`amount`、`status=paid`、`paidAt`，并直接查询 `orders` / `payments` 表核对订单状态和支付记录；随后用相同 `orderNo` 再支付一次，断言 409 / 40903、支付记录仍只有首次一条。
2. `PaymentTransactionTest` 不加外层事务，通过 Spy 强制支付插入失败，再用独立连接确认订单仍为 `created`、`paid_at` 为空、支付记录为 0。
3. `PaymentConcurrencyTest` 通过随机端口上的真实 HTTP 服务同时发起 10 次支付，等待全部响应后断言恰好 1 个 200、9 个 409 / 40903，最终只有 1 条支付记录且订单为 `paid`。

手工调用时，先按下单接口创建订单，将返回的 `orderNo` 填入下面的变量。在服务已启动的另一个终端执行：

```bash
payment_order_no='替换为刚创建订单的 orderNo'
curl -i http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -d "{\"orderNo\":\"${payment_order_no}\",\"method\":\"alipay\"}"
curl -i "http://localhost:8080/api/orders/${payment_order_no}"
```

首次支付应返回 HTTP 200，`data` 包含 `paymentId`、订单号、订单总额（分）、`status=paid` 和 UTC `paidAt`；订单详情也应为 `paid`，且支付时间相同。再次执行同一支付命令应返回 HTTP 409 / 40903。此处为作业同步模拟支付，不会调用第三方渠道或产生真实扣款。

### L2-10 并发测试：准备、运行、核对

**数据准备**：无需启动应用或手工建数据。`OrderConcurrencyTest` 自动在独立的 H2 内存库中插入一个 `status='on'`、`stock=1` 的商品，然后创建 10 个客户端线程。每个线程通过 JDK `HttpClient` 向随机端口上真实运行的 Spring MVC 服务发送 1 个购买 1 件的 HTTP 请求。

单独运行：

```bash
cd backend
./mvnw -Dtest=OrderConcurrencyTest test
```

**核对方式**：测试使用 `ready` / `start` 两个闩锁让 10 个线程就绪后同时放行，等待全部 HTTP 响应后再自动断言：

1. 恰好 **1 个响应为 HTTP 201**，业务码为 0。
2. 其余 **9 个响应均为 HTTP 409**，业务码为 40901、消息为“库存不足”。
3. 最终商品库存为 **0**，该商品本轮订单数为 **1**，订单总额为 1000 分。

任一项不满足测试都会失败，不需要人工读取临时数据库。命令显示 `Tests run: 1, Failures: 0, Errors: 0` 即通过；摘要也可查看 `target/surefire-reports/com.lzu.marketplace.order.OrderConcurrencyTest.txt`。测试结束清理内存库数据，可直接重复运行。

### 事务回滚专项测试

```bash
./mvnw -Dtest=OrderTransactionTest test
```

测试没有在外层添加 `@Transactional`，通过 Spy 强制 `OrderRepository.insert` 抛出数据库异常，然后在独立数据库连接中确认库存仍为 5、订单数为 0，避免外层测试事务掩盖真实提交/回滚行为。

### 支付事务回滚专项测试

```bash
./mvnw -Dtest=PaymentTransactionTest test
```

测试通过 Spy 强制 `PaymentRepository.insert` 抛出数据库异常，并模拟插入后读取失败，确认订单更新与支付记录在同一事务中一起回滚；插入故障解除后重试成功，并检查已提交数据。

## 没做完的地方

| 项 | 状态 | 原因 |
|---|---|---|
| L2-11 鉴权 | 未实现 | 本次优先保证商品、订单、支付及并发正确性 |
| L2-13 索引对比说明 | 未申报 | 已有订单查询索引，但尚未完成同数据的索引前后 `EXPLAIN` 对照 |

## 自检清单

- [x] 已从 `backend/` 执行 `./mvnw clean test`，137 条测试通过。
- [x] 未提交 `target/`、本地 H2 数据文件或 `.env`；根 `.gitignore` 的 `*.db` 可匹配 H2 的 `.mv.db` 文件。
- [x] `DESIGN.md` 已按当前实现填写。
- [x] `AI_USAGE.md` 仍需本人结合平台日志填写完整。
- [x] `backend/README.md` 题目文件未修改。
