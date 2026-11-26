# 设计说明（DESIGN）

## 1. 我做到了哪一步

- [x] L0：健康检查、商品发布、列表筛选与分页、商品详情、商品上下架、文件数据库持久化、统一响应与异常处理。
- [x] L1：创建订单、按订单号查询详情、按买家手机号分页查询、库存扣减、订单快照、金额计算及参数校验。
- [ ] L2：并发不超卖专项验证及完整业务自动化测试。

目前 L0 和 L1 的主要正常、异常接口已通过人工 HTTP 验证，项目已经完成四次有意义的 Git Commit。健康检查自动化测试已通过。事务异常回滚、并发不超卖、订单金额溢出等专项边界尚未验证，不将设计预期写成实际测试结论。

## 2. 我是怎么拆的

| 阶段 | 实现内容 | 这样安排的原因 |
|---|---|---|
| 1. 工程初始化 | Maven、Spring Boot、健康检查和基本响应结构 | 先确保工程可以编译、运行、测试，降低后续业务错误与环境错误混淆的风险 |
| 2. 数据库设计 | products、orders、H2 文件数据库及初始化 SQL | 商品和订单共享数据约束，先确定字段、类型和关系再写接口 |
| 3. 商品功能及公共设施 | 商品四个接口、统一异常、参数校验、分页和业务分包 | 订单依赖商品查询与库存信息，先验证商品功能，并沉淀可复用的公共能力 |
| 4. 订单模块 | 事务设计、条件扣减库存、订单号及三个订单接口 | 先明确跨表事务边界和并发风险，再实现业务逻辑 |
| 5. 人工验收及文档 | Maven 编译、真实 HTTP 请求、错误日志分析、阶段性提交 | 验证实际执行结果，记录设计取舍和未完成事项 |

阶段提交记录：

| Commit | 主要内容 |
|---|---|
| `0854a5a` | Spring Boot 工程初始化与健康检查 |
| `50d77bc` | 数据库结构及 H2 持久化 |
| `13ee78f` | 商品管理接口与异常处理 |
| `97e7492` | 订单事务与原子库存扣减 |

## 3. 我的程序分成哪几块

### 3.1 技术栈

| 技术 | 用途 |
|---|---|
| Java 17 | 业务代码，使用 record 表达部分不可变数据对象 |
| Spring Boot 3.5.16 | 应用启动、Web 接口、Bean 管理和自动配置 |
| Spring JDBC / JdbcTemplate | 手写 SQL、绑定参数、行映射及数据访问 |
| H2 文件数据库 | 本地数据存储，无需单独部署数据库服务器 |
| Bean Validation、Jackson | 请求参数校验及 JSON 序列化、反序列化 |
| Maven、JUnit、MockMvc | 依赖管理、构建及现有健康检查测试 |

项目默认端口为 8080。由于本地另一个应用占用该端口，人工验收时使用启动参数指定 `127.0.0.1:8081`，未改变项目默认端口。

### 3.2 包结构与分层

采用外部按业务模块分包、内部按技术职责分层的方式：

```text
com.lzu.marketplace/
├── MarketplaceApplication.java
├── common/
│   ├── ApiResponse.java
│   ├── BusinessException.java
│   ├── GlobalExceptionHandler.java
│   ├── JacksonConfig.java
│   └── PageResult.java
├── health/
│   └── HealthController.java
├── product/
│   ├── controller/ProductController.java
│   ├── service/ProductService.java
│   ├── repository/ProductRepository.java
│   ├── model/Product.java
│   └── dto/
│       ├── CreateProductRequest.java
│       ├── UpdateStatusRequest.java
│       └── UpdateStatusResult.java
└── order/
    ├── controller/OrderController.java
    ├── service/OrderService.java
    ├── repository/OrderRepository.java
    ├── model/Order.java
    └── dto/CreateOrderRequest.java
```

| 层次 | 职责 |
|---|---|
| Controller | HTTP 路由、接收参数、触发校验、统一响应包装 |
| Service | 业务规则、异常判断、金额计算、事务边界、协调 Repository |
| Repository | SQL、参数绑定、数据库增删改查和结果映射 |
| Model | 表示商品或订单数据 |
| DTO | 表示接口请求或特定响应，限制客户端可提交的字段 |
| common | 统一响应、分页、业务异常、全局异常处理及 JSON 配置 |

普通商品请求由 ProductController 调用 ProductService，再由 ProductRepository 访问数据库。订单创建由 OrderService 协调 ProductRepository 和 OrderRepository，避免将跨表事务逻辑放入 Controller。

### 3.3 数据库设计

使用 `jdbc:h2:file:./data/marketplace` 持久化数据，启动时执行 `schema.sql`，以 `IF NOT EXISTS` 避免正常重启时重复创建已存在的表及序列。`data/` 不提交至 Git。已通过重启后查询旧商品验证商品数据持久化。

**products 表**

| 字段 | 类型 / 约束 | 含义 |
|---|---|---|
| id | BIGINT、自增主键 | 商品标识 |
| title | VARCHAR(100)、非空 | 商品标题 |
| price | BIGINT、非空、非负 | 商品价格（分） |
| stock | INT、非空、非负 | 可售库存 |
| category | VARCHAR(50)、非空 | 分类 |
| description | VARCHAR(500)、非空、默认空字符串 | 商品描述 |
| status | VARCHAR(8)、默认 on、限制 on/off | 上下架状态 |
| seller_name | VARCHAR(50)、非空 | 卖家名称 |
| created_at | TIMESTAMP、默认当前时间 | 创建时间 |

**orders 表**

| 字段 | 类型 / 约束 | 含义 |
|---|---|---|
| id | BIGINT、自增主键 | 内部订单标识 |
| order_no | VARCHAR(32)、非空、唯一 | 对外订单号 |
| product_id | BIGINT、非空、外键 | 关联商品 |
| product_title | VARCHAR(100)、非空 | 商品标题快照 |
| quantity | INT、非空、至少为 1 | 购买数量 |
| unit_price | BIGINT、非空、非负 | 成交单价（分） |
| total_amount | BIGINT、非空、非负 | 订单金额（分） |
| buyer_name | VARCHAR(50)、非空 | 买家名称 |
| buyer_phone | VARCHAR(20)、非空 | 买家手机号 |
| address | VARCHAR(200)、非空 | 地址 |
| status | VARCHAR(16)、默认 created、限制 created/paid | 订单状态 |
| created_at | TIMESTAMP、默认当前时间 | 创建时间 |
| paid_at | TIMESTAMP、允许空 | 支付时间，当前 L1 未实现支付 |

表之间通过 `orders.product_id` 外键形成一对多关系。商品库存、价格和订单数量等使用数据库 CHECK 约束作为数据完整性防线；业务层仍需在写入前进行校验，返回明确错误。

```text
products (1) ──────── (N) orders
    id  ←──────────── product_id
```

订单不只保存商品 ID，还保存商品标题、成交单价和总金额，避免未来商品信息变化后影响历史订单展示。价格统一以整数分存储，避免浮点金额误差。客户端下单只提交商品 ID、数量及买家信息，金额由服务端根据商品价格计算，使用 `Math.multiplyExact` 防止 Java long 乘法溢出。

订单号使用数据库序列 `order_no_seq`，以 `SO + yyyyMMdd + 至少八位序号` 组成。序号增长时不截断；表上的唯一约束是最后一道唯一性保障。序列跳号是允许的，订单号不承担连续计数职责。实际正常下单已成功生成 `SO2026112500000001`，重启后序列行为与并发唯一性尚未专项验证。

### 3.4 接口、响应及异常处理

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| POST | `/api/products` | 发布商品 |
| GET | `/api/products` | 商品列表、筛选与分页 |
| GET | `/api/products/{id}` | 商品详情 |
| PATCH | `/api/products/{id}/status` | 商品上下架 |
| POST | `/api/orders` | 创建订单 |
| GET | `/api/orders/{orderNo}` | 订单详情 |
| GET | `/api/orders?buyerPhone=...` | 按买家手机号分页查询 |

统一响应包含 `code`、`message` 和 `data`，错误响应的 `data` 为 null。分页使用 `PageResult`，字段为 `total`、`page`、`pageSize`、`items`。

| HTTP 状态 | 业务码 | 含义 |
|---|---:|---|
| 200 / 201 | 0 | 查询、更新或创建成功 |
| 400 | 40001 | 请求参数非法 |
| 404 | 40401 | 商品或订单不存在 |
| 409 | 40901 | 库存不足 |
| 409 | 40902 | 商品已下架 |
| 500 | 50000 | 未预期服务端异常 |
| 404 | 40400 | 未知路由（自定码） |
| 405 | 40500 | 请求方法不支持（自定码） |
| 415 | 41500 | 不支持的媒体类型（自定码） |
| 409 | 40900 | 商品状态并发变化、建议重试（自定码） |

40903 所对应的支付相关功能不在当前实现范围内。

请求 DTO 使用 `@NotBlank`、`@NotNull`、`@Min`、`@Size` 等进行校验；Service 对业务状态和分页范围进行检查。Jackson 显式关闭 `ALLOW_COERCION_OF_SCALARS` 和 `ACCEPT_FLOAT_AS_INT`，拒绝不符合整数要求的字符串或小数数值。实际小数价格请求已返回 400。

`BusinessException` 携带 HTTP 状态和业务码，`GlobalExceptionHandler` 统一处理业务异常、参数错误、JSON 读取异常、路径及类型转换错误等；服务端未预期异常记录日志，响应不返回 SQL 或堆栈。未知接口返回 404 已验证；405、415 分支尚未专项实测。

### 3.5 SQL 与分页

使用 JdbcTemplate 的参数绑定，用户输入作为 SQL 参数，而不是拼接进 SQL 语句。商品 keyword 对标题进行 LIKE 模糊筛选，status、category 精确筛选，多个条件按 AND 组合；动态 SQL 片段来源于固定代码。

商品列表按 `id DESC` 排序，订单列表按 `created_at DESC, id DESC` 排序，唯一 ID 用于消除相同时间的排序歧义。默认 page=1、pageSize=10，pageSize 范围 1～50；offset 使用 long 参与计算，防止 int 乘法溢出。`total` 由单独的 COUNT 查询统计满足条件的记录总数，而不是当前页条数。

稳定排序不能保证数据持续新增或删除时 offset 分页完全没有重复、遗漏；当前业务规模选择较简单的 offset 分页。

## 4. 一次完整链路

以下以创建订单为例：

```text
POST /api/orders
    ↓
OrderController：接收 DTO，触发 Bean Validation
    ↓
OrderService.createOrder()：开启事务（@Transactional）
    ├─ ProductRepository.findById：查询商品
    ├─ 校验商品存在、上架、库存充足
    ├─ Math.multiplyExact：计算服务端订单金额
    ├─ ProductRepository.deductStock：原子条件扣库存
    ├─ OrderRepository.nextOrderNo：数据库序列取号
    └─ OrderRepository.insert：插入订单、获取 ID、回读完整订单
    ↓
正常返回：提交事务 → HTTP 201 + ApiResponse
事务内运行时异常：回滚事务 → 全局异常处理器
```

库存扣减 SQL：

```sql
UPDATE products
SET stock = stock - ?
WHERE id = ? AND status = 'on' AND stock >= ?;
```

读取库存仅用于提前校验；最终扣减由带条件的 UPDATE 判断。受影响行数为 1 表示成功扣减；为 0 时重新查询商品，区分不存在、下架或库存不足。如果重新查询后条件均满足，则按实现进行最多三次有界重试，仍失败返回并发状态变化的业务错误，而不是无依据报告库存不足。

事务放在公开的 Service 方法上，由 Controller 经 Spring Bean 调用。库存扣减、订单插入、主键获取及回读共享同一事务；符合默认回滚规则的异常向外抛出，设计上可撤销先前的库存扣减。数据库序列消耗的值不要求随事务回滚恢复。

真实正常下单测试结果：

| 指标 | 实际值 |
|---|---|
| 商品 | Test Keyboard |
| 下单响应 | HTTP 201 / code=0 |
| 单价 / 数量 / 总额 | 1990 分 / 2 / 3980 分 |
| 商品库存 | 10 → 8 |
| 订单状态 | created |
| 订单详情、手机号列表 | HTTP 200，返回正确订单 |
| 库存不足、下架等异常请求后 | 库存仍为 8，对应买家订单数仍为 1 |

以上证明正常业务链路和已测试的前置校验行为正确，但并未证明扣减成功后、插单异常时的事务回滚，也未证明并发压力下的最终结果。

## 5. 一个取舍

| 方案对比 | 我的选择及理由 | 代价 |
|---|---|---|
| JdbcTemplate vs JPA/MyBatis | 选择 JdbcTemplate。本项目 SQL 数量较少，需要清晰控制查询条件、库存更新语句和受影响行数 | 手动编写 SQL、RowMapper，复杂查询增多时维护成本会上升 |
| H2 文件库 vs MySQL | 选择 H2 文件数据库，降低环境部署成本，支持本地持久化 | H2 与生产数据库的 SQL 兼容性、锁行为和运维能力存在差异，不能据此推定生产环境表现 |
| 条件 UPDATE vs SELECT FOR UPDATE | 选择条件 UPDATE，把状态、剩余库存约束放到写操作中，避免单靠 Java 层先查后改 | 需要处理零行更新、并发状态变化、锁等待等边界；并发结果必须实测 |
| 数据库序列 vs JVM 计数器 | 选择数据库序列，避免依赖单进程内存计数，配合唯一约束 | 回滚允许跳号，序号不连续；数据库迁移时需要适配序列语法 |

## 6. 卡得最久的问题

商品发布第一次真实测试返回 500，但随后查询商品列表发现商品已经写入数据库。通过检查服务端异常堆栈定位到 `ProductRepository.insert` 调用 `GeneratedKeyHolder.getKey()` 的位置。

异常信息显示 H2 返回了两个生成字段：`ID` 和 `CREATED_AT`。原代码使用 `Statement.RETURN_GENERATED_KEYS`，而 `getKey()` 只适用于单生成键，导致 INSERT 已执行、获取主键时发生异常。

修复时将 PreparedStatement 明确限定为只返回 ID：

```java
connection.prepareStatement(sql, new String[]{"ID"});
```

同时在 ProductService.create() 增加 `@Transactional`，使插入、取主键、回读处于同一事务。重新编译和运行后，商品发布成功返回 ID=2，详情、列表和上下架正常。

这次问题的关键不是 SQL 插入失败，而是写入后的处理失败。订单模块因此直接采用指定 ID 生成列的方式，并将库存扣减及插单放在同一事务中。正常订单创建已验证；人为触发事务内写入失败的回滚尚未验证。

另一次订单模块编译错误来自把 record 误当成普通 JavaBean：`product.getId()` 和 `product.getTitle()` 无法解析，改为 `product.id()` 和 `product.title()` 后成功启动并完成接口验收。

## 7. 没做的事和已知问题

| 项目 | 当前状态 |
|---|---|
| 支付接口与支付幂等 | 未实现；订单保持 created，paidAt 为 null |
| 并发不超卖专项测试 | 条件 UPDATE 代码已实现；未进行可重复的并发测试与订单数核对 |
| 事务异常回滚 | 事务边界已实现；未构造扣库存成功后插单失败的实测场景 |
| 自动化业务回归 | 现有健康检查测试通过；商品、订单主要使用人工 HTTP 验收，业务自动化测试尚未补齐 |
| 身份认证与权限控制 | 未实现；手机号筛选不等同于身份认证，不适合直接作为生产环境访问控制 |
| 商品快照一致性 | 快照来自事务内首次查询。目前没有改价接口；未来加入改价或改名后需评估读取与扣减之间的一致性窗口 |
| 订单号序列 | 正常生成已实测；重启连续取号、并发唯一性尚未专项验证 |
| 其他边界 | 金额溢出、部分 JSON 类型转换、405/415 响应及多记录分页仍需补充验证 |

## 8. 如果还有一天

1. 优先完成可重复的并发下单测试：创建独立测试商品，多个请求竞争有限库存，核对成功数量、失败响应、最终库存与新增订单数，不把理论设计当成实测结果。
2. 补充业务自动化测试：使用独立测试数据库，覆盖商品与订单接口、非法参数、库存不足、金额溢出，以及在库存扣减后人为触发订单写入失败并断言回滚。
3. 在核心验证完成后再考虑支付幂等及鉴权等扩展。保持当前工程结构与公共组件，优先保证已实现功能正确，而不是盲目增加接口数量。
