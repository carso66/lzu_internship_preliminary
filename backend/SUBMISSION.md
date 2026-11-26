# 提交说明（SUBMISSION）

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 梁宇超 |
| 学号 | 220260947671 |
| 方向 | 后端 |
| 分支 | `220260947671` |
| 实际投入 | 6 小时 |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Windows 11 |
| 运行时 | JDK 17（OpenJDK 17.0.21） |
| 包管理器 | Maven（项目自带 Maven Wrapper，无需本机安装 Maven） |
| 数据库 | H2 内存库（无需外部依赖） |

## 启动方式

```bash
cd backend
./mvnw spring-boot:run
```

Windows 下把 `./mvnw` 换成 `mvnw.cmd`。若本机已装 Maven，也可以用 `mvn spring-boot:run`。

服务启动后打开 <http://localhost:8080/api/health>，应看到：

```json
{"code":0,"message":"ok","data":null}
```

数据库是 H2 内存库，每次启动从 `schema.sql` 重建三张空表，**不需要任何外部依赖或手工准备数据**。

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | 5 / 5 条（建表 / 发布 / 列表 / 详情 / 上下架） |
| L1 | 4 / 4 条（下单 / 订单详情 / 订单列表 / 参数校验） |
| L2 | 全部 5 项：L2-9 支付（含幂等）、L2-10 并发不超卖、L2-11 鉴权、L2-12 自动化测试、L2-13 索引专项 |

L2 计分规则是「任选 1~2 个，每个 6 分，两个封顶 10」，所以 L2-9 + L2-10 已拿满分；L2-11 与 L2-13 不追加计分，但作为完整性与面试素材一并完成。L2-12（自动化测试）不在选做计分内，同时它也是 L2-10「可重跑测试」的载体。

## 测试 / 自检

在 `backend/` 下运行全部测试：

```bash
cd backend
./mvnw clean test
```

Windows 下用 `mvnw.cmd clean test`，或者本机有 Maven 时用 `mvn clean test`。

**实际执行输出**（本次提交前真实运行）：

```text
[INFO] Results:
[INFO]
[INFO] Tests run: 242, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
[INFO] Total time:  20.419 s
[INFO] Finished at: 2026-11-24T16:18:09+08:00
```

21 个测试类的分工：

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `ProductListTest` | 36 | L0-3 列表：三条件叠加、keyword 通配符转义、分页边界、越界不夹断 |
| `ProductCreateTest` | 30 | L0-2 发布：字段长度/类型/范围、非法 JSON、SQL 注入按字面量存储 |
| `CommonLayerTest` | 23 | 统一响应结构、业务码映射、500 不泄漏堆栈 |
| `ProductDetailAndStatusTest` | 21 | L0-4 详情、L0-5 上下架幂等与非法枚举 |
| `OrderQueryTest` | 20 | L1-7 详情、L1-8 列表：倒序、同时间戳稳定翻页、买家隔离 |
| `OrderValidationTest` | 20 | L1-6 校验顺序短路、买家字段长度与空白 |
| `OrderCreateTest` | 17 | L1-6 下单成功、服务端算价、库存不足/下架/商品不存在、quantity 类型 |
| `JsonStrictnessTest` | 12 | Jackson 严格类型配置确实绑定到 ObjectMapper |
| `SchemaConstraintTest` | 12 | 三表约束：负库存、重复单号、金额不符、重复支付被拒 |
| `PaymentTest` | 10 | L2-9 支付成功、金额服务端决定、重复支付 40903 |
| `AuthTest` | 9 | **L2-11 鉴权**：登录签发 token、401 业务码 40101/40102、保护范围、公开接口不受影响 |
| `ProductBoundaryTest` | 6 | 边界值恰好合法、分页全量遍历不重不漏 |
| `ConcurrentOrderTest` | 5 | **L2-10 并发不超卖**（`@RepeatedTest` 5 轮） |
| `ConcurrentPaymentTest` | 5 | L2-9 幂等：同一订单 10 并发支付（5 轮） |
| `PurchaseFlowTest` | 4 | 全链路：发布 → 下单 → 支付 → 查询接口可见 |
| `ConcurrentOrderNoUniquenessTest` | 3 | 并发下单 10 个单号互不相同 |
| `ExceptionMappingRegressionTest` | 3 | 415/406 回归、时间格式毫秒精度 |
| `IndexExplainTest` | 2 | **L2-13 索引专项**：同一 SQL 加索引前后 EXPLAIN ANALYZE 与 scanCount 对比 |
| `OrderRollbackTest` | 2 | L1-6 事务回滚：扣库存后插订单失败，库存恢复 |
| `PaymentRollbackTest` | 1 | 支付记录插入失败，订单状态不被更新 |
| `MarketplaceApplicationTests` | 1 | 上下文启动、health 接口 |

题目 L2-12 要求的三个必测场景，分别落在：

- **正常下单库存减 1** → `OrderCreateTest.createSuccess`（库存 10 → 8）
- **库存不足返回 409** → `OrderCreateTest.insufficientStock`（断言 409 + 40901，且库存与订单数不变）
- **`quantity=0` 返回 400** → `OrderCreateTest.quantityZero`（另有 `-1`、`"2"`、`2.5`、`null`、缺字段等）

## L2-10 并发不超卖：如何准备数据、运行与核对

### 运行

```bash
cd backend
./mvnw test -Dtest=ConcurrentOrderTest
```

### 准备数据

**无需手工准备**。`ConcurrentOrderTest` 的 `@BeforeEach` 每轮执行：

1. 清空 `payments` / `orders` / `products` 三张表；
2. 插入一件**库存 = 1、状态 = on** 的商品。

### 并发是怎么发起的

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` 启动**真实 Tomcat**；
- 每个任务用 JDK `HttpClient` 发一次真实的 `POST /api/orders`，走完整的「Tomcat 线程 → Controller → `@Transactional` 代理 → `JdbcTemplate` → H2」链路；
- 两级 `CountDownLatch`（`ready` 全部就绪 → `startGate` 统一放行）保证 10 个请求尽量同时到达，而不是顺序发出；
- 不重试、不捕获业务异常（`Future.get()` 传播执行异常，任何任务意外失败都会让测试直接失败）；
- `@RepeatedTest(5)` 连跑 5 轮，每轮重建数据，验证结果可重复而非偶然。

### 怎么核对两项结果

测试内部已经断言了全部四项，评审也可以自己手动复核：

| # | 要核对的 | 测试内断言 | 手工复核方式 |
|---|---|---|---|
| 1 | 恰好 1 个请求成功 | `assertEquals(1, success)` —— 统计 10 个响应的 HTTP 状态 | 看 surefire 报告 5 个 testcase 全绿 |
| 2 | 其余 9 个返回 409 且业务码 40901 | `assertEquals(9, conflict)` —— 逐条解析响应体 `code` | 同上；失败时断言消息会打印全部 10 个响应 |
| 3 | 最终库存恰好为 0 | `assertEquals(0, stockOf(productId))` —— `SELECT stock FROM products WHERE id=?` | 连数据库或加日志查询该商品 |
| 4 | 本轮恰好新增 1 条订单 | `assertEquals(1, orderCountOf(productId))` —— `SELECT COUNT(*) FROM orders WHERE product_id=?` | `SELECT * FROM orders WHERE product_id=?` |

### 实际跑了多少轮

`@RepeatedTest(5)` —— **每次运行 5 轮**。本轮开发过程中累计运行十余次，每次均为 5 轮，结果每轮一致：**1 个 201、9 个 409/40901、库存 0、订单 1 条**。

### 为什么这不是「伪装并发」

- 用真实 HTTP + 真实 Tomcat 线程池，**不是** MockMvc（MockMvc 默认在调用线程内执行，会掩盖事务和连接池的真实行为）；
- `CountDownLatch` 两级同步，不是循环顺序发出；
- 断言的是**数据库最终状态**（库存 0、订单 1），不是只看响应码；
- 事务由被请求的 Tomcat 线程真实提交/回滚，测试类**没有**挂测试级 `@Transactional`（否则会让被测事务加入同一事务，永远看不到真实的提交/回滚边界）。

### 我另外做的人工复核（非测试代码）

除了测试，我还单独启动服务、用 10 个并发 `curl` 打同一个库存 1 的商品提交了一次，结果与测试一致：HTTP 状态分布恰好 1×201 + 9×409，最终库存 0。这一步不在测试套件里，只作为额外的独立确认。

## L2-11 最简鉴权：接口、业务码与复核方法

### 接口

```bash
# 1) 登录（账号写死：admin / market123）
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"market123"}'
# → {"code":0,"message":"ok","data":{"token":"<64 位十六进制>"}}

# 2) 带 token 发布商品（不带 → 401）
curl -s -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"title":"测试","price":100,"stock":1,"category":"文具","sellerName":"铺"}'
```

### 保护范围（严格按题目 §6 L2-11 原文）

| 接口 | 是否要 token |
|---|---|
| `POST /api/products`（发布） | 要 |
| `PATCH /api/products/{id}/status`（上下架） | 要 |
| 其余全部接口（商品查询、下单、支付、登录本身） | 不要 —— 题目只要求保护上面两个 |

保护逻辑在 `auth/AuthInterceptor`：注册到这两个路径后按 HTTP 方法过滤（`/api/products` 同时承载 GET 列表和 POST 发布，只有 POST/PATCH 校验）。

### 业务码（题目允许自定非 0，在此说明）

| code | HTTP | 场景 |
|---|---|---|
| `40101` | 401 | 缺少 `Authorization` 头 |
| `40102` | 401 | token 无效/已失效，或头不是 `Bearer <token>` 格式 |

失败响应仍是 `{code,message,data}` 统一结构（拦截器抛 `BusinessException`，走同一个 `GlobalExceptionHandler` 出口）。登录账密错误返回 400 / 40001「账号或密码不正确」，账号不存在与密码错误同码同文案（防用户名枚举）。

### 实现取舍

- token 是 32 字节 `SecureRandom` → 64 位十六进制，存服务端内存 `Set`；**服务重启即全部失效**——题目不要求 JWT、不要求过期、不要求角色，单机内存 H2 场景下这是预期行为
- scheme 大小写宽松（RFC 6750：`bearer xxx` 与 `Bearer xxx` 等价），token 本身严格区分
- 登录失败统一文案，不泄漏「账号是否存在」

### 复核

`./mvnw test -Dtest=AuthTest`——9 例覆盖：登录成功/两次登录 token 不同且都可用/密码错与账号不存在同文案/请求体校验/两个受保护接口的无头 401(40101)、伪造与格式错误 401(40102)、带有效 token 正常 201/200、公开接口不带 token 全部正常。

## L2-13 索引专项：查询、索引设计与前后对比证据

- **被测查询**：L1-8 的订单分页查询（`OrderRepository.searchByBuyerPhone` 的数据查询）：
  `SELECT ... FROM orders WHERE buyer_phone = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?`
  典型参数：`buyer_phone = '13800000000'`、`page = 1`、`pageSize = 10`。
- **索引**：`orders (buyer_phone, created_at DESC, id DESC)`（schema.sql 中的 `idx_orders_buyer_phone_created`）。选列理由与备选分析见 `DESIGN.md` 附录 A。
- **证据（可重跑）**：`./mvnw test -Dtest=IndexExplainTest`。测试自动构建 20 买家 × 每人 1000 单（共 2 万行，created_at 逐行递增），对同一 SQL 分别做「删索引 → EXPLAIN ANALYZE」与「重建索引 → EXPLAIN ANALYZE」，断言并打印两份执行计划与扫描行数：

| 场景 | 执行计划 | scanCount（物理扫描行数） |
|---|---|---|
| 无索引 | `PUBLIC.ORDERS.tableScan`（全表扫描） | **20001** |
| 有索引 | `IDX_ORDERS_BUYER_PHONE_CREATED: BUYER_PHONE = ?1`（索引范围扫描） | **1001**（该买家的 1000 行 + 1 次边界探测） |

  扫描量约 **20 倍**收敛。**如实说明**：H2 2.x 在本查询形态下未沿索引序在 LIMIT 10 处提前终止（若免排序成立，scanCount 应约 11），即本库收益来自「扫描范围收敛」而非「免排序」——这是该引擎的限制，不是索引设计问题；若换支持 index-order scan 提前终止的引擎（如 PostgreSQL），同一 DDL 可进一步受益。结论以 scanCount 本身为准，与 EXPLAIN 文本是否回显 ORDER BY 无关。
- 完整分析（查询、典型参数、选列理由、评审如何复核、H2 与 MySQL/PostgreSQL 的行为差异）见 `DESIGN.md` 附录 A。

## 没做完的地方

| 项 | 状态 | 原因 |
|---|---|---|
| 异步支付回调 / 轮询 | 没做 | 题目允许「直接同步返回 paid，不用真做异步回调」 |
| 独立并发 shell 脚本 | 没做 | 并发验证通过 `./mvnw test -Dtest=ConcurrentOrderTest` 执行，依赖 Maven |
| token 过期 / 登出 / 角色 | 没做 | 题目明确「不要求 JWT、不要求过期、不要求角色」 |
| 索引的免排序收益 | 引擎限制 | H2 不沿索引序提前终止 LIMIT（详见 L2-13 节）；扫描量收益 20 倍已实测 |

已知边界（不影响上述完成度，详细记录见 `DESIGN.md` 第 7 节）：

- 字符串字段收到数字/布尔 JSON 值时会被 Jackson 转成字符串并接受（如 `"buyerName": 123` → `"123"`），仅影响字符串字段；`quantity`/`price`/`stock` 等数值字段的严格类型校验是完整的。
- `Accept: application/xml` 返回 406 但响应体为空（Spring 内容协商阶段的固有限制）。

## 自检清单

- [x] 删掉依赖目录后，按上面的命令能重新跑起来（H2 内存库，无外部依赖；`mvn clean test` 本次实测 20.419 秒、242 例通过）
- [x] 没提交 `node_modules` / `target` / `*.db` / `.env`（`.gitignore` 已覆盖，`git ls-files backend/target` 为空）
- [x] `DESIGN.md`、`AI_USAGE.md` 已填写完整
- [x] 所有接口成功/失败均返回统一 `{code,message,data}` 结构（406 空体为已知例外，见下）
- [x] 受保护接口在**不带 token** 时对 L0/L1 验收的影响已说明：评审按 L0/L1 验收发布/上下架前，先按「L2-11 最简鉴权」一节登录取 token
