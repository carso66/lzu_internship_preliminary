# 提交说明（SUBMISSION）

**项目**：校园好物市集 · 后端服务

**技术栈**：Java 17 编译目标 / Spring Boot 4.2.0 / JdbcTemplate / H2

**交付目录**：`backend/`

## 基本信息

| 项 | 内容                     |
|---|------------------------|
| 姓名 | 郭子杰；GitHub 账号为 `gzzjj` |
| 学号 | `220250946261`         |
| 方向 | 后端 `[BE]`              |
| 分支 | `220250946261`         |
| 当前范围 | L0 商品管理、L1 订单；L2 仅申报 L2-11 最简鉴权、L2-12 自动化测试 |
| 实际投入 | 12 小时 |

本次完成商品管理、事务下单、订单查询和最简登录鉴权。L2 选择 **L2-11 最简鉴权、L2-12 自动化测试**。实现与取舍见 [DESIGN.md](DESIGN.md)，运行与验收方法如下。

## 交付总览

这份文件是本次后端提交的总入口：汇总实现范围、文件位置、启动方法、接口约定和验收证据。源码、SQL、脚本和说明以仓库中的独立文件交付，评审可从下表定位。

| 交付内容 | 位置 | 当前状态与用途 |
|---|---|---|
| Maven 工程 | [pom.xml](pom.xml)、[mvnw.cmd](mvnw.cmd)、[maven-wrapper.properties](.mvn/wrapper/maven-wrapper.properties) | 已提供依赖、Java 17 编译目标与 Maven Wrapper |
| 手工请求样例 | [product-test.json](product-test.json) | 虚构商品发布 JSON，供手动请求使用；验收脚本自行创建数据，不依赖该文件 |
| 数据库定义 | [schema.sql](schema.sql) | products、orders 两表，包含主键、唯一约束、外键、CHECK 和常规查询索引 |
| 应用配置 | [application.properties](src/main/resources/application.properties) | H2 内存库、SQL 初始化、H2 控制台 |
| Java 实现 | [src/main/java/com/example/demo](src/main/java/com/example/demo/) | 健康检查、商品、订单、登录、统一响应与异常处理 |
| 自动化测试 | [src/test/java/com/example/demo](src/test/java/com/example/demo/) | 订单 5 项、鉴权 5 项、应用启动 1 项 |
| HTTP 验收脚本 | [scripts](scripts/) | L0 8 项、L1 22 项、L2 鉴权 11 项，可重复准备测试数据 |
| 实际验证记录 | [test-evidence](test-evidence/) | 当前提交版本实际生成的报告与控制台输出 |
| 设计说明 | [DESIGN.md](DESIGN.md) | 对应题目模板的八项问题，解释拆分、数据和业务决策 |
| AI 使用说明与平台日志 | [AI_USAGE.md](AI_USAGE.md)、[ai-evidence](ai-evidence/README.md) | 已整理使用说明、脱敏对话、模型统计和索引；费用按本人填写的 8.82 元记录，声明由本人确认 |

### 项目结构

```text
backend/
├── pom.xml / mvnw / mvnw.cmd / .mvn/wrapper/
├── schema.sql
├── src/main/java/com/example/demo/
│   ├── DemoApplication.java / HealthController.java
│   ├── controller/   AuthController、ProductController、OrderController
│   ├── dto/          LoginRequest/Response、CreateProductRequest、
│   │                UpdateProductStatusRequest、CreateOrderRequest
│   ├── service/      AuthService、ProductService、OrderService
│   ├── repository/   ProductRepository、OrderRepository
│   ├── model/        Product、Order
│   ├── common/       ApiResponse、PageResult、ErrorCode、
│   │                BizException、GlobalExceptionHandler
│   └── config/       JacksonConfig、RequireToken、TokenInterceptor、WebConfig
├── src/main/resources/application.properties
├── src/test/java/com/example/demo/
│   ├── DemoApplicationTests.java
│   ├── OrderIntegrationTest.java
│   └── AuthIntegrationTest.java
├── scripts/test-l0-validation.ps1
├── scripts/test-l1-validation.ps1
├── scripts/test-l2-validation.ps1
├── test-evidence/
└── SUBMISSION.md / DESIGN.md / AI_USAGE.md
```

L2 按题目选择两项申报；订单的事务、库存保护和相关回归测试随 L1 一并保留。


## 环境

| 项 | 内容 |
|---|---|
| 操作系统 | Windows，25H2，系统 build 26200.9711 |
| Java | 编译目标 Java 17；运行需要 JDK 17 或以上，本次验证使用 JDK 21.0.8 |
| 框架 | Spring Boot `4.2.0` |
| 数据访问 | Spring JDBC / JdbcTemplate |
| 数据库 | H2 内存数据库，版本 2.5.252 |
| 构建 | Maven；仓库提供 Maven Wrapper，分发版本 `3.10.1` |

本工程沿用当前已运行验证的 Spring Boot 4.2.0 与 Jackson 3。题目中的 Spring Boot 3.x 是入门示例；接口和验收规则仍按题目实现，依赖及异常 API 按工程实际版本适配。

首次构建需要能够访问 Maven 依赖仓库。使用 Maven Wrapper 无需另行安装 Maven。仓库使用 only-script 类型的 Wrapper，mvnw、mvnw.cmd 和 .mvn/wrapper/maven-wrapper.properties 已跟踪，无需提交 maven-wrapper.jar。

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
| L2 | 仅申报 L2-11 最简鉴权、L2-12 自动化测试；不提交支付链路或索引实验 |

## 题目要求与实现对应

| 层级与要求 | 当前实现 | 验收入口 |
|---|---|---|
| L0：建表 | 两张业务表及约束，应用启动自动初始化 | schema.sql；启动后在 H2 控制台查表 |
| L0：发布商品 | 校验字段与类型，服务端生成 ID/状态/时间，返回 201 | ProductController/Service；L0 脚本发布成功与错误输入检查 |
| L0：筛选分页 | keyword/status/category 可组合，返回筛选后 total 与 items | ProductRepository.findPage；下文提供组合查询与数据核对方法 |
| L0：商品详情 | 正常 200，不存在 40401 | GET products/{id}；订单与鉴权测试也用详情核对库存 |
| L0：上下架 | on/off 校验，维护更新时间，库存和价格不因上下架改变 | 鉴权集成测试；L1/L2 HTTP 脚本 |
| L1：创建订单 | 参数校验、数据库价格、金额快照、事务扣库存，成功 201 | OrderIntegrationTest；L1 22 项脚本 |
| L1：订单详情 | 按订单号回读快照，不存在 40401 | 正常下单和查询集成用例 |
| L1：订单分页 | buyerPhone 必填、统一 strip、稳定排序、边界页 | 手机号分页集成用例与脚本 |
| L1：异常及一致性 | 参数错误不改数据；真实 INSERT 失败时库存恢复 | 参数、溢出与真实数据库回滚用例 |
| L2-11：最简鉴权 | 固定账号登录发随机 token，两个商品写接口拒绝无效 token | AuthIntegrationTest；L2 鉴权 11 项脚本 |
| L2-12：自动化测试 | 一条 mvn test 覆盖正常下单、库存不足、quantity=0，并补充其他回归 | 测试源码与 test-evidence 中的实际报告 |



## 已实现接口

| 方法 | 路径 | 行为 |
|---|---|---|
| GET | `/api/health` | 健康检查，200 |
| POST | `/api/auth/login` | 固定账号登录，200，返回随机 token |
| POST | `/api/products` | 需有效 Bearer token；发布成功 201 |
| GET | `/api/products` | 列表、组合筛选和分页，200 |
| GET | `/api/products/{id}` | 商品详情，200；不存在 404 |
| PATCH | `/api/products/{id}/status` | 需有效 Bearer token；`on` / `off` 上下架，200 |
| POST | `/api/orders` | 下单，成功 201；校验、扣库存与插单事务 |
| GET | `/api/orders/{orderNo}` | 按订单号查询，200；不存在 404 |
| GET | `/api/orders` | 按 buyerPhone 分页查询，200 |

### 最简鉴权（L2-11）

固定演示账号：`demo`，密码：`demo123`。`POST /api/auth/login` 请求：

```json
{"username":"demo","password":"demo123"}
```

成功响应 data 为 `{"token":"随机字符串"}`。发布商品、上下架请求携带 `Authorization: Bearer <token>`；缺少或无效 token 返回 401/40101 和统一 JSON。错误账号密码返回 40101，缺失或非法登录字段返回 40001。

PowerShell 获取 token：

```powershell
$login = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body '{"username":"demo","password":"demo123"}'
$token = $login.data.token
```

原 curl 商品写请求增加 `-H "Authorization: Bearer $token"`。查询和下单不要求 token。三份验收脚本自动登录。

token 使用 SecureRandom 生成 32 字节随机值，Base64URL 无填充编码，保存在进程内并发 Set；服务重启后失效。按题目不实现用户表、角色、过期、注销或 JWT。

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
| 40101 | 401 | 缺少或无效 token、账号密码错误 |
| 40401 | 404 | 商品、订单或请求路径不存在 |
| 40901 | 409 | 库存不足 |
| 40902 | 409 | 商品已下架 |
| 40501 | 405 | 请求方法不支持 |
| 41501 | 415 | 请求内容类型不支持 |
| 50000 | 500 | 服务内部错误 |

## 完整操作示例：从登录到下单查询

先在第一个终端按前述命令启动服务。以下代码在另一个 PowerShell 中执行；示例使用虚构买家标识，每次生成新值。HTTP 状态以接口表为准，Invoke-RestMethod 的变量保存 JSON 响应体。

### 1. 健康检查与登录

```powershell
$baseUrl = "http://localhost:8080"
Invoke-RestMethod -Method Get -Uri "$baseUrl/api/health"

$loginBody = @{ username="demo"; password="demo123" } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/login" -ContentType "application/json" -Body $loginBody
$headers = @{ Authorization = "Bearer " + $login.data.token }
```

健康检查应为 code=0；登录 data.token 应为非空字符串。后面的商品写请求复用 headers。

### 2. 发布一件商品并查询

```powershell
$productBody = @{
    title="NotebookDemo"
    price=1990
    stock=5
    category="Stationery"
    sellerName="Demo seller"
    description="Demo product"
    imageUrl=""
} | ConvertTo-Json -Compress

$product = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/products" -Headers $headers -ContentType "application/json" -Body $productBody
$productId = $product.data.id
Invoke-RestMethod -Method Get -Uri "$baseUrl/api/products/$productId"
Invoke-RestMethod -Method Get -Uri "$baseUrl/api/products?keyword=NotebookDemo&status=on&category=Stationery&page=1&pageSize=10"
```

发布成功应返回 code=0、生成的 ID、status=on、price=1990、stock=5。列表包含该商品；如果先前已运行同名示例，total 可能大于 1，不能固定假设数据库只含本条记录。

### 3. 下架后再上架

```powershell
Invoke-RestMethod -Method Patch -Uri "$baseUrl/api/products/$productId/status" -Headers $headers -ContentType "application/json" -Body '{"status":"off"}'
Invoke-RestMethod -Method Patch -Uri "$baseUrl/api/products/$productId/status" -Headers $headers -ContentType "application/json" -Body '{"status":"on"}'
```

两次成功均为 code=0，状态按请求改变，库存仍为 5，价格仍为 1990。下单前已恢复 on。

### 4. 下单、查询订单和最终库存

```powershell
$buyerPhone = "DEMO-" + [guid]::NewGuid().ToString("N").Substring(0, 8)
$orderBody = @{
    productId=$productId
    quantity=2
    buyerName="Demo buyer"
    buyerPhone=$buyerPhone
    address="Demo address"
    remark=""
} | ConvertTo-Json -Compress

$order = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/orders" -ContentType "application/json" -Body $orderBody
$orderNo = $order.data.orderNo
Invoke-RestMethod -Method Get -Uri "$baseUrl/api/orders/$orderNo"
Invoke-RestMethod -Method Get -Uri "$baseUrl/api/orders?buyerPhone=$buyerPhone&page=1&pageSize=10"
Invoke-RestMethod -Method Get -Uri "$baseUrl/api/products/$productId"
```

在本示例商品没有其他写入的前提下，预期 unitPrice=1990、quantity=2、totalAmount=3980、status=created、paidAt=null；商品余量为 3。手机号是本次新生成值，列表应能查到本单。

### 5. 查看原始 HTTP 状态与错误响应

已登录的当前 PowerShell 中，可用 curl.exe 直接查看原始状态行。例如发布时把整数 stock 错写成字符串：

```powershell
$token = $login.data.token
'{"title":"BadType","price":1990,"stock":"5","category":"Stationery","sellerName":"Demo seller"}' |
    curl.exe -i "$baseUrl/api/products" -H "Content-Type: application/json" -H "Authorization: Bearer $token" --data-binary "@-"
```

预期 HTTP 400、code=40001，message 指出 stock 字段类型错误。下面是其他应核对的分支；错误请求前后可查询商品库存和订单数量对照，测试时避免其他客户端同时写入。

| 条件 | HTTP / code | 应观察到的数据变化 |
|---|---|---|
| 商品写请求不带 token 或使用未知 token | 401 / 40101 | 不新增商品，不改变商品状态 |
| quantity=0、-1、null、字符串或小数 | 400 / 40001 | 库存不变，不新增订单 |
| 数量超过现有库存 | 409 / 40901 | 库存不变，不新增订单 |
| 已下架商品下单 | 409 / 40902 | 库存不变，不新增订单 |
| 正整数形式但不存在的商品 ID | 404 / 40401 | 不新增订单 |
| 不存在的订单号 | 404 / 40401 | 查询不修改数据 |
| page=0 或 pageSize=51 | 400 / 40001 | 查询不修改数据 |
| 价格和数量的乘积溢出 long | 400 / 40001 | 在扣库存前失败 |

以上为可执行操作示例；本次实际测试报告和控制台输出统一收录于 test-evidence。


## 测试与复核

### Maven 自动化测试

本次验证在本人本机完成，导出和核验结果；指定平台模型负责静态审查。

在 backend 工作目录运行全部测试：

```powershell
.\mvnw.cmd test
```

已安装 Maven 时，等价命令为 `mvn test`。实际 Surefire TXT 报告已复制到 `test-evidence/`，不依赖被 Git 忽略的 target 目录：

- `test-evidence/com.example.demo.OrderIntegrationTest.txt`：5 项，0 失败、0 错误、0 跳过。
- `test-evidence/com.example.demo.DemoApplicationTests.txt`：1 项，0 失败、0 错误、0 跳过。
- `test-evidence/com.example.demo.AuthIntegrationTest.txt`：5 项，0 失败、0 错误、0 跳过。

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

### 鉴权测试（L2-11）

```powershell
.\mvnw.cmd "-Dtest=AuthIntegrationTest" test
```

5 项验证：两次登录 token 不同且均有效；错误账号密码与字段类型；缺少/非法 token 对两处写接口返回统一 401 且数据不变；合法 token 下发布/上下架及原参数校验；查询和下单仍可匿名。使用独立 auth_integration_test 内存库、真实 HTTP 和随机端口。

### 鉴权测试的逐项内容

| 方法 | 请求场景 | 核对内容 |
|---|---|---|
| loginReturnsDifferentRandomTokensAndBothRemainValid | 两次正确账号登录 | token 格式、两次不同、两者均可发布商品 |
| invalidLoginNeverReturnsToken | 错账号/密码、缺失字段、数字/布尔等类型错误 | 对应 40101/40001，失败响应 data=null |
| missingOrInvalidTokenBlocksBothWritesWithoutChangingData | 两处商品写接口使用缺失/错误 header | 统一 40101；商品总数、状态和库存保持原样 |
| validTokenAllowsStatusChangesAndPreservesParameterValidation | 合法 token 发布/上下架，附带非法业务参数 | 合法请求成功；业务错误仍按 400/404 处理 |
| readsAndOrdersRemainAccessibleWithoutToken | 匿名健康检查、商品查询、订单创建与查询 | 不错误扩大鉴权范围，正常下单仍扣库存 |

鉴权共 5 个测试方法，部分方法循环验证多组输入。JUnit 方法总数与 HTTP 脚本检查数分别统计。


### 订单库存并发回归

```powershell
.\mvnw.cmd "-Dtest=OrderIntegrationTest#concurrentOrdersDoNotOversell" test
```

无需手动启动服务或准备数据。该测试在独立测试库中清理测试表，自动创建已上架、库存为 1 的商品；用 CountDownLatch 同时释放 10 个请求，每个购买 1 件。等待请求完成后，断言恰好 1 个 201、9 个 409/40901，随后查询数据库核对库存为 0、订单数为 1。测试失败会导致 Maven 非零退出。

### L2-12 自动化测试

申报依据为上述 5 项 JUnit 业务集成测试。其中明确覆盖正常下单扣库存、库存不足 409、quantity=0 返回 400，同时核对业务码和数据库最终状态。全部测试可在 backend 中通过一条 `mvn test` 运行。

### 实际测试摘要

以下摘自本次归档的 Maven 输出，完整内容见 test-evidence/maven-output.txt：

```text
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| 测试类 | 方法数 | 核心覆盖 |
|---|---:|---|
| OrderIntegrationTest | 5 | 正常下单、服务端算价与快照、异常输入、订单分页、真实 SQL 失败回滚和库存并发回归 |
| AuthIntegrationTest | 5 | 随机 token、错误登录、两处写接口保护、拒绝后数据不变、合法业务与匿名查询/下单 |
| DemoApplicationTests | 1 | 应用上下文启动 |
| 合计 | 11 | 0 失败、0 错误、0 跳过 |

L2-12 的三个必测场景与源码对应：

| 题目要求 | 测试位置 | 核对内容 |
|---|---|---|
| 正常下单扣库存 | normalOrderUsesDatabasePriceAndPreservesSnapshots | 按请求数量扣减库存、金额来自数据库、订单快照保留 |
| 库存不足返回 409 | invalidOrdersDoNotChangeStockOrCreateOrders | HTTP 409/40901，库存与订单数不变 |
| quantity=0 返回 400 | 同上方法中的零数量输入 | HTTP 400/40001，未产生数据写入 |

### 手动接口验收

本次在本人本机，将只含两项 L2 的提交版本启动到随机本地端口 http://localhost:50812，并执行三份脚本：L0 8 项、L1 22 项、L2 鉴权 11 项全部通过，共 41 项检查。原始输出与退出码记录归档于 test-evidence。脚本与 JUnit 数量分开统计。

默认部署端口和三份脚本的 BaseUrl 都是 8080。本次为避免占用现有服务，启动时显式传 --server.port=0 获取随机端口，再用 -BaseUrl 传给脚本，不要求更改默认配置。

脚本已放入仓库。启动最新服务后，另开终端，在 backend 工作目录执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-l1-validation.ps1
```

脚本自动准备测试商品和虚构买家标识，检查下单、订单查询、错误请求不变更数据、金额溢出、分页及库存刚好足够，全部通过时显示 `RESULT: 22 passed, 0 failed.`。它会新增 3 件测试商品和 3 条成功订单，不删除原有数据。该脚本不替代 JUnit 中的真实 SQL 失败回滚和并发测试。

如需留存本次实际输出：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-l1-validation.ps1 *>&1 | Tee-Object -FilePath "C:\Temp\l1-http-test.log"
```

其余脚本：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-l0-validation.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-l2-validation.ps1
```

使用其他端口时，在命令后增加 `-BaseUrl http://localhost:实际端口`。
L0 脚本新增 1 件商品；L1 新增 3 件商品、3 条订单；L2 鉴权脚本新增 1 件商品、1 条订单。均不删除原有数据。L2 脚本验证登录、无效 token、有效 token 发布/上下架、匿名查询/下单等 11 项。

数据库人工复核：

```sql
SELECT id, title, stock, status FROM products ORDER BY id DESC;
SELECT order_no, product_id, quantity, unit_price, total_amount, status
FROM orders ORDER BY created_at DESC, id DESC;
```

## 验证文件索引与数据库复核

| 文件 | 内容 |
|---|---|
| [maven-test-summary.txt](test-evidence/maven-test-summary.txt) | 各测试类统计、11 项总数、BUILD SUCCESS、实际完成时间 |
| [maven-output.txt](test-evidence/maven-output.txt) | 本轮 Maven 的原始控制台输出 |
| [OrderIntegrationTest 报告](test-evidence/com.example.demo.OrderIntegrationTest.txt) | 5 项订单集成测试统计 |
| [AuthIntegrationTest 报告](test-evidence/com.example.demo.AuthIntegrationTest.txt) | 5 项鉴权集成测试统计 |
| [DemoApplicationTests 报告](test-evidence/com.example.demo.DemoApplicationTests.txt) | 1 项应用启动测试统计 |
| [http-summary.txt](test-evidence/http-summary.txt) | 三份脚本的目标地址、退出码与最终统计 |
| [L0 输出](test-evidence/test-l0-validation-output.txt) | 8 项 HTTP 检查 |
| [L1 输出](test-evidence/test-l1-validation-output.txt) | 22 项 HTTP 检查 |
| [L2 输出](test-evidence/test-l2-validation-output.txt) | 11 项鉴权 HTTP 检查 |
| [归档说明](test-evidence/README.txt) | 运行来源、版本与统计口径 |

H2 控制台可执行以下查询复核当前数据。内存库属于当前应用进程，独立 JUnit 测试库不会在主服务控制台中出现。

```sql
SELECT COUNT(*) AS product_count FROM products;
SELECT id, title, price, stock, status FROM products ORDER BY id DESC;

SELECT order_no, product_id, product_title, quantity,
       unit_price, total_amount, buyer_phone, status, paid_at
FROM orders ORDER BY created_at DESC, id DESC;

SELECT buyer_phone, COUNT(*) AS order_count
FROM orders GROUP BY buyer_phone;

SELECT order_no FROM orders
WHERE total_amount <> unit_price * CAST(quantity AS BIGINT);
```

最后一条金额一致性检查预期为空；它不能替代“失败请求未扣库存”等事务测试。临时测试 CHECK 约束只存在于对应隔离测试库，用例结束时删除，不应手动加到主服务库。


## 提交范围与运行说明

| 项目 | 说明 |
|---|---|
| 本次选做 | 仅申报 L2-11 最简鉴权和 L2-12 自动化测试；支付、索引实验不纳入本次范围 |
| 内存数据 | H2 数据和 token 都在当前进程内，应用重启后需重新准备商品并登录 |
| 接口保护范围 | 发布商品、商品上下架需要有效 token；查询和下单按题目保持公开 |
| 分页查询 | COUNT 与分页 SELECT 共用筛选条件，分别执行；不提供跨请求快照读 |
| AI 材料 | AI_USAGE.md 与 ai-evidence 已整理；费用按本人填写的 8.82 元记录，声明由本人确认 |

提交文件位于 backend，保留原题目 README。源码、SQL、Maven 配置及 Wrapper、测试源码、HTTP 脚本、实际报告和提交文档一并交付。target、IDE 配置、数据库数据文件和 .env 不纳入仓库。
