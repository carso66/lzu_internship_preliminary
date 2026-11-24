# 提交说明（SUBMISSION）· 校园好物市集后端

> 本文描述当前分支代码（至 commit `de99952`）的真实状态。复现命令按示例标注的终端执行。

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 唐荣君 |
| 学号 | 320240944501 |
| 方向 | 后端 |
| 分支 | `320240944501` |
| 实际投入 | 约 9 小时 |
| 开发提交 | 6 次有效 commit 并已 push，最新业务及测试代码提交 `de99952`（文档提交不计入） |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Windows 11（实测环境）。Linux / macOS **未实际运行验证**，命令理论上相同（Maven 跨平台），但未确认 |
| 运行时 | JDK 17（需 `java -version` ≥ 17） |
| 包管理器 | Maven 3.9.16（需 `mvn -v` 可用） |
| 框架 | Spring Boot 3.5.4（Spring Web + JdbcTemplate + Bean Validation） |
| 数据库 | H2 内存数据库（Maven 依赖自带，**无需单独安装**） |

## 启动方式（必填，要能照抄）

两种启动方式都从仓库根目录进入 `backend` 再执行。Spring Boot 的 `file:./schema.sql` 按当前工作目录加载建表脚本。

**方式一 · Maven 启动**

```text
cd backend
mvn spring-boot:run
```

**方式二 · 打包后启动**

```text
cd backend
mvn clean package -DskipTests
java -jar target/marketplace-0.0.1-SNAPSHOT.jar
```

默认端口为 `8080`。若该端口已被占用，在 `backend` 目录执行所选方式的备用端口命令。JAR 方式需先按上面步骤打包。

```text
mvn "-Dspring-boot.run.arguments=--server.port=18080" spring-boot:run
java -jar target/marketplace-0.0.1-SNAPSHOT.jar --server.port=18081
```

另开终端检查服务，使用实际启动端口替换下列地址中的 `8080`。成功响应为 `{"code":0,"message":"ok","data":null}`。

```text
curl.exe --noproxy 127.0.0.1 -i "http://127.0.0.1:8080/api/health"
```

H2 为内存库，每次启动由 `schema.sql` 初始化 `products` 和 `orders` 两张空表。重启后数据清空。

## 已实现接口一览（7 个业务接口 + 健康检查）

| # | 接口 | 成功 HTTP | 说明 |
|---|---|---|---|
| 0 | `GET /api/health` | 200 | 健康检查 |
| 1 | `POST /api/products` | 201 | L0-2 发布商品 |
| 2 | `GET /api/products` | 200 | L0-3 列表：keyword/status/category 可叠加筛选 + 分页 |
| 3 | `GET /api/products/{id}` | 200 | L0-4 详情 |
| 4 | `PATCH /api/products/{id}/status` | 200 | L0-5 上下架 |
| 5 | `POST /api/orders` | 201 | L1-6 下单（事务内扣库存 + 服务端算价） |
| 6 | `GET /api/orders/{orderNo}` | 200 | L1-7 按订单号查详情 |
| 7 | `GET /api/orders?buyerPhone=…&page=…&pageSize=…` | 200 | L1-8 按手机号查订单列表（创建时间倒序） |

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | 做了 **5 / 5** 条（建表、发布、列表、详情、上下架），全部完成 |
| L1 | 做了 **4 / 4** 条（下单 7 分 / 详情 / 列表 / 参数校验），全部完成 |
| L2 | 做了第 **10** 项（并发不超卖）和第 **12** 项（自动化测试） |

- **L0-1**：手写 `schema.sql`（`IF NOT EXISTS` 幂等），两表 + CHECK 约束 + 外键 + 一条索引；金额全程「分」整数，无浮点。
- **L1-6 关键点**：`quantity` 严格整数校验（`"2"`/2.5/0/-1 均 400）；扣库存为条件 UPDATE
  （`SET stock = stock - ? WHERE id=? AND status='on' AND stock >= ?`）；扣库存与插订单同一事务；
  价格由服务端按库中商品价计算（`Math.multiplyExact` 防溢出），请求体不接受客户端传价。
- **L2-10**：`OrderConcurrencyTest`，见下文「并发测试复现」。
- **L2-12**：要求 3~5 条，实际 **95 项** JUnit 测试（分层见 DESIGN.md 第 9 节），含真实 H2 库集成测试 5 项。

## 接口请求示例（虚构测试数据，字段与真实代码一致）

成功响应包含 `code=0`、`message="ok"` 和 `data`，失败响应的 `code` 为非零值，`data` 为 `null`。金额单位是分（整数）。

以下各接口的 `curl` 代码块按 **Git Bash** 的语法编写，请在同一个会话按顺序执行。使用备用端口时，先调整 `base_url`。PowerShell 和 CMD 的单行请求见本节末尾。响应示例中的 ID、订单号和时间为示意值，请使用实际响应中的值。

提交前冷启动验收中，商品发布或下单的即时响应与随后查询的 `createdAt` 偶有亚微秒差异。即时响应使用应用生成的时间，H2 `TIMESTAMP` 回读为微秒精度。这是时间字段的存储精度差异。

### 发布商品 `POST /api/products`

```bash
base_url="http://127.0.0.1:8080"
curl --noproxy 127.0.0.1 -i -X POST "$base_url/api/products" \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Keyboard","price":19900,"stock":5,"category":"digital","description":"blue switch 87 keys","sellerName":"Campus Shop"}'
```

```
HTTP/1.1 201 Created
{"code":0,"message":"ok","data":{"id":1,"title":"Test Keyboard","price":19900,"stock":5,
 "category":"digital","description":"blue switch 87 keys","status":"on","sellerName":"Campus Shop",
 "createdAt":"2026-11-24T15:30:12"}}
```

将响应中的 `data.id` 输入下列命令的提示，供后续请求使用。

```bash
read -r -p "Product ID from data.id: " product_id
```

字段约束：`title` 必填 ≤100；`price` 必填 ≥0 的整数（Long）；`stock` 必填 ≥0 的整数；
`category` 必填 ≤50；`sellerName` 必填 ≤50；`description` 可选 ≤500。缺字段/类型错/越界 → 400 + `40001`。

### 商品列表 `GET /api/products`

```bash
curl --noproxy 127.0.0.1 -i "$base_url/api/products?keyword=Keyboard&status=on&category=digital&page=1&pageSize=10"
```

```
HTTP/1.1 200 OK
{"code":0,"message":"ok","data":{"total":1,"page":1,"pageSize":10,
 "items":[{"id":1,"title":"Test Keyboard","price":19900,"stock":5,"category":"digital",
   "description":"blue switch 87 keys","status":"on","sellerName":"Campus Shop","createdAt":"2026-11-24T15:30:12"}]}}
```

`keyword` 标题模糊匹配、`status` 仅 `on`/`off`、`category` 精确匹配，三者可叠加；
`page` 默认 1，`pageSize` 默认 10、范围 1~50。`total` 为筛选后总条数。

### 商品详情 `GET /api/products/{id}`

```bash
curl --noproxy 127.0.0.1 -i "$base_url/api/products/$product_id"
# 200：{"code":0,"message":"ok","data":{ …同上单个商品对象… }}
curl --noproxy 127.0.0.1 -i "$base_url/api/products/999999"
# 404：{"code":40401,"message":"商品不存在","data":null}
```

### 上下架 `PATCH /api/products/{id}/status`

```bash
curl --noproxy 127.0.0.1 -i -X PATCH "$base_url/api/products/$product_id/status" \
  -H "Content-Type: application/json" -d '{"status":"off"}'
```

```
HTTP/1.1 200 OK
{"code":0,"message":"ok","data":{"id":1,"status":"off"}}
```

`status` 非 `on`/`off`（含缺失）→ 400 + `40001`。

> 完成下架后，先用下面的命令**重新上架**再下单。向下架商品下单会返回 HTTP 409，业务码 `40902`。

```bash
curl --noproxy 127.0.0.1 -i -X PATCH "$base_url/api/products/$product_id/status" \
  -H "Content-Type: application/json" -d '{"status":"on"}'
```

### 下单 `POST /api/orders`

```bash
curl --noproxy 127.0.0.1 -i -X POST "$base_url/api/orders" \
  -H "Content-Type: application/json" \
  -d '{"productId":'"$product_id"',"quantity":2,"buyerName":"Alice Test","buyerPhone":"00000000001","address":"No.1 Example Road, Lanzhou"}'
```

```
HTTP/1.1 201 Created
{"code":0,"message":"ok","data":{"id":1,"orderNo":"SO20261124000001","productId":1,
 "productTitle":"Test Keyboard","quantity":2,"unitPrice":19900,"totalAmount":39800,"status":"created",
 "buyerName":"Alice Test","buyerPhone":"00000000001","address":"No.1 Example Road, Lanzhou",
 "createdAt":"2026-11-24T16:02:45"}}
```

记录响应的 `data.orderNo`，查询时使用该实际值。订单号由日期和数据库自增序号组成。同日重启空 H2 库后，序号从头开始，可能再次生成相同的订单号。

失败示例（服务端 `message` 文案为中文，属实际行为；GBK 终端下若显示乱码仅为显示问题）：

```
404 商品不存在        {"code":40401,"message":"商品不存在","data":null}
409 商品已下架        {"code":40902,"message":"商品已下架","data":null}
409 库存不足          {"code":40901,"message":"库存不足","data":null}
400 quantity=0        {"code":40001,"message":"请求参数校验失败: quantity: 购买数量必须为不小于1的整数","data":null}
```

请求体**没有**价格字段——价格由服务端按数据库商品价格计算，客户端传价无处可传。
`quantity` 为 `"2"`（字符串）或 `2.5`（小数）同样返回 400 + `40001`。

### 订单详情 `GET /api/orders/{orderNo}`

先在提示中输入上一步返回的 `data.orderNo`。

```bash
read -r -p "Order number from data.orderNo: " order_no
```

```bash
curl --noproxy 127.0.0.1 -i "$base_url/api/orders/$order_no"
# 200：{"code":0,"message":"ok","data":{ …同上订单对象… }}
curl --noproxy 127.0.0.1 -i "$base_url/api/orders/SO00000000000000"
# 404：{"code":40401,"message":"订单不存在","data":null}
```

### 订单列表 `GET /api/orders`

```bash
curl --noproxy 127.0.0.1 -i "$base_url/api/orders?buyerPhone=00000000001&page=1&pageSize=10"
# 200：{"code":0,"message":"ok","data":{"total":1,"page":1,"pageSize":10,"items":[ …订单对象数组，按创建时间倒序… ]}}
```

`buyerPhone` 必填（缺失 → 400 + `40001`）；分页参数与商品列表一致。

### Windows PowerShell / CMD 单行请求

提交前冷启动验收中，PowerShell 和 CMD 均以 `curl.exe` 读取 JSON 文件的方式发布商品，返回 HTTP 201。下面使用 JAR 的备用端口 `18081`。若使用其它端口，请相应修改 URL。

将以下内容保存为当前终端工作目录下的 UTF-8 文件 `body.json`。

```json
{"title":"Test Keyboard","price":19900,"stock":5,"category":"digital","description":"blue switch 87 keys","sellerName":"Campus Shop"}
```

```text
curl.exe --noproxy 127.0.0.1 -i -X POST "http://127.0.0.1:18081/api/products" -H "Content-Type: application/json" --data-binary "@body.json"
```

使用发布响应中的 `data.id` 替换下方 `PRODUCT_ID`。下架时将 `body.json` 改为 `{"status":"off"}` 并执行一次，重新上架时改为 `{"status":"on"}` 再执行一次。

```text
curl.exe --noproxy 127.0.0.1 -i -X PATCH "http://127.0.0.1:18081/api/products/PRODUCT_ID/status" -H "Content-Type: application/json" --data-binary "@body.json"
```

下单时将 `body.json` 改为下列 JSON，并将 `productId` 改为实际商品 ID。

```json
{"productId":1,"quantity":2,"buyerName":"Alice Test","buyerPhone":"00000000001","address":"No.1 Example Road, Lanzhou"}
```

```text
curl.exe --noproxy 127.0.0.1 -i -X POST "http://127.0.0.1:18081/api/orders" -H "Content-Type: application/json" --data-binary "@body.json"
```

GET 请求沿用各接口的路径，使用 `curl.exe` 并给完整 URL 加双引号。查询订单详情时，将 `ORDER_NO` 替换为实际响应的 `data.orderNo`。

```text
curl.exe --noproxy 127.0.0.1 -i "http://127.0.0.1:18081/api/orders/ORDER_NO"
curl.exe --noproxy 127.0.0.1 -i "http://127.0.0.1:18081/api/orders?buyerPhone=00000000001&page=1&pageSize=10"
```

在 GBK 终端中，中文响应文案可能显示乱码，仍可核对 HTTP 状态和业务码。

## 错误码说明（含自定义码）

README 码表全部按约定实现：`0`（200/201）、`40001`（400）、`40401`（404）、
`40901`（409）、`40902`（409）、`40903`（409，仅为支付预留，当前无接口产生）、`50000`（500，兜底，堆栈只进日志不下发）。

**两个自定义业务码**（不在 README 码表内，按 HTTP 标准语义补位，未复用已有码含义）：

| code | HTTP | 场景 | message 示例 |
|---|---|---|---|
| `40501` | 405 | 请求方法不被支持（如对 `/api/products` 用 PUT） | `请求方法不被支持，该资源支持: POST, GET` |
| `41501` | 415 | Content-Type 不是 JSON | `请求 Content-Type 不被支持` |

**未知 JSON 字段的当前行为**：请求体中多余的未知字段**默认被忽略**，不报错、不落库
（Spring Boot 对 Jackson 的默认配置，本项目未开启 `FAIL_ON_UNKNOWN_PROPERTIES`）。
即向 `POST /api/orders` 附带 `{"price":1}` 不会生效也不会报错——价格无处被读取，服务端始终按库内价格算价。

## 测试 / 自检

在 **`backend` 目录**下用 Java 17 和 Maven 运行，H2 由 Maven 依赖提供。

**Git Bash / CMD**

```text
mvn clean test
mvn -Dtest=OrderConcurrencyTest test
mvn -Dtest=OrderIntegrationTest,OrderRollbackIntegrationTest test
```

**PowerShell** 中组合测试类时，给整个 `-Dtest=…` 参数加引号。前两条命令可直接使用。

```powershell
mvn '-Dtest=OrderIntegrationTest,OrderRollbackIntegrationTest' test
```

此前本人手动验证的结果如下。

- 全量 `mvn clean test` 共 **95 项全部通过，0 失败 / 0 错误 / 0 跳过**，构建耗时 13.102 秒。
- 并发测试共运行 **2 次**（1 次单独运行、1 次随全量测试），每次 3 轮，合计 **6 轮 / 60 个真实并发 HTTP 请求**。每轮恰好 1 个 201 成功、9 个 409/40901 库存不足、最终库存 0、恰好新增 1 条订单，超时、500 和其它错误均为 0。

### 提交前独立冷启动验收

2026 年 11 月 24 日，Codex 在从 HEAD `de99952` 创建的独立项目副本中实际执行验收。`-Dmaven.repo.local` 指向起始为空的临时目录，从 Maven Central 下载依赖后重新编译、运行全量测试并打包。原 Maven 缓存和用户级配置保持原样。

| 实际执行 | 结果 | Maven 耗时 |
|---|---|---:|
| `mvn clean test` | **95 项测试通过，0 失败 / 0 错误 / 0 跳过** | **7 分 01 秒** |
| `mvn clean package -DskipTests` | 打包成功，生成可执行 JAR | **5 分钟** |

验收时默认端口 `8080` 已被占用，`mvn spring-boot:run` 使用 `18080`，`java -jar` 使用 `18081`。两种方式均通过真实 HTTP 验证，依次完成健康检查、发布商品、商品列表和详情、下架、重新上架、下单、库存复查以及订单详情和列表。两轮均从实际响应取得商品 ID 和订单号，下单 2 件后库存从 5 减至 3，总额为 39,800 分。验收结束后，本轮启动的服务均已停止。

### 并发测试复现说明（L2-10，无需修改任何源码）

运行 `mvn -Dtest=OrderConcurrencyTest test`，测试自动完成以下全部步骤（`OrderConcurrencyTest` + 工具类 `TestDb`）：

1. **数据准备**：`@BeforeEach` 在整个测试方法开始前用 `JdbcTemplate` 清空 `orders`/`products` 两表
   （**只清一次**，不是每轮清库）；随后循环 3 轮，每轮向真实 H2 直插一个**独立的库存=1、状态=on**
   的商品并拿到其自增 `id`，该轮所有库存/订单核对均以这个商品 ID 统计，轮与轮之间数据互不复用。
2. **同时发送**：`@SpringBootTest(RANDOM_PORT)` 已启动真实 Tomcat。测试用 10 线程池 +
   `CountDownLatch` 双闸门——10 个线程先各自就绪，再同一瞬间释放，经 `TestRestTemplate`
   向 `POST /api/orders` 发出 **10 个真实 HTTP 请求**，每个 `quantity=1`。
3. **响应统计**：逐一审视 10 个响应，**严格三分类**：`201 且 code=0` 计「成功」、
   `409 且 code=40901` 计「库存不足」、其余（超时、500、任何其它状态/业务码）一律记「其它」，
   绝不允许混入「库存不足」。
4. **核对数据库最终状态**（直接 SQL 断言，不经过接口）：
   `SELECT stock FROM products WHERE id=?` 必须为 **0**；
   `SELECT COUNT(*) FROM orders WHERE product_id=?` 必须为 **1**。
5. **每轮断言**：恰好 `1 成功 + 9 库存不足 + 0 其它` + 上述两条库存/订单核对，3 轮全部通过测试才算过。

### 事务回滚测试说明（覆盖范围如实说明）

`OrderRollbackIntegrationTest` 用 `@MockitoSpyBean` **只把「插入订单」这一步桩成抛异常**，
商品 Repository 保持真实：扣库存由未 mock 的 `JdbcProductRepository` 对真实 H2 执行，
异常沿生产代码的 `@Transactional` AOP 代理上抛。断言三件事：响应为 500 + `50000`、
数据库中库存**恢复为下单前的值**（3）、`orders` 表零残留，并以 `verify(insert)` 证明流程确实走过了扣减。
即回滚由生产事务管理器完成，不是测试事务代劳。**边界**：故障只注入「订单插入」这一个点，
未覆盖订单号回填 UPDATE 失败等其它写失败路径——验证的是「回滚机制真实有效」，不是「所有故障已穷举」。

## 没做完的地方

| 项 | 状态 | 原因 |
|---|---|---|
| L2-9 支付链路 | 未做 | 时间优先给了并发验证与集成测试；已预留 `paid_at` 列和 `40903` 错误码 |
| L2-11 鉴权 | 未做 | L2 任选 1~2 个，已选择并发与自动化测试两项，覆盖 L2 的 10 分评分上限，实际得分以评审为准 |
| L2-13 索引 EXPLAIN 前后对比 | 未做 | 索引 `idx_orders_buyer_phone` 已建，但未做执行计划取证，**不声称其带来性能收益** |
| 数据持久化 | 设计如此 | H2 内存库，重启即清空（换取评审零依赖一条命令起服务） |
| MySQL 迁移 | 未验证 | SQL 只用了标准语法，但未在任何其它数据库实测；迁移还需增加 MySQL 驱动依赖、改数据源配置，并验证 DDL（IDENTITY 自增语法）、分页与各 SQL 的方言兼容 |
| `buyerPhone` 格式校验 | 已知问题 | 只校验必填和长度 ≤20，未校验手机号格式（README 未要求） |

**验证局限性（不夸大）**：此前本人手动验证了「单机、单 H2 实例、10 并发、库存=1」下的 6 轮并发；
多应用实例共享同库的场景机制上仍成立（行锁在库侧）但未实测；跨库/分库场景不适用；
其它数据库的行锁语义未实测。测试通过 ≠ 所有场景绝对安全。

## 自检清单

- [x] 已验证空缓存冷启动。提交前独立冷启动验收在基于 HEAD 的副本中使用起始为空的临时 Maven 依赖目录，成功下载依赖、重新编译、通过 95 项测试并完成打包。
- [x] 没提交 `node_modules` / `target` / `*.db` / `.env`（已用 `git ls-files` 核实：仓库内无构建产物；根 `.gitignore` 已覆盖 `target/`、`*.db`、`.env`）
- [ ] `DESIGN.md`、`AI_USAGE.md` 已成稿。企业平台最终费用已核对并记录，`AI_USAGE.md` 第 6 节声明待本人确认后勾选。
- [ ] 截图放进了 `screenshots/`（前端）——**不适用**（后端方向）

## Git 提交记录（6 次有效开发提交，均已 push）

```
461234d feat: initialize Spring Boot backend and health endpoint
29a4ff4 feat: implement unified error handling and strict request validation
6abcfd8 feat: implement product APIs with filtering and pagination
cd636cc feat: implement transactional order APIs and atomic stock deduction
92f0187 feat: initialize database schema and H2 configuration
de99952 test: verify order integration, transaction rollback and concurrent stock safety
```
