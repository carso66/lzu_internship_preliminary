# 设计说明（DESIGN）

**项目**：校园好物市集 · 后端服务
**技术栈**：Java 21 / Spring Boot 4.2.0 / Spring Data JPA (Hibernate 7) / MySQL 8 / Lombok / Bean Validation
**目录**：`backend/`，建表脚本 `backend/schema.sql`

---

## 1. 我做到了哪一步

- [x] **L0**：建表、发布商品、商品列表（筛选叠加 + 分页）、商品详情、上下架
- [x] **L1**：下单（事务 + 原子扣库存）、按订单号查详情、按手机号查订单列表
- [x] **L2**：做了第 **9** 项（支付链路：支付、幂等、订单状态流转、支付单查询）
- [ ] L2-10 并发不超卖：并发方案已内建在下单里并自测通过（10 并发只剩 1 单），但没按挑战项提交脚本与说明
- [ ] L2-11 最简鉴权 / 前端改造：未做

一句话：**我做了一个校园市集的商品与订单后端，它能发布商品、筛选列表、下单扣库存、支付并保证不重复扣款，但还不能退款、不能登录鉴权、也没有把并发挑战项按提交物交付。**

---

## 2. 我是怎么拆的

| 顺序 | 我做的事 | 为什么排在这个位置 |
|---|---|---|
| 1 | 先按需求反推表结构，手写 `schema.sql` | 字段类型（金额用分、状态用 tinyint）定错了，后面所有层都得返工，先钉死最底下的东西 |
| 2 | 先做统一的响应体 `Result` 和错误码枚举 | 所有接口都要用它，先做省得每个接口各写一套 `{code,message,data}` |
| 3 | 再做商品四件套（建/列/详/改状态） | 商品是唯一没有外部依赖的领域，用它把 controller→service→repository 这条链路跑通，后面照抄结构 |
| 4 | 下单放在商品之后 | 下单要读商品价格和库存，商品不通没法测；下单也要生成订单号、扣库存，是第一个带事务的接口 |
| 5 | 支付放在下单之后 | 支付依赖 `orderNo`，必须先能下单才能测支付；支付又要求把订单改成 `paid`，正好复用订单实体 |
| 6 | 最后补支付单查询 + 并发自测 | 并发问题只有在主流程正确之后才测得出来，否则分不清是逻辑错还是并发错 |

一句话原则：**先定不会变的东西（表结构、响应体、错误码），再做依赖最少的领域，最后做依赖最多的。**

---

## 3. 我的程序分成哪几块

```
com.lzu.xy
├── XyApplication              启动类
├── config/JacksonConfig       收紧 JSON 反序列化（拒绝 "2"、1.5 这类脏数据）
├── controller/                只做路由和参数接收，不写业务
│   ├── ProductController      /api/products
│   ├── OrderController        /api/orders
│   ├── PaymentController      /api/payments
│   └── model/
│       ├── dto/               入参对象 + Bean Validation 注解
│       └── vo/                出参对象，负责把数据库值翻译成接口值
├── service/                   业务与事务边界
│   ├── ProductService
│   ├── OrderService
│   └── PaymentService
├── repository/                Spring Data JPA 接口，含原子 UPDATE
├── domain/                    JPA 实体，一个类对应一张表
├── enums/                     状态枚举（库内 0/1/2 ↔ 接口 created/paid…）+ 错误码
├── exception/                 BusinessException + 全局异常处理器
├── response/Result            统一响应外壳
└── util/                      雪花 ID、订单号生成器
```

| 文件 / 模块 | 负责什么 | 为什么单独拆出来 |
|---|---|---|
| `controller/model/dto` | 入参 + 校验注解 | 校验规则跟着接口走；DTO 变了不影响实体，实体变了也不影响接口契约 |
| `controller/model/vo` | 出参 | 数据库存 `status=0/1/2`，接口要 `created/paid/cancelled`。翻译只放在 VO 的 `from()` 里一处，不在每个 service 里 `if` |
| `service/` | 业务编排 + `@Transactional` | 下单要「扣库存 + 插订单」原子完成，事务边界必须在 service，放 controller 里会和参数校验、HTTP 细节缠在一起 |
| `repository/` | 数据访问 | 原子扣库存、原子改支付状态这类 SQL 集中在这里，service 不写 SQL |
| `enums/` | 状态与错误码 | 魔法数字收敛到一处，前后端对齐时只改一个枚举 |
| `exception/` | 异常 → 统一响应 | 业务代码只 `throw new BusinessException(...)`，不关心 HTTP 状态码怎么拼 |
| `util/` | 无状态的纯工具 | 雪花 ID 和时间有关，必须单例，不能每次 new |

> **如果再加「退款」和「优惠券」，哪里会最先乱掉？**
> 会先是 `OrderService`——它现在同时管「下单」和「查订单」，退款会再塞进来一个改状态 + 反向加库存的流程，事务和状态机很快纠缠。
> 我会先把订单状态流转抽成一个 `OrderStatusMachine`（只允许 created→paid、created→cancelled、paid→refunded），再把扣/还库存抽成 `StockService`，让退款和下单复用同一套库存原子操作。
> 优惠券则是另一个聚合，应该独立成 `CouponService` + `coupon` 表，在 `OrderService` 里只调用它，不把规则写进订单。**现在这个结构能舒服地撑到这里，但我不会说它不用重构。**

---

## 4. 一次完整链路是怎么走的

以最核心的**下单**为例（`OrderService.create`）：

```
POST /api/orders  { productId, quantity: 2, buyerName, buyerPhone, address }
  → @Valid + Jackson 严格模式
        quantity 是 "2" / 1.5 / 0 / -1 ── ✗ → 400 + 40001，message 指出是 quantity
  → @Transactional 开启事务
  → 查商品（过滤 is_deleted=1）
        不存在 / 已删除 ── ✗ → 404 + 40401
        status = 0（下架）── ✗ → 409 + 40902
        stock < quantity ── ✗ → 409 + 40901
  → 原子扣库存
        UPDATE products SET stock = stock - 2
         WHERE id = ? AND stock >= 2 AND is_deleted = 0
        影响 0 行（并发下被别人抢光）── ✗ → 409 + 40901
  → 用数据库里的 price 算 unitPrice、totalAmount（不采信客户端）
  → 生成雪花 id + 当天序号订单号 SO202611250001
  → INSERT orders
        INSERT 失败 ── ✗ → 异常向上抛 → 整个事务回滚 → 刚才扣的库存自动还原
  → 提交事务
  → 返回 201 + { code:0, data:{ orderNo, unitPrice, totalAmount, status:"created", … } }
```

**关键点**：扣库存和插订单在同一个事务里，任何一步抛异常，库存都会回到扣之前的样子。这一点我用「给 `orders` 建一个必然失败的触发器」实测过：下单返回 500，库存纹丝不动。

---

## 5. 一个取舍：我选了 A，没选 B

- **可以在哪两个方案里选**：扣库存用「先查库存再 `UPDATE` 写回」 / 用「带条件的原子 `UPDATE`」
- **我选了**：原子 `UPDATE ... WHERE stock >= :quantity`，按影响行数判断成败
- **因为**：
  1. 「先查后写」在并发下两个请求会读到同一个库存，都判断"够"，然后各扣一次 → 超卖；
  2. 原子 `UPDATE` 把「判断」和「扣减」压成一条语句，由数据库的行锁保证不会扣成负数，影响 0 行就是没抢到；
  3. 顺带就满足了 L2-10 的并发要求，不用另写锁。
- **代价是**：
  - 拿不到扣之前的库存值做提示（只能笼统返回「库存不足」）；
  - 这条 `UPDATE` 是手写 JPQL，`@Modifying` 会清空一级缓存，所以代码里要重新查、不能依赖之前那个 `Product` 对象的字段；
  - 如果以后要按「库存充足度」做差异化提示，还得再查一次。

> 另一个我明确纠结过的点：**金额用 `long` 存分**，而不是 `BigDecimal` 存元。因为题目要求全程整数分，用 `BigDecimal` 反而每个地方都要考虑 scale；代价是前端展示必须自己除以 100，且我要防住有人传 `1.5`，所以加了 Jackson 严格模式。

---

## 6. 我卡得最久的一个问题

- **现象**：`price` 传 `1.5`，接口返回 201，数据库里价格变成了 `1`。也就是说**脏数据被静默截断**了，比我预想的最坏情况更糟——它不报错。
- **我一开始以为**：是实体字段类型写错了（以为还是 `BigDecimal`），去翻 `Product.java`。
- **试过什么**：
  1. 检查实体类型 → 已经是 `Integer`，不是实体的锅；
  2. 加 `spring.jackson.deserialization.accept-float-as-int: false` → 部分生效，但传 `"2"`（字符串）仍然被接受；
  3. 试 `@JsonDeserialize` 逐个字段加 → 太啰嗦，而且题目要求的是"所有整数字段"都严格。
- **最后靠什么定位的**：读 Jackson 3 的 `CoercionConfig` 文档，发现「字符串转整数」和「小数转整数」是两套独立的 coercion 规则，光改 `accept-float-as-int` 只覆盖了后者。
- **真正的原因**：Spring Boot 4 用的是 **Jackson 3**（包名 `tools.jackson.*`），默认对整数类型做了宽松转换——`"2"` → `2`、`1.5` → `1` 都是默认行为。需要声明式地按 `LogicalType.Integer` 把 String / Float / Boolean 三种输入形状全部设成 `CoercionAction.Fail`。

**解决**：新增 `config/JacksonConfig.java`，注册 `JsonMapperBuilderCustomizer`：

```java
builder.withCoercionConfig(LogicalType.Integer, cfg -> {
    cfg.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
    cfg.setCoercion(CoercionInputShape.Float,  CoercionAction.Fail);
    cfg.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
});
```

然后再让 `GlobalExceptionHandler` 用 `JacksonException.getPath()` 把出错的字段名提取出来，这样错误信息从「请求体格式不正确」升级成「quantity 类型不正确或格式非法」——**用户能一眼知道改哪个字段**，这是我认为比"能跑"更值钱的地方。

> 附带踩的坑：Spring Boot 4 已经不再带 `com.fasterxml.jackson.databind`，网上所有 Jackson 2 的 `ObjectMapper` 配置示例在这里都直接编译不过，必须认准 `tools.jackson.*`。

---

## 7. 没做的事和已知问题

| 项 | 状态 | 原因 |
|---|---|---|
| L2-10 并发不超卖提交物 | 代码已具备，缺提交脚本 | 并发原子扣库存已经实现并自测（10 并发 → 1 成功 9 个 409、库存 0、1 条订单），但没按挑战项要求写可重跑脚本 + `SUBMISSION.md` 说明 |
| L2-11 鉴权 | 未做 | 时间分配给了支付链路 |
| 订单取消接口 | 未做 | 题目未要求；数据库里 `status=2` 已预留 |
| 支付回调 | 未做 | 题目允许同步返回 `paid` |
| `payments` 唯一索引 | 需要手写 `schema.sql` | `ddl-auto: none`，靠 Hibernate 建表不会带唯一索引，幂等兜底会缺失 |
| 订单号跨实例唯一 | 只在单实例内保证 | `OrderNoGenerator` 用 JVM 内 `synchronized` + 库内续号；多实例部署需要改成数据库序列或 Redis 自增 |

---

## 8. 如果还有一天

1. **把 L2-10 补成交付物**——代码已经能扛并发，缺的只是一份别人能一键跑起来的脚本和说明。这是"已有能力变成分数"性价比最高的一步，所以排第一。
2. **补鉴权（L2-11）**——现在发布商品和下架是公网裸奔的，这是真实的产品缺陷，不只是评分项。
3. **给 `OrderService` 抽状态机**——现在支付、取消、将来退款都在改 `orders.status`，散着改迟早出错；先抽 `OrderStatusMachine`，后面加退款才不会把订单逻辑撕碎。

---

## 9. 我认为值得说的地方（亮点）

### 9.1 全局异常：业务代码不碰 HTTP

`BusinessException` 只带「业务码 + HTTP 状态」，`GlobalExceptionHandler` 统一翻译成 `{code,message,data}`。业务层写的是：

```java
throw new BusinessException(ResultCodeEnum.INSUFFICIENT_STOCK);
```

不用写 `new ResponseEntity<>(..., HttpStatus.CONFLICT)`，也不用在每个 controller 里 try-catch。兜底的 `Exception` 处理器会 `log.error` 完整堆栈但**只返回 50000「服务出错」**，不把堆栈泄露给客户端。

另外每个参数错误都指名道姓：`quantity 必须是大于等于1的整数`、`buyerPhone 格式不正确，应为11位手机号`、`method 只能是 alipay 或 wechat`。**判卷的和前端都不用猜是哪出了问题。**

### 9.2 雪花算法：ID 自己生成，不依赖自增

`SnowflakeIdGenerator`：41 位时间戳 + 5 位数据中心 + 5 位机器 + 12 位序列，纪元 2024-01-01。

- 单体、多实例都不冲突（不同 workerId 生成不同 ID）；
- ID 里带时间，天然按创建顺序递增，做分页排序友好；
- 处理了**时钟回拨**：回拨超过 5ms 直接抛异常拒绝生成，5ms 内自旋等待，宁可失败也不生成重复 ID；
- 用 `synchronized` 保护 `sequence`，同进程并发安全。

订单号也是同理：`SO` + `yyyyMMdd` + 当天 4 位序号，启动/跨天时从库里查当天最大的订单号续号，用 `order by length(...) desc, order_no desc` 排序，保证序号破万（`10000` vs `9999`）时排序仍然正确。

### 9.3 数据库字段设计

| 设计 | 我为什么这么设计 |
|---|---|
| 金额一律 `INT`/`BIGINT` 存**分** | 浮点数存钱是最经典的坑（`0.1+0.2 != 0.3`）。整数分从数据库到 JSON 全程不出现小数，`totalAmount` 用 `long` 防 `unit_price × quantity` 溢出 |
| `updated_at`（不是 `update_at`） | 统一复数过去式命名，和 `created_at` 对齐 |
| 状态用 `TINYINT` 存 0/1/2 | 省空间、能加索引；对外在 VO 层翻译成 `on/off`、`created/paid/cancelled`，数据库值永远不直接暴露给前端 |
| 订单存 `product_title`、`unit_price` **快照** | 商品改价、下架后，历史订单必须保持当时的价格和名称，否则对账全错。这是订单表最容易被忽略的设计 |
| `is_deleted` 逻辑删除 | 商品被下单后不能物理删除，否则订单就指向了不存在的商品 |
| `created_at/updated_at/created_by/updated_by/is_deleted` 五件套 | 所有业务表统一，排查问题时任何一张表都能回答"谁在什么时候改的" |
| 订单号 `UNIQUE KEY uk_orders_order_no` | 订单号是业务主键，必须由数据库兜底唯一，不能只靠代码 |
| `payments.order_id UNIQUE` | 支付幂等的最后一道保险：就算应用层判断被绕过，数据库也不允许同一订单有两条支付记录 |
| 联合索引 `(status, is_deleted)` | 最常出现的查询是「上架且未删除的商品」，正好走这个索引 |
| `products.stock INT UNSIGNED` | 库存不可能为负，让数据库也拦一道 |

### 9.4 场景化错误码，而不是只返回 500

| code | HTTP | 场景 |
|---|---|---|
| `0` | 200/201 | 成功 |
| `40001` | 400 | 参数不对（类型错、超范围、枚举非法） |
| `40401` | 404 | 商品 / 订单 / 支付单不存在 |
| `40901` | 409 | 库存不足 |
| `40902` | 409 | 商品已下架 |
| `40903` | 409 | 订单已支付，不可重复支付 |
| `40904` | 409 | 订单当前状态不可支付（如已取消，自定义码） |
| `50000` | 500 | 服务出错（不返回堆栈） |

---

## 10. 接口设计

所有接口共用外层：

```json
{ "code": 0, "message": "ok", "data": { } }
```

`code` 与上表一致；失败时 `data` 为 `null`。金额单位全部是**分**。

### 10.1 `POST /api/products` 发布商品

| 项 | 说明 |
|---|---|
| 请求体 | `title`、`price`、`stock`、`category`、`description`、`sellerName` |
| 校验 | `title` 1~50 字符；`price` 正整数（分）；`stock` 0~999 整数；`category` ≤50；`description` ≤200；`sellerName` ≤64。`price` 传小数或字符串 → 400 |
| 成功 | **201**，`data` 为商品详情，`status` 固定 `"on"`（新建即上架） |
| 失败 | 400 + `40001`，message 指明字段 |

```json
// 请求
{ "title": "测试商品", "price": 1990, "stock": 10, "category": "文具", "description": "备注", "sellerName": "校园数码铺" }
// 201
{ "code": 0, "message": "ok", "data": {
  "id": 383989678841401344, "title": "测试商品", "price": 1990, "stock": 10,
  "category": "文具", "description": "备注", "status": "on",
  "sellerName": "校园数码铺", "createdAt": "2026-11-25T22:37:53" } }
```

### 10.2 `GET /api/products` 商品列表

| 参数 | 说明 |
|---|---|
| `keyword` | 标题模糊匹配（`%` `_` `\` 已转义，输入里的通配符不会生效） |
| `status` | `on` / `off`，不传表示全部；传其他值 → 400 |
| `category` | 分类精确匹配 |
| `page` / `pageSize` | 默认 1 / 10，`pageSize` 1~50 |
| 行为 | 三个筛选条件**可叠加**，默认只返回未删除的商品，按 `created_at` 倒序 |

成功 **200**，`data` 包含 `total`（筛选后总数）、`page`、`pageSize`、`items`；无匹配时 `items` 为空数组、`total` 为 0，不是 404。

```json
{ "code": 0, "message": "ok", "data": { "total": 0, "page": 1, "pageSize": 10, "items": [] } }
```

### 10.3 `GET /api/products/{id}` 商品详情

成功 **200**，`data` 为单条商品；不存在或已删除 → **404 + `40401`**；`id` 不是数字 → 400 + `40001`。

### 10.4 `PATCH /api/products/{id}/status` 上下架

| 项 | 说明 |
|---|---|
| 请求体 | `{ "status": "on" }` 或 `{ "status": "off" }`，其他值 → 400 + `40001` |
| 成功 | **200**，`data` 为 `{ "id": …, "status": "off" }` |
| 失败 | 商品不存在 → 404 + `40401` |

### 10.5 `POST /api/orders` 下单

| 项 | 说明 |
|---|---|
| 请求体 | `productId`、`quantity`、`buyerName`、`buyerPhone`、`address` |
| 校验 | `quantity` 必须 ≥1 的**整数**（`0`、`-1` 拒绝；字符串 `"2"`、小数 `1.5` 在反序列化阶段就拒绝）；`buyerPhone` 11 位手机号；`buyerName` ≤64；`address` ≤255 |
| 处理顺序 | 校验参数 → 查商品 → 判下架 → 判库存 → 原子扣库存 → 按库内价格算金额 → 生成订单号 → 插订单，**全部同一事务** |
| 成功 | **201**，`data` 为订单详情 |
| 失败 | 商品不存在 404/`40401`；已下架 409/`40902`；库存不足 409/`40901`；参数错 400/`40001` |

```json
// 请求
{ "productId": 3, "quantity": 2, "buyerName": "张三", "buyerPhone": "13800138000", "address": "兰州市城关区天水南路222号" }
// 201
{ "code": 0, "message": "ok", "data": {
  "id": 383976388295790592, "orderNo": "SO202611250004", "productId": 3,
  "productTitle": "考研数学复习全书", "quantity": 2, "unitPrice": 4990,
  "totalAmount": 9980, "status": "created", "buyerName": "张三",
  "buyerPhone": "13800138000", "address": "兰州市城关区天水南路222号",
  "createdAt": "2026-11-25T21:45:04", "paidAt": null } }
```

**说明**：`unitPrice` / `totalAmount` 一律由服务端按数据库价格计算，客户端即使传了价格字段也不采用（DTO 里根本没有这个字段，传了会被忽略）。

### 10.6 `GET /api/orders/{orderNo}` 订单详情

成功 **200**；不存在或已删除 → **404 + `40401`**。

### 10.7 `GET /api/orders` 买家订单列表

| 参数 | 说明 |
|---|---|
| `buyerPhone` | **必填**，11 位手机号；缺失 → 400「buyerPhone 不能为空」，格式错 → 400「格式不正确，应为11位手机号」 |
| `page` / `pageSize` | 默认 1 / 10，`pageSize` 1~50 |
| 排序 | 按 `created_at` **倒序** |
| 成功 | **200**，`data` 结构同商品列表：`{total, page, pageSize, items}` |

### 10.8 `POST /api/payments` 发起支付

| 项 | 说明 |
|---|---|
| 请求体 | `{ "orderNo": "SO…", "method": "alipay" \| "wechat" }`；`method` 其他值 → 400/`40001` |
| 处理 | 查订单 → 判状态 → 原子把订单 `created→paid` 并写 `paid_at` → 写 `payments`，同一事务 |
| 成功 | **200**（不是 201），`data` 见下 |
| 失败 | 订单不存在 404/`40401`；订单已支付 409/`40903`；订单已取消等不可支付状态 409/`40904` |
| 幂等 | 重复支付同一订单必定 409；并发 10 个请求只会有 1 个 200，库里只有 1 条支付记录 |

```json
// 请求
{ "orderNo": "SO202611250004", "method": "alipay" }
// 200
{ "code": 0, "message": "ok", "data": {
  "paymentId": "PAY383989678841401344", "orderNo": "SO202611250004",
  "amount": 9980, "status": "paid", "method": "alipay",
  "paidAt": "2026-11-25T22:37:53" } }
```

**幂等是怎么保证的**：订单状态用带条件的原子 `UPDATE`（`WHERE id=? AND status=0`），并发时只有一个能改成功；改 0 行就重新查状态区分「已支付(40903)」和「已取消(40904)」。此外 `payments.order_id` 有唯一索引，`saveAndFlush` 冲突会被翻译成 `40903`，**即使应用层判断被绕过也落不下第二条成功记录**。

### 10.9 `GET /api/payments/{paymentId}` 查询支付状态

成功 **200**，`data` 结构同上，`status` 为 `processing` / `paid` / `failed`；`paymentId` 不存在 → **404 + `40401`**。前端轮询这个接口即可，不需要额外字段。
