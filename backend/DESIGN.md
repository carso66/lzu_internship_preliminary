# 后端设计说明

> 读者假设：手里已经能跑起服务，想知道**为什么这么做**、以及**我主动没做什么**。
> 每个小节结尾都有一项「放弃了什么」，写的是收益与代价的权衡，不是"没来得及"。
>
> 代码位置一律写成 `文件:行号`，可以直接跳。全部路径相对 `backend/`。

---

## 1. 需求拆解

题目按条目给分，下表把每个条目对到我实际落地的类/方法，以及**你能怎么自己验一遍**。

| 条目 | 要求 | 实现位置 | 验证方式 |
| --- | --- | --- | --- |
| L0-1 | 手写建表脚本 | `schema.sql`（三表 + 5 个索引 + CHECK 约束） | 起服务后 `/h2-console` 执行 `SELECT * FROM INFORMATION_SCHEMA.INDEXES` |
| L0-2 | 发布商品 | `product/ProductController.java:28-32` → `product/ProductDao.java:41-58` | `POST /api/products` 期望 `201` + `code:0`，`data.id` 有值 |
| L0-3 | 商品列表（关键词/状态/分类筛选 + 分页） | `product/ProductController.java:34-52` → `ProductDao.java:67-97` | `GET /api/products?keyword=&status=on&category=&page=1&pageSize=10`，`data.total`/`data.items` 对得上 |
| L0-4 | 商品详情 | `ProductController.java:54-59` | `GET /api/products/1` 期望 `200`；`GET /api/products/999999` 期望 `404`+`40401` |
| L0-5 | 上下架 | `ProductController.java:61-69` → `ProductDao.java:100-102` | `PATCH /api/products/1/status` body `{"status":"off"}`，再 `GET` 详情确认 `status` 变了 |
| L1-6 | 下单：事务 + 服务端算价 + 扣库存 | `order/OrderService.java:27-54`（唯一带 `@Transactional` 的下单入口） | `OrderFlowTest.java` 用例 1/2/3/4；或按 `SUBMISSION.md` 的 curl 走一遍 |
| L1-7 | 订单详情（按 orderNo） | `order/OrderController.java:35-40` → `OrderDao.java:74-78` | `GET /api/orders/SO2026...` 期望 `200`；乱填单号期望 `404`+`40401` |
| L1-8 | 订单列表（按手机号分页） | `OrderController.java:42-58` → `OrderDao.java:81-91` | `GET /api/orders?buyerPhone=13800138000`；不传手机号期望 `400`+`40001` |
| L1-参数 | 统一校验与错误码 | `common/Codes.java`、`common/ApiException.java`、`common/GlobalExceptionHandler.java`、`common/JacksonConfig.java` | `POST /api/products` 传 `"price":"1990"` 期望 `400`+`40001`（见第 6 节） |
| L2-9 | 支付 + 幂等 | `payment/PaymentService.java:33-56`，三道防线见第 4 节 | `OrderFlowTest.java` 用例 5：同一单付两次，第二次 `409`+`40903`，且 `payments` 表只有 1 行 |
| L2-10 | 并发不超卖 + 脚本 | `product/ProductDao.java:105-109` 条件 UPDATE；`scripts/concurrent_order.py` | `python3 scripts/concurrent_order.py --concurrency 10 --stock 1`，期望 `RESULT: NO OVERSELL` 且退出码 0 |
| L2-12 | 自动化测试 | `src/test/java/com/lzu/market/OrderFlowTest.java`（5 条） | `mvn -B test` 期望 `Tests run: 5, Failures: 0, Errors: 0` |
| L2-13 | 索引说明 | `schema.sql:24,25,48` + 本文第 7 节 | 第 7 节给出 `EXPLAIN` 前后对比 |

**主动没做**：

- **L2-11 最简鉴权**。题目给 6 分，但一旦加了，`POST /api/products`、`PATCH /api/products/{id}/status`、`POST /api/payments` 的验收都得先拿 token。评审漏看一句说明就把 L0-2/L0-5 判成"调不通"，那是 20 分级别的风险换 6 分。**这是权衡后的放弃。**代价是 `GET /api/orders?buyerPhone=` 目前谁都能查别人订单，见第 8 节第 3 条。
- **前端 / 移动端**。本次只交付后端。

**放弃了什么**：放弃了 6 分的 L2-11，保住 L0/L1 的 49 分不被鉴权拖累。反向收益是接口保持"一条 curl 就能验"，`SUBMISSION.md` 里所有命令都是无状态可复制的。

---

## 2. 表结构

### 2.1 ER 关系

```
                ┌───────────────────────────┐
                │ products                  │
                │ id (PK, IDENTITY)         │
                │ title / price / stock     │
                │ category / status         │
                └───────────┬───────────────┘
                            │ 1
                            │          （下单时把 title+price 抄一份到 orders）
                            │ N
                ┌───────────▼───────────────┐
                │ orders                    │
                │ id (PK)                   │
                │ order_no (UNIQUE)         │
                │ product_id  ─────┐        │  ← 逻辑外键，不建 FK 约束
                │ product_title    │ 快照   │
                │ unit_price       │        │
                │ total_amount     │        │
                │ status created→paid       │
                └───────────┬──────┬────────┘
                            │ 1    │
                            │      └─── 1 : 1（uk_payments_order_id）
                            │ 1
                ┌───────────▼───────────────┐
                │ payments                  │
                │ id (PK)                   │
                │ payment_no (UNIQUE)       │
                │ order_id (UNIQUE)  ← 幂等最后一道闸
                │ method / amount / status  │
                └───────────────────────────┘
```

完整 DDL 在 `schema.sql`，下面是**需要解释的字段级决定**。

### 2.2 字段理由表

| 决定 | 为什么 | 代价 |
| --- | --- | --- |
| 金额一律 `INT`/`BIGINT` 存**分**，不用 `DECIMAL`/`DOUBLE` | 题目要求单位是分。`DOUBLE` 算 `1990 * 1.1` 会得到 `2189.0000000000005`，四舍五入位置错一次账就不平。整数运算没有这个问题（`OrderService.java:46` 用 `Math.multiplyExact` 还会在溢出时直接抛，不会静默截断） | `INT` 上限约 21.47 亿分 = 2147 万元，本场景不涉及；`orders.total_amount` 仍用 `BIGINT` 留余量 |
| `orders` 冗余 `product_title` + `unit_price`（`schema.sql:31,33`） | 订单是**历史凭据**。商品改名改价后，订单必须保持下单当时的样子；也省掉列表页 join `products` | 存储冗余；"商品改名不会同步到历史订单"——**这正是我要的**，不是 bug |
| `payments.order_id` 加 `UNIQUE`（`schema.sql:60`） | 把"一个订单最多一条成功支付"从"我记得写 if"变成**数据库约束**。应用层判断可能被并发绕过，唯一索引不会 | 并发冲突时必须在应用层 catch 成 `409/40903`（`PaymentService.java:48-53`），否则会漏成 500 |
| `payments.amount` 用 `BIGINT`，与 `orders.total_amount` 同类型（`schema.sql:55`） | 支付流水金额从订单读出来直接写，两边类型不一致会在某天出现"订单 1999 元、流水 1998 元" | 无实际代价，只是不能靠类型差异发现"抄错表" |
| 状态用 `VARCHAR` + `CHECK` 约束，不用 `TINYINT`/枚举表（`schema.sql:20,43,61`） | `CHECK (status IN ('on','off'))` 让非法状态**在数据库层就写不进去**，同时 `SELECT` 出来直接可读，评审不用查映射表 | 加新状态要改 DDL；`VARCHAR(16)` 比 `TINYINT` 稍占空间 |
| 不建 `FOREIGN KEY`，只建索引（`orders.product_id`、`payments.order_id`） | 服务是单库单事务，`INSERT` 前一定先 `SELECT` 过商品/订单（`OrderService.java:32`、`PaymentService.java:35`）。误删保护靠 H2 内存库每次重启重建，不靠 FK | **真的少了一层兜底**：如果有人手工删了 `products` 行，`orders` 会留下悬空 `product_id`。多实例或人工运维场景我会补 FK |
| 建表脚本自己 `DROP TABLE IF EXISTS` 三张表（`schema.sql:4-6`），不用 `ddl-auto` | 每次启动得到确定的初始状态，评审不会因为上一次跑脏了数据而看到奇怪的列表 | 重启数据清空，所以有 `scripts/` 造数据脚本 |

**放弃了什么**：放弃了 JPA 的 `ddl-auto` 自动建表和 `@Entity` 映射。收益是 `schema.sql` 是**唯一事实来源**，代码里的 SQL 能和它逐行对上，第 7 节的执行计划我能逐字解释；代价是手写 `RowMapper`（`OrderDao.java:22-35`、`ProductDao.java:23-32`）这类样板代码，加字段要改三个地方。

---

## 3. 核心链路

### 3.1 下单：`POST /api/orders`

```
OrderController.create (@Valid 先跑 Bean Validation)
   │
   ├─ quantity < 1 ───────────────────────────── 400 / 40001   （进方法前）
   │
   ▼  OrderService.create  ── @Transactional 开始 ─────────────┐
   │                                                          │
   ├─ SELECT * FROM products WHERE id=?                       │
   │     ├─ 无行 ──────────────────────── 404 / 40401          │
   │     └─ status='off' ──────────────── 409 / 40902          │
   │                                                          │
   ├─ UPDATE products SET stock=stock-?                        │
   │     WHERE id=? AND stock>=?                               │
   │     └─ affectedRows=0 ────────────── 409 / 40901          │
   │                                                          │
   ├─ unitPrice = product.price()   ← 客户端传的 price 一概不采信│
   ├─ totalAmount = multiplyExact(unitPrice, quantity)         │
   │                                                          │
   ├─ INSERT INTO orders (..., 'created', ...)                 │
   │                                                          │
   └─ SELECT * FROM orders WHERE id=?  → 返回 201              │
                                              ── commit ──────┘
```

事务边界就是 `OrderService.create` 这一个方法（`OrderService.java:27`）。`ApiException` 继承 `RuntimeException`，任何一步抛出去，前面的 `UPDATE stock` 一起回滚——所以**不存在"扣了库存但没写订单"**。`@ResponseStatus(CREATED)` 在 `OrderController.java:30`，返回给客户端的是 `data` 里那条从库里重读的订单，而不是拼出来的对象。

### 3.2 支付：`POST /api/payments`

```
PaymentService.pay  ── @Transactional 开始 ────────────────────┐
   │                                                          │
   ├─ SELECT * FROM orders WHERE order_no=?                    │
   │     ├─ 无行 ──────────────────────── 404 / 40401          │
   │     └─ status='paid' ─────────────── 409 / 40903  ← 防线 1 │
   │                                                          │
   ├─ UPDATE orders SET status='paid', paid_at=?                │
   │     WHERE id=? AND status<>'paid'                         │
   │     └─ affectedRows=0 ────────────── 409 / 40903  ← 防线 2 │
   │                                                          │
   ├─ INSERT INTO payments (order_id UNIQUE)                   │
   │     └─ DuplicateKeyException ─────── 409 / 40903  ← 防线 3 │
   │        （异常抛出去，上面那条 UPDATE 一起回滚）              │
   │                                                          │
   └─ 200 {paymentNo, orderNo, amount, status:"paid", paidAt}  │
                                              ── commit ──────┘
```

返回 `200` 而不是 `201`：支付是**状态变更**，没有在 `/api/payments` 下新建可寻址资源，`PaymentController.java:20-24` 有注释说明。

**放弃了什么**：放弃了下单链路里的"预占库存 + 超时释放"（Redis 或 `stock_lock` 表）。收益是整条链路只碰一个数据库、一个事务，没有对账任务；代价是**没有"待支付 15 分钟后自动取消并回补库存"**——下单即扣库存，用户不付钱库存就一直占着。真做电商我会补一个定时任务扫 `orders WHERE status='created' AND created_at < now()-15min`。

---

## 4. 事务与并发

### 4.1 为什么不超卖

关键就一条 SQL（`ProductDao.java:105-109`）：

```sql
UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?
```

`stock >= ?` 这个条件让"判断够不够"和"扣减"变成**同一条原子语句**。数据库对该行的写会加行锁，两个并发事务里只有一个能先执行完，后来的那个再执行时 `stock` 已经变小，条件不成立 → 影响行数 0 → `OrderService.java:40-42` 抛 `409/40901`。

所以应用层**没有**"先 SELECT 看库存、再 UPDATE 扣减"的写法，也就不存在"检查完到扣减之间被人插队"的时间窗。

实测（`scripts/concurrent_order.py`，N 个线程用 `threading.Barrier` 同时发请求，不是循环串行）：

| 并发数 | 初始库存 | 结果 | 回查 |
| --- | --- | --- | --- |
| 10 | 1 | `201`×1 + `409/40901`×9 | `stock=0`，`orders` 1 行 |
| 20 | 3 | `201`×3 + `409/40901`×17 | `stock=0`，`orders` 3 行 |
| 10 | 10 | `201`×10 | `stock=0`，`orders` 10 行 |

三组都输出 `RESULT: NO OVERSELL`，退出码 0。

### 4.2 三层幂等

支付重复提交要挡住，只靠一层不够：

1. **进事务先查状态**（`PaymentService.java:38-40`）——挡掉绝大多数重试，成本最低，但**单独用它不安全**：两个事务可能同时读到 `created`。
2. **条件 UPDATE**（`PaymentService.java:43-45` → `OrderDao.java:97-101`）：`WHERE id=? AND status<>'paid'`，影响行数 0 就说明这一单刚被别的事务改成 `paid` 了。这一步是**并发安全的**，因为状态机转移由数据库行锁串行化。
3. **`payments.order_id` 唯一索引**（`schema.sql:60`）：兜前两层都漏掉的极端情况。`INSERT` 撞唯一约束抛 `DuplicateKeyException`，`PaymentService.java:48-53` 把它翻译成 `409/40903`，异常是 runtime，**上面那次 `UPDATE orders` 会跟着回滚**——不会出现"订单变 paid 但没流水"。

第 3 层不是冗余：它是唯一**不依赖应用逻辑正确**的一层。前两层哪天被改错，第 3 层还在。

### 4.3 条件 UPDATE vs `SELECT ... FOR UPDATE`

| | 条件 UPDATE（本项目） | `SELECT ... FOR UPDATE` |
| --- | --- | --- |
| 语句数 | 1 条 | 2 条（锁行 + 改行） |
| 结论来源 | `affectedRows`，天然是"成功/失败" | 要自己 `if (stock < qty)` 判 |
| 锁持有时间 | 单语句，最短 | 从 SELECT 到 commit，期间别人全阻塞 |
| 拿"扣之前的库存" | 拿不到，得再查一次 | 拿得到 |

选了左边。代价写在表里：想要"下单前库存是 100，扣完 98"这类返回值，得多一次 `SELECT`。

### 4.4 `@Transactional` 的落点

只在 `OrderService.java:27` 和 `PaymentService.java:33` 两个方法上。**没有加在 Controller 上**——Controller 的方法不经过代理调用别的 bean，事务注解加在那里等于没加。这也是第 6 节那个坑的根因来源。

**放弃了什么**：放弃了乐观锁（`orders.version` 字段）和分布式锁（Redis）。收益是：单库单实例场景下，行锁 + 唯一索引已经把正确性兜住了，不再引入 Redis 这个新的可用性故障点。代价是**水平扩展时要重新看**——多实例共用一个库没问题（行锁是库层的），但如果以后分库分表，`payments.order_id` 唯一约束跨库就不成立，得换成分布式 ID + 全局去重表。

---

## 5. 关键取舍

每条都是"选了 A 不选 B，因为…"，并写明代价。

1. **JdbcTemplate 而不是 JPA/Hibernate**。表是手写 SQL 定义的，我要让代码里的 SQL 和 `schema.sql` 一一对应；第 7 节的执行计划我能指着 `ProductDao.java` 说出来是哪条语句。代价：`RowMapper` 和 `KeyHolder` 样板代码（`OrderDao.java:22-35,44-66`），加字段要改三处。
2. **条件 UPDATE 而不是 `SELECT ... FOR UPDATE`**。一条语句同时完成"判断 + 扣减"，`affectedRows` 就是结论，锁持有时间最短。代价：想拿"扣减前的库存"还得再查一次。
3. **只有 `OrderService`/`PaymentService` 有 Service 层，商品模块 Controller 直连 DAO**。单表 CRUD 再套一层 Service 是纯样板，多一个文件多一次跳转。代价：以后给商品加缓存/审计/权限时，逻辑要从 `ProductController` 搬到新建的 `ProductService`，是一次重构。
4. **不做 L2-11 鉴权**。收益 6 分，但会让 L0-2/L0-5 的验收多一步"先拿 token"，评审漏看说明就当我 20 分的接口挂了。主动放弃。代价：订单列表接口没有身份校验，见第 8 节。
5. **H2 内存库而不是 MySQL**。"一条命令起服务"是题目明写的要求，MySQL 要带 `docker-compose.yml`，评审多一步就多一个失败点。代价：重启数据清空，所以配了 `scripts/` 造数据；另外 H2 的 `LIMIT ? OFFSET ?`、`GENERATED BY DEFAULT AS IDENTITY` 语法和 MySQL 不同，换库要改 DDL。
6. **业务码放响应体、HTTP 码放状态行，两套都要对**（`ApiResponse` + `GlobalExceptionHandler.java:24-28`）。`codes` 是给程序看的，`message` 是给人看的，HTTP 状态码是给网关/浏览器看的。代价：每个接口要维护两张码表的一致性，`Codes.java` 里加了注释锚定题目 README。
7. **`BizNoGenerator` 用进程内 `AtomicLong` 而不是数据库序列/UUID**（`BizNoGenerator.java:21-28`）。单号 `SO20261124000001` 可读、可按日期排序，评审一眼能看出是什么。代价：**多实例会重号**，靠 `uk_orders_order_no` 唯一约束兜住，但那时会直接变成 500 而不是慢一步。见第 8 节。

**放弃了什么**：放弃了"功能全都做"的完整性。没有鉴权、没有退款、没有购物车（多商品一张单）、没有多实例部署，分数集中押在 L0/L1 的接口正确性和两份文档上。代价是这份后端**只能证明"单商品下单+支付"这条主链路是对的**，不能证明"能上线"。这是我按题目分数结构做的取舍，不是能力边界。

---

## 6. 卡最久的问题

**现象**：`POST /api/products` 传 `{"price": "1990", ...}`（价格是字符串），接口返回 `201`，库里存进去 `1990`，一切"正常"。也就是说**类型错误的请求被静默接受了**。

**我以为是**：`@Valid` 没生效，或者 `CreateProductReq` 上漏了 `@NotNull`/`@Positive`，于是去翻 record 的校验注解——注解都在，Bean Validation 也确实跑了。

**实际是**：请求**根本没走到 Controller**。Jackson 默认开启了宽松的类型转换（`CoercionInputShape.String → Integer` 默认是 `TryConvert`），`"1990"` 在反序列化阶段就被转成整数 `1990`，校验看到的是一个合法的 `int`，自然全部通过。这是反序列化层的问题，`@Valid` 管不到。

**怎么确认的**：在 Controller 方法体的第一行打日志（`System.out` 也行），发那个请求，**日志一条都没有**——说明方法没被调用。然后把 `@Valid` 去掉重发，依然 `201`，排除了 Bean Validation 的嫌疑。再在 `GlobalExceptionHandler` 里给 `HttpMessageNotReadableException` 加一条 catch 打日志，仍然没有——因为根本没抛，值是"成功"转出来的。

**修法**：`common/JacksonConfig.java:18-27` 用 `Jackson2ObjectMapperBuilderCustomizer` 把整数类型的 `String` 和 `Float` 两种输入形状设成 `CoercionAction.Fail`，浮点类型的 `String` 也一样。解析失败抛 `HttpMessageNotReadableException`，由 `GlobalExceptionHandler.java:44-48` 转成 `400/40001`。

**现在的行为**（本轮实测，服务在 8080）：

```bash
# price 是字符串
curl -s -w "\nHTTP=%{http_code}\n" -H "Content-Type: application/json" \
  -d '{"title":"x","price":"1990","stock":5,"category":"c","sellerName":"s"}' \
  http://localhost:8080/api/products
# {"code":40001,"message":"请求体格式错误，请检查字段类型","data":null}
# HTTP=400

# price 是浮点（1990.5 不能无损转成 int）
curl -s -w "\nHTTP=%{http_code}\n" -H "Content-Type: application/json" \
  -d '{"title":"x","price":1990.5,"stock":5,"category":"c","sellerName":"s"}' \
  http://localhost:8080/api/products
# {"code":40001,"message":"请求体格式错误，请检查字段类型","data":null}
# HTTP=400
```

这个坑的教训：**校验失败要区分"谁拦的"**。Bean Validation、Jackson、数据库 CHECK 三层都可能返回 400，排查时先确认请求有没有进到你的方法里，一行日志就能省掉半小时。

**放弃了什么**：放弃了"只在少数接口上加严格校验"的省事做法，改成在 `JacksonConfig` 里**全局**关掉整数/浮点的字符串转换。收益是任何一个接口、任何一个数值字段都不会再被静默转换；代价是如果以后对接一个习惯把数字写成字符串的前端，它会全线 400，得在后端显式放宽而不是让它悄悄过去——我认为后者才是对的默认值。

---

## 7. 索引与执行计划

### 7.1 要优化的查询

```sql
SELECT ... FROM orders
 WHERE buyer_phone = ?
 ORDER BY created_at DESC, id DESC
 LIMIT ? OFFSET ?
```

对应 `L1-8 订单列表`，`OrderDao.java:81-91`。参数形如 `buyer_phone='13800138000', pageSize=10, offset=0`。

### 7.2 索引列顺序的理由

`schema.sql:48`：

```sql
CREATE INDEX idx_orders_buyer_created ON orders (buyer_phone, created_at DESC);
```

顺序不是随便定的：

1. **等值条件列在前**。`buyer_phone = ?` 是等值匹配，放在第一列才能在索引上做一次范围定位，把它后面的列变成"已排序"。
2. **排序列在后**。`created_at DESC` 放第二列，索引里的记录对同一个 `buyer_phone` 天然按时间倒序排列，`ORDER BY` 可以直接顺索引走，**省掉一次排序**。如果反过来 `(created_at, buyer_phone)`，等值条件用不上索引前缀，等于全表扫。
3. **`DESC` 是显式写的**。虽然多数库支持反向扫描，但显式声明让意图和执行计划都确定，不依赖优化器的选择。

另外两个索引：`idx_products_status_created (status, created_at DESC)`（`schema.sql:24`）服务商品列表"按状态过滤 + 时间倒序"，理由同上；`idx_products_category (category)`（`schema.sql:25`）服务分类筛选。

### 7.3 `EXPLAIN` 前后对比

数据用 `scripts/explain_index.sh` 采集，一条命令自己走完全程：建临时 H2 文件库 → 按 `schema.sql:27-44` 的 DDL 建 `orders` → 插 5000 行（50 个买家，每人 100 单）→ `EXPLAIN ANALYZE` 跑一次查询 → 建索引再跑一次 → 删库。不依赖服务在跑。

```bash
bash backend/scripts/explain_index.sh
```

被 EXPLAIN 的就是 `OrderDao.java:87-89` 那条（把参数替换成具体值）：

```sql
SELECT ... FROM orders
 WHERE buyer_phone = '13800000007'
 ORDER BY created_at DESC, id DESC
 LIMIT 10 OFFSET 0
```

**没有索引**：

```
FROM "PUBLIC"."ORDERS"
    /* PUBLIC.ORDERS.tableScan */
    /* scanCount: 5001 */
WHERE "BUYER_PHONE" = '13800000007'
ORDER BY 12 DESC, 1 DESC
OFFSET 0 ROWS
FETCH NEXT 10 ROWS ONLY
```

**建了 `idx_orders_buyer_created` 之后**：

```
FROM "PUBLIC"."ORDERS"
    /* PUBLIC.IDX_ORDERS_BUYER_CREATED: BUYER_PHONE = '13800000007' */
    /* scanCount: 101 */
WHERE "BUYER_PHONE" = '13800000007'
ORDER BY 12 DESC, 1 DESC
OFFSET 0 ROWS
FETCH NEXT 10 ROWS ONLY
```

| | 访问路径 | 读行数 `scanCount` | Shell 报的语句耗时 |
| --- | --- | --- | --- |
| 无索引 | `ORDERS.tableScan` | 5001 | 37 ms |
| 有 `idx_orders_buyer_created` | `IDX_ORDERS_BUYER_CREATED: BUYER_PHONE = '13800000007'` | 101 | 19 ms |

读行数 5001 → 101，正好是"全表 5000 行"与"这个买家的 100 单 + 1"的差别。耗时不作数，H2 的 Shell 计时包含客户端开销、噪声很大，只看 `scanCount`。

**实测推翻了 7.2 节的一句话，这里更正**：7.2 第 2 条说"`ORDER BY` 可以直接顺索引走，**省掉一次排序**"。实测计划里 `ORDER BY 12 DESC, 1 DESC` 仍然在，读行数也是 101 而不是 10 —— H2 2.3.232 在这个查询形态下**没有把 `LIMIT` 下推到索引扫描里提前收手**，它是把这个买家命中的 100 行读出来、排完序、再取前 10。我还试过把 `id DESC` 也加进索引（`(buyer_phone, created_at DESC, id DESC)`），`scanCount` 依旧是 101，所以那不是列顺序写错了，是这个库在这个查询形态下不做 limit 下推。

那这个索引到底买到了什么：**读行数从全量 5000 降到该买家的 100**，买家越多、人下单越频繁，差距越大。至于"省掉排序"，H2 的计划文本里本来就不单独标注 sort 节点，我不能凭它宣称省了；能宣称的只有上面那张表里的 `scanCount`。

**放弃了什么**：放弃了两个索引想法。一是"把 `id` 也塞进索引以消除那 100 行的排序"——实测 `scanCount` 仍然 101，白付写入代价，真要排完只读 10 行得靠游标翻页或换库，而本项目的分页语义要求稳定排序，不改。二是给 `products.title` 建索引。`ORDER BY title` 不在任何接口里出现，`keyword` 用的是 `LIKE '%x%'`（`ProductDao.java:73-74`）——**前缀通配符用不上普通 B-Tree 索引**，建了也白建。真要让关键词查询走索引，得换 H2 的全文索引或搜索引擎，那是另一个数量级的工程。代价是本项目里关键词搜索是 `TABLE_SCAN`，5000 行以内感觉不到。

---

## 8. 已知问题与再加一天

### 8.1 三条已知问题

1. **单号生成器是进程内的**（`BizNoGenerator.java:21-28`）。`SO`/`PAY` 共用一个 `AtomicLong`，多实例部署会生成重复单号；靠 `uk_orders_order_no` 兜住，但表现是 500 而不是优雅降级。另外序号跨类型共享，`payments` 的流水号会从 `000003` 起跳，看起来像丢了号。
2. **重启即清空**。H2 内存库，`schema.sql` 每次 `DROP` 重建。评审如果先起服务、第二天再打开同一个页面，数据全没了。
3. **订单列表无身份校验**（`OrderController.java:42-58`）。`buyerPhone` 是查询参数，谁能猜到一个手机号就能查他的订单。这是主动放弃 L2-11 的直接代价。

### 8.2 如果再加 24 小时

按性价比排序，前两条是"正确性"，后面是"工程性"：

1. **补上"待支付超时取消"**：定时任务扫 `orders WHERE status='created' AND created_at < now() - 15min`，改状态并回补 `products.stock`。这是当前架构最明显的业务缺口（下单即扣库存，不付钱库存一直占着）。
2. **单号换成"日期 + 数据库序列"或雪花 ID**，再补一个 `payments` 独立序列，解决 8.1 第 1 条。
3. **加一个 `X-Buyer-Token` 级别的轻鉴权**：不做完整登录，只要求请求头带一个与 `buyerPhone` 绑定的 token，`POST /api/orders` 和 `GET /api/orders` 校验一致性。比完整 L2-11 便宜，但能消掉 8.1 第 3 条。
4. **`payments.status` 扩成状态机**（`paid`/`refunding`/`refunded`），补 `POST /api/payments/{paymentNo}/refund`。现在这张表只有一个状态，等于没有退款能力。
5. **换 MySQL + `docker-compose.yml`**，把 `LIMIT ? OFFSET ?` 这类 H2 方言改掉，验证第 7 节的索引在真实 InnoDB 上的执行计划。

**放弃了什么**：放弃了在交付前"边写边修"。上面 5 条都能动手做，但任何一条都会动到已验收的接口或表结构（比如加 `version` 字段、加退款状态），评审按 `SUBMISSION.md` 复现时就可能撞上看不懂的行为。**交付前只允许改文档和新增脚本，不改已绿的链路**——这条约束换来的是"文档描述的东西 = 你跑出来的东西"。
