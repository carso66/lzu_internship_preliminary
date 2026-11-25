# 提交说明（L0＋L1 阶段）

## 基本信息

| 项 | 内容                     |
|---|------------------------|
| 姓名 | 郭子杰；GitHub 账号为 `gzzjj` |
| 学号 | `220250946261`         |
| 方向 | 后端 `[BE]`              |
| 分支 | `220250946261`         |
| 当前范围 | L0 商品管理、L1 订单；同时申报 L2-10 并发不超卖、L2-12 自动化测试 |
| 实际投入 | 未单独统计，待本人补充            |

本文件是阶段性提交说明。最终提交前继续完善设计和 AI 使用记录。

## 环境

| 项 | 内容 |
|---|---|
| 操作系统 | Windows；具体版本待本人补充 |
| Java | 编译目标 Java 17；运行需要 JDK 17 或以上，实际运行版本以 `java -version` 为准 |
| 框架 | Spring Boot `4.2.0` |
| 数据访问 | Spring JDBC / JdbcTemplate |
| 数据库 | H2 内存数据库 |
| 构建 | Maven；仓库提供 Maven Wrapper，分发版本 `3.10.1` |

首次构建需要能够访问 Maven 依赖仓库。使用 Maven Wrapper 无需另行安装 Maven。

## 启动方式

以下命令从仓库根目录执行。Windows PowerShell：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

如果已安装 Maven，也可在 `backend/` 中运行：

```text
mvn spring-boot:run
```

必须将 `backend/` 作为运行工作目录，因为初始化配置读取 `file:./schema.sql`。IDE 的运行配置也应使用这个工作目录。

- 服务地址：`http://localhost:8080`
- 健康检查：`GET /api/health`
- 健康检查预期：`{"code":0,"message":"ok","data":null}`
- 商品接口：`http://localhost:8080/api/products`

数据库连接为 `jdbc:h2:mem:market;DB_CLOSE_DELAY=-1`，启动时执行 `backend/schema.sql`。不需要外部数据库。应用进程退出后数据清空，新启动时商品和订单表为空，需要通过发布接口准备商品。

本地 H2 控制台：`http://localhost:8080/h2-console`。

```text
Driver Class: org.h2.Driver
JDBC URL: jdbc:h2:mem:market
User Name: sa
Password: 留空
```

## 完成情况

| 层级 | 当前情况 |
|---|---|
| L0 | 5/5 项已实现：建表、发布商品、列表筛选分页、详情、上下架 |
| L1 | 下单、事务扣库存、订单详情和手机号分页查询均已实现 |
| L2 | 申报 L2-10 并发不超卖、L2-12 自动化测试；支付、鉴权、EXPLAIN 对比未实现 |

## 已实现接口

| 方法 | 路径 | 行为 |
|---|---|---|
| GET | `/api/health` | 健康检查，200 |
| POST | `/api/products` | 发布商品，成功 201 |
| GET | `/api/products` | 列表、组合筛选和分页，200 |
| GET | `/api/products/{id}` | 商品详情，200；不存在 404 |
| PATCH | `/api/products/{id}/status` | `on` / `off` 上下架，200 |
| POST | `/api/orders` | 下单，成功 201；校验、扣库存与插单事务 |
| GET | `/api/orders/{orderNo}` | 按订单号查询，200；不存在 404 |
| GET | `/api/orders` | 按 buyerPhone 分页查询，200 |

### 发布商品

示例请求：

```json
{"title":"Notebook","price":1990,"stock":10,"category":"Stationery","description":"","sellerName":"Test seller","imageUrl":""}
```

- `title`、`category`、`sellerName` 必填，去除首尾空白后分别最多 100、50、50 个 Java 字符串长度单位。
- `price` 为非负整数分，使用 `Long/long`；`stock` 为非负整数，使用 `Integer/int`。
- `description`、`imageUrl` 可不传，分别最多 2000、500 个 Java 字符串长度单位，缺失或为 null 时保存空字符串。
- 商品 ID、初始状态 `on` 和时间由服务器/数据库生成。
- 整数字段拒绝字符串、小数和布尔值；文本字段拒绝数字和布尔值。

### 商品列表

- `page` 默认 1，必须至少为 1；`pageSize` 默认 10，范围 1～50。
- `keyword` 标题包含匹配；null 或纯空白时不筛选，非空白关键词保留原始首尾空白；`%`、`_`、`!` 按字面字符处理。
- `status` 仅允许 `on` / `off`；不传表示全部，显式空字符串属于非法状态。
- `category` 精确匹配；不传表示全部，显式空字符串按空字符串查询。
- 三个条件通过 AND 叠加；总数与当前页查询使用相同条件。
- 排序为 `created_at DESC, id DESC`；超出页数返回空数组，`total` 仍是筛选后总数。

示例：

```text
GET /api/products?keyword=Notebook&status=on&category=Stationery&page=1&pageSize=10
```

`data` 包含 `total`、`page`、`pageSize`、`items`。

### 商品上下架

请求 `{"status":"off"}` 或 `{"status":"on"}`。响应返回更新后的完整商品，包含题目要求的 `id`、`status`；价格和库存不因上下架操作改变。

### 下单与订单查询

下单示例（productId 替换为实际发布返回的 ID）：

```json
{"productId":1,"quantity":2,"buyerName":"Test buyer","buyerPhone":"TEST-PHONE-001","address":"Test address","remark":""}
```

- productId 为正整数，quantity 为至少 1 的整数；缺失、null、负数、零和字符串/小数类型不符合要求时返回 400/40001。
- buyerName、buyerPhone、address 必填，strip 后长度分别最多 50、20、500；remark 可选，strip 后最多 500。
- 手机号仅校验非空和长度，保存与查询使用相同 strip 规则；不额外要求中国大陆 11 位号码格式。
- 事务内 SELECT FOR UPDATE 锁定商品，依次检查商品存在、上架和库存足够，再以 Math.multiplyExact 计算整数分金额。溢出在扣库存前返回 400/40001。
- 库存使用带 stock >= quantity 条件的 UPDATE 扣减，与插订单处于同一事务；异常向外传播时回滚。
- 单价和总金额来自数据库和服务端计算；请求 DTO 不定义客户端价格字段。
- 订单保存商品标题、单价和总金额快照；订单号为 SO + yyyyMMdd + 32 位 UUID，长度 42，并有数据库唯一约束。
- 初始 status=created、paidAt=null。当前没有支付接口。
- 详情按 orderNo 查询。列表 buyerPhone 必填，page 默认 1、pageSize 默认 10，范围为 1～50；按 created_at DESC、id DESC 排序。
- total 是该手机号的订单数；超出页数返回空 items。

## 响应和错误码

```json
{"code":0,"message":"ok","data":null}
```

所有已实现接口使用 `code`、`message`、`data`。失败时 `data` 为 null；未预期错误返回通用提示，不向客户端返回堆栈。

| code | HTTP | 当前场景 |
|---|---|---|
| 0 | 200 / 201 | 成功 |
| 40001 | 400 | 参数校验、类型错误、非法 JSON |
| 40401 | 404 | 商品、订单或请求路径不存在 |
| 40901 | 409 | 库存不足 |
| 40902 | 409 | 商品已下架 |
| 40501 | 405 | 请求方法不支持 |
| 41501 | 415 | 请求内容类型不支持 |
| 50000 | 500 | 服务内部错误 |

重复支付的 40903 业务码已预留，但支付接口尚未实现，不作为当前完成项。

## 测试与复核

### Maven 自动化测试

在 backend 工作目录运行全部测试：

```powershell
.\mvnw.cmd test
```

已安装 Maven 时，等价命令为 `mvn test`。实际 Surefire TXT 报告已复制到 `test-evidence/`，不依赖被 Git 忽略的 target 目录：

- `test-evidence/com.example.demo.OrderIntegrationTest.txt`：5 项，0 失败、0 错误、0 跳过。
- `test-evidence/com.example.demo.DemoApplicationTests.txt`：1 项，0 失败、0 错误、0 跳过。
- 本次待提交 L1 版本在隔离副本实际运行 mvn -B -ntp clean test，合计 6 项通过；报告为本次实际生成文件。摘要见 test-evidence/maven-test-summary.txt。

OrderIntegrationTest 使用真实 HTTP、随机本地端口和独立 H2 数据库 `order_integration_test`，不会操作正在运行的 8080 服务的数据库。测试类和方法没有外层测试事务，服务事务由 Spring 管理的 OrderService.create 创建。

| 方法 | 验证内容 |
|---|---|
| normalOrderUsesDatabasePriceAndPreservesSnapshots | 正常下单 201、服务端算价、库存刚好扣至 0、订单号格式；商品改名改价后订单快照不变 |
| invalidOrdersDoNotChangeStockOrCreateOrders | quantity 为 null/0/-1/字符串/小数、库存不足、不存在、下架、金额溢出；最终库存与订单数量 |
| orderQueriesNormalizePhoneAndUseFilteredStablePagination | 手机号 strip、同手机号筛选后的 total、前后页、空页、非法分页和缺少手机号、订单不存在 |
| databaseInsertFailureRollsBackStockAndOrderCount | 测试库 CHECK 约束使真实 INSERT 失败，核对 SQLState 23513，库存恢复、订单数不变，finally 删除测试约束 |
| concurrentOrdersDoNotOversell | 10 个同时释放的 HTTP 请求购买 1 件库存，核对 1 成功、9 个 409/40901、最终库存 0、订单数 1 |

只运行订单测试：

```powershell
.\mvnw.cmd "-Dtest=OrderIntegrationTest" test
```

### L2-10 并发不超卖：复跑方式

```powershell
.\mvnw.cmd "-Dtest=OrderIntegrationTest#concurrentOrdersDoNotOversell" test
```

无需手动启动服务或准备数据。该测试在独立测试库中清理测试表，自动创建已上架、库存为 1 的商品；用 CountDownLatch 同时释放 10 个请求，每个购买 1 件。等待请求完成后，断言恰好 1 个 201、9 个 409/40901，随后查询数据库核对库存为 0、订单数为 1。测试失败会导致 Maven 非零退出。

### L2-12 自动化测试

申报依据为上述 5 项 JUnit 业务集成测试。其中明确覆盖正常下单扣库存、库存不足 409、quantity=0 返回 400，同时核对业务码和数据库最终状态。全部测试可在 backend 中通过一条 `mvn test` 运行。

### 手动接口验收

本次将待提交的 L1 快照启动到随机本地端口，实际运行 scripts/test-l1-validation.ps1，22 项全部通过；原始输出已归档到 test-evidence/test-l1-validation-output.txt。此版本还未加入 token 鉴权，脚本无需登录。

脚本已放入仓库。启动最新服务后，另开终端，在 backend 工作目录执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-l1-validation.ps1
```

脚本自动准备测试商品和虚构买家标识，检查下单、订单查询、错误请求不变更数据、金额溢出、分页及库存刚好足够，全部通过时显示 `RESULT: 22 passed, 0 failed.`。它会新增 3 件测试商品和 3 条成功订单，不删除原有数据。该脚本不替代 JUnit 中的真实 SQL 失败回滚和并发测试。

如需留存本次实际输出：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-l1-validation.ps1 *>&1 | Tee-Object -FilePath "C:\Temp\l1-http-test.log"
```

原有 `scripts/test-l0-validation.ps1` 仍可复跑 L0 字段提示等检查；不根据脚本存在预先宣称通过。

数据库人工复核：

```sql
SELECT id, title, stock, status FROM products ORDER BY id DESC;
SELECT order_no, product_id, quantity, unit_price, total_amount, status
FROM orders ORDER BY created_at DESC, id DESC;
```

## 未完成和已知限制

- 支付及 payments 表、鉴权未实现；它们为其他选做项，不影响已实现的 L1。
- 申报的 L2 项目为 L2-10、L2-12；没有进行索引前后 EXPLAIN 对比，不申报 L2-13。
- 内存数据库在应用进程退出后丢失数据，需要重新准备手动测试数据。
- 当前没有用户表，按题目使用姓名和手机号等简化字段，不提供身份或角色权限保证。
- 列表 COUNT 和 SELECT 是两次查询，没有承诺并发写入下共享同一查询快照。
- 并发测试验证了响应和最终数据，未单独测量行锁的阻塞时长。
- AI_USAGE 最终总结、平台模型和费用记录由本人随后补充；本次不提交其本地草稿更新。
- 实际投入时长尚未统计，不填推测数字。

提交范围限于 backend。保留原题目 README；target、IDE 配置和数据库数据文件不属于提交内容。test-evidence 仅保留明确选取的 TXT 测试报告，不包含 Surefire XML 环境属性。
