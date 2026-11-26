# 设计说明（DESIGN）

## 1. 我做到了哪一步

- [x] L0：5 / 5（建表、发布、列表、详情、上下架）
- [x] L1：4 / 4（下单、订单详情、订单列表、参数校验）
- [x] L2：5 / 5（模拟支付、并发不超卖、鉴权、自动化测试、索引专项）

这次我实现的是一个校园好物市集后端，主要业务流程是商品发布、下单和支付。项目使用 H2 内存数据库，可以通过一条命令启动。商品发布和上下架需要登录鉴权，下单时使用事务和悲观行锁扣减库存，订单支持按手机号查询，模拟支付同步返回 `paid`，重复支付返回 409。目前没有实现真实支付回调和退款。

按照题目的计分规则，L2 每项 6 分，两项封顶 10 分。我主要选择 L2-9 和 L2-10，另外也完成了鉴权、自动化测试和索引实验。后面三项不再增加 L2 的分数，但能进一步完善接口保护、验证业务正确性，并了解实际查询的执行情况。

## 2. 我是怎么拆的

| 顺序 | 我做的事 | 为什么这样安排 |
|---|---|---|
| 1 | 手写 `schema.sql` | 先确定三张表、字段和约束，后续业务有明确的数据基础 |
| 2 | 搭建工程骨架 | 统一响应、异常处理和 `/api/health` 都是后续接口共用的组件 |
| 3 | 商品模块 L0-2~L0-5 | 商品模块可以独立运行，适合先验证参数校验、数据库访问和异常映射 |
| 4 | 下单 L1-6 | 下单依赖商品价格、状态和库存，需要单独处理事务与并发 |
| 5 | 订单查询 L1-7/L1-8 | 下单完成后，可以使用实际创建的订单验证详情和分页查询 |
| 6 | 支付 L2-9 | 支付依赖已有订单及其金额，放在订单模块之后实现 |
| 7 | 并发测试 L2-10 | 验证已经实现的下单事务在并发请求下是否正确 |
| 8 | 补齐自动化测试 L2-12 | 在主要功能稳定后集中补充边界和回归用例 |
| 9 | 鉴权 L2-11 | 加入鉴权会改变发布和上下架接口的调用方式，因此放在业务接口稳定之后 |
| 10 | 索引专项 L2-13 | 查询和表结构基本确定后，再通过 EXPLAIN 对比索引效果 |

我选择先建表而不是先写 Controller，主要是因为库存、金额和支付都涉及数据一致性。先确定 CHECK、UNIQUE 和外键约束，可以减少后续接口返工。例如，将 `total_amount = unit_price * quantity` 写成数据库 CHECK 约束，即使业务代码出现错误，数据库也能拒绝不合法的数据。

## 3. 我的程序分成哪几块

| 文件 / 模块 | 职责 | 拆分原因 |
|---|---|---|
| `ProductController` / `OrderController` / `PaymentController` | 参数绑定、调用 Service、封装 `ApiResponse` | 将 HTTP 处理与业务逻辑分开 |
| `ProductService` / `OrderService` / `PaymentService` | 业务校验、状态更新和事务管理 | 在 Service 层统一管理跨 Repository 的事务 |
| `ProductRepository` / `OrderRepository` / `PaymentRepository` | 使用 `JdbcTemplate` 执行 SQL，绑定查询参数 | SQL 和业务分离，同时可以直接查看行锁、条件更新和索引相关语句 |
| `common/ErrorCode` | 集中定义业务码和 HTTP 状态 | 避免在不同接口中重复定义或写错错误码 |
| `common/GlobalExceptionHandler` | 统一处理业务异常、参数错误、JSON 解析异常等 | 尽量保证错误响应结构一致 |
| `auth/AuthController` / `auth/AuthService` / `auth/AuthInterceptor` | 登录、签发内存 token、校验两个写接口的 Bearer token | 通过拦截器集中处理鉴权，避免在每个商品接口中重复校验 |
| `config/JacksonConfig` / `config/WebConfig` | 时间格式、严格类型校验及 UTF-8 配置 | 在 Spring 自动装配的基础上定制，不额外创建 ObjectMapper，具体原因见第 6 节 |
| `dto/` 中的 Request / Response / Query | 请求、响应和查询参数 | 数据库实体与接口契约分开，便于分别维护 |

我没有使用 ORM，而是选择 JdbcTemplate。这个项目的 SQL 数量不多，同时题目重点考察事务、并发和索引。直接写 SQL 更方便理解 `FOR UPDATE`、条件扣减和执行计划，也便于排查问题。

### 如果增加退款和优惠券，哪里最需要调整？

我认为首先需要调整 `OrderService.create()` 和订单状态流转逻辑。目前下单流程比较简单：锁商品、校验、扣库存、计算价格、插入订单。加入优惠券后，算价还需要检查优惠券有效期、使用门槛和抵扣金额，并考虑优惠券与库存一起更新时的事务一致性。

退款则需要增加 `paid → refunding → refunded` 等状态，调整订单状态的 CHECK 约束，并重新检查已有支付状态判断。退款是新的业务操作，不能直接依赖原下单事务的回滚。

如果继续扩展，我会优先把下单方法拆成资源锁定、业务校验、价格计算和数据写入几个内部步骤，仍由外层 `@Transactional` 方法统一管理事务。之后再增加优惠券、退款相关表和状态流转，并明确资源加锁顺序，避免商品和优惠券之间出现死锁。

## 4. 一次完整链路是怎么走的（下单事务）

下单是本项目的核心事务之一。下面以商品库存为 5、购买数量为 2 为例说明执行过程。

```
POST /api/orders {productId: 2, quantity: 2, buyerName, buyerPhone, address}
  ↓
OrderController.create()          —— 只做参数绑定，JSON 反序列化及 @Valid 校验数量
  ↓
OrderService.create()  @Transactional  ─┐ 事务开始
  ↓                                      │
  1. SELECT ... FROM products WHERE id=? FOR UPDATE   ← 悲观行锁，其他事务在此排队
   ↓                                      │
     不存在？ → 抛 BusinessException(40401)  → 事务回滚，锁释放
   ↓                                      │
  2. status = 'off'? → 抛 BusinessException(40902) → 事务回滚
   ↓                                      │
  3. stock < quantity? → 抛 BusinessException(40901) → 事务回滚
   ↓                                      │
  4. UPDATE products SET stock = stock - 2
        WHERE id = ? AND stock >= 2        ← 第二道防线：即便锁失效也不会扣成负数
        affected != 1? → 抛 BusinessException(40901) → 事务回滚
   ↓                                      │
  5. unitPrice = product.price()          ← 只读锁内读到的数据库价格
     totalAmount = Math.multiplyExact(unitPrice, 2)   ← 溢出抛 ArithmeticException → 50000
   ↓                                      │
  6. INSERT INTO orders (order_no, ..., unit_price, total_amount, ...) 
        order_no = "SO" + yyyyMMdd + 32位UUID  ← 唯一约束 uk_orders_order_no 兜底
   ↓                                      │
  7. 回读刚插入的订单，返回订单数据
  ↓                                      ┘ 事务提交，锁释放，HTTP 201
```

如果扣减库存后发生异常，库存更新和订单写入会一起回滚。`OrderRollbackTest` 使用固定单号制造唯一约束冲突，使订单插入失败。测试在事务内读到库存 4，请求结束后从事务外读到库存 5。这两个读数分别证明了扣减确实执行过，以及事务回滚确实生效。

支付接口采用类似的事务处理方式（`PaymentService.pay()`）：

```
POST /api/payments {orderNo, method}
  → SELECT ... FROM orders WHERE order_no=? FOR UPDATE   ← 锁订单行
  → 订单不存在 → 404
  → status = 'paid' → 409 40903（重复支付）
  → INSERT INTO payments (payment_id, order_no, amount, method, status='paid', paid_at)
  → UPDATE orders SET status='paid', paid_at=? WHERE order_no=? AND status='created'
  → 返回 200 + payment
```

两个并发支付同一订单时，后到者在 `FOR UPDATE` 上排队；先到者提交后，后到者读到 `status='paid'` 返回 409。即便这层逻辑失效，`payments.order_no` 上的唯一约束（`uk_payments_order_no`）也会让第二条支付记录插入失败——因此，即使业务层检查出现问题，数据库唯一约束仍可阻止重复写入。

## 5. 一个取舍：我选了 A，没选 B

在下单扣库存时，我考虑了两种方案：A 是 `SELECT ... FOR UPDATE` 悲观行锁，B 是通过带库存条件的 UPDATE 原子扣减。最终采用 A。

AI 最初推荐条件 UPDATE，主要理由是 SQL 更少，也能直接保证库存扣减的原子性。我没有直接采用，主要考虑了以下几点：

1. 题目要求区分商品不存在、商品下架和库存不足，分别返回 40401、40902 和 40901。条件 UPDATE 失败后，仅凭影响行数不能直接区分这些情况，通常还需要额外查询。
2. 行锁方案可以先锁定商品，再依次检查商品状态和库存，最后扣减库存。业务处理顺序比较清楚，也方便在同一个事务中读取价格并保存订单快照。
3. AI 对 H2 的 `FOR UPDATE` 行为有疑虑，我通过并发测试验证了当前环境。在 H2 2.3 中，库存为 1 时发起 10 个并发下单请求，实际结果为 1 个成功、9 个返回 409。

AI 关于 Spring 事务失效的提醒我保留了。`create()` 使用 public 方法作为事务入口，不依赖类内自调用触发新的事务代理；业务异常使用 `RuntimeException` 的子类，不在事务内部捕获后吞掉异常。

行锁方案也有代价。正常扣减前多一次 SELECT，同一商品的并发请求需要等待行锁。如果事务中包含外部网络请求，锁等待时间可能明显增加，所以当前事务中没有网络调用。此外，迁移数据库后仍需重新验证锁行为，可以直接重跑 `ConcurrentOrderTest`。对于当前单机、小规模的作业，我认为这些代价是可以接受的。

## 6. 我卡得最久的一个问题

后来补充参数类型测试时，我发现 `price: "1990"` 被转换成整数 1990 并通过了校验。题目明确要求下单数量不能接受字符串，因此我希望金额、库存和数量等整数参数都采用严格的 JSON 类型校验。

一开始我怀疑是 `application.yml` 中的 `spring.jackson.mapper.allow-coercion-of-scalars=false` 写错了。检查拼写后没有发现问题，于是增加了一个测试用的 Controller，只接收并回显整数，绕开全部业务逻辑。结果 `{"quantity":"2"}` 仍返回 200，而不是预期的 400，说明问题出在反序列化阶段。

接着检查 `ObjectMapper.getDeserializationConfig()`，发现被检查的 mapper 上 `ALLOW_COERCION_OF_SCALARS` 已经是 `false`。这让我开始怀疑实际处理 HTTP 请求的 mapper 和检查的实例不是同一个。

最后发现有两个原因：之前为了统一 `LocalDateTime` 的输出格式，采用了 AI 推荐的 `new Jackson2ObjectMapperBuilder().build()`，手工创建的 mapper 没有继承 Spring Boot 的 `spring.jackson.*` 配置。另外，字符串转数字和浮点数转整数由不同的 Jackson 特性控制，只设置其中一项不能覆盖全部情况。

修复时，我在 `config/JacksonConfig.java` 中改为注册 `SimpleModule` bean，让 Spring Boot 自动装配到已有的 mapper 上；`WebConfig` 只修改已有消息转换器，不再新建 mapper。同时将 `allow-coercion-of-scalars` 和 `accept-float-as-int` 都设为 `false`，关闭对应的类型转换行为。

验证分为两层：`JsonStrictnessTest.ObjectMapperFeaturesApplied` 检查实际 feature 状态；`OrderCreateTest`、`ProductCreateTest` 和 `OrderValidationTest` 从 HTTP 层验证 `"2"`、`2.5`、`2.0`、`true` 等非法整数输入。相关回归测试全部通过，其中 `JsonStrictnessTest` 有 12 个用例。

这次排查让我意识到，框架配置是否正确和运行时是否真正使用该配置是两回事。之后遇到自动装配相关的问题，我会先确认实际使用的 bean 和配置，再考虑修改业务代码。

## 7. 没做的事和已知问题

| 项目 | 状态 | 说明 |
|---|---|---|
| 异步支付回调、轮询和退款 | 未实现 | 当前仅做同步模拟支付，不接入真实支付平台 |
| token 过期、登出、多角色 | 未实现 | 题目不要求这些功能；当前 token 存在服务端内存，重启后失效 |
| 字符串字段接受数字或布尔值 | 已知限制 | 实测 `"buyerName": 123` 会被 Jackson 转成字符串并接受；金额、库存、数量的严格整数校验已覆盖。题目未明确要求字符串字段拒绝这种转换，因此暂未处理，详见 `docs/决策与遗留问题.md` |
| `Accept: application/xml` 返回 406 且响应体为空 | 已知限制 | 与 Spring 内容协商有关，当前系统仅支持 JSON，暂未为这种请求增加特殊处理 |
| 分页 COUNT 和 SELECT 独立查询 | 已知限制 | 并发写入时 total 和 items 可能短暂不一致；两条查询共用 `ProductRepository.Filter` 生成的筛选条件。当前接受弱一致性，未增加快照读 |
| 未提供独立并发脚本 | 已知取舍 | `ConcurrentOrderTest` 使用真实 Tomcat 和 HTTP 请求进行并发集成测试，可以通过 Maven 重跑，但尚未提供脱离测试框架的 shell 脚本 |
| H2 索引未消除排序 | 实验限制 | 本次 scanCount 从 20001 降至 1001，但未验证到 LIMIT 提前终止或免排序的收益，详见附录 A |

### 测试结果

执行 `./mvnw clean test`，共 21 个测试类、242 个用例，0 失败、0 错误、0 跳过。

| 测试类 | 用例数 | 覆盖什么 |
|---|---|---|
| `ProductListTest` | 36 | L0-3：三条件叠加、LIKE 通配符转义、分页边界、越界 400 不夹断 |
| `ProductCreateTest` | 30 | L0-2：字段长度/类型/范围，非法 JSON，SQL 注入按字面量存储 |
| `CommonLayerTest` | 23 | 统一响应结构、异常映射、500 不泄漏堆栈 |
| `ProductDetailAndStatusTest` | 21 | L0-4/L0-5：详情、上下架幂等、非法枚举 |
| `OrderQueryTest` | 20 | L1-7/L1-8：倒序、同时间戳稳定翻页、买家隔离 |
| `OrderValidationTest` | 20 | L1-6：校验顺序短路（quantity 先于商品存在性）、字段长度/空白 |
| `OrderCreateTest` | 17 | L1-6：下单成功、服务端算价、库存不足/下架/不存在、quantity 类型 |
| `JsonStrictnessTest` | 12 | Jackson 严格类型配置确实生效 |
| `SchemaConstraintTest` | 12 | 三表约束：负库存、重复单号、金额不符、重复支付被拒 |
| `PaymentTest` | 10 | L2-9：支付成功、金额服务端决定、重复支付 40903 |
| `AuthTest` | 9 | L2-11：登录签发随机 token、401 业务码 40101/40102、保护范围与公开接口 |
| `ProductBoundaryTest` | 6 | 边界值恰好合法、分页全量遍历不重不漏 |
| `ConcurrentOrderTest` | 5 | L2-10：库存 1，10 并发，恰好 1 成功（`@RepeatedTest` 5 轮） |
| `ConcurrentPaymentTest` | 5 | L2-9 幂等：同一订单 10 并发支付，恰好 1 成功（5 轮） |
| `PurchaseFlowTest` | 4 | 全链路：发布 → 下单 → 支付 → 查询接口可见 |
| `ConcurrentOrderNoUniquenessTest` | 3 | 并发下单 10 个单号互不相同 |
| `ExceptionMappingRegressionTest` | 3 | 415/406 回归、时间格式毫秒精度 |
| `IndexExplainTest` | 2 | L2-13：同一 SQL 加索引前后的 EXPLAIN ANALYZE 与 scanCount 对比 |
| `OrderRollbackTest` | 2 | L1-6 事务：扣库存后插订单失败，库存回滚（双读数证明） |
| `PaymentRollbackTest` | 1 | 支付记录插入失败，订单状态不被更新 |
| `MarketplaceApplicationTests` | 1 | 上下文启动、health 接口 |

## 8. 如果还有一天

1. 优先完善字符串字段的类型校验。现在 `buyerName`、`title`、`address` 等字段仍可能接受非字符串 JSON 值。计划在现有 `JacksonConfig` 的 `SimpleModule` 中注册自定义 String 反序列化器，拒绝非文本 token，并重新执行全量回归测试。这项优先级最高，因为它直接关系到接口输入的准确性。
2. 增加独立的并发验证脚本，例如使用 `curl` 和 `xargs -P` 实现 `scripts/concurrent-order.sh`。目前 Maven 集成测试已经能够验证并发不超卖，独立脚本可以让评审在服务启动后更方便地复现结果。
3. 增加 token 过期和登出机制。当前内存 token 满足题目基本要求，但不能主动失效。如果系统继续向实际应用发展，需要进一步管理 token 的有效期及注销状态。

---

## 附录 A：L2-13 索引专项 —— 查询、索引设计与可复核证据

> 题目要求：从已实现的商品或订单查询中选一条 SQL，自行设计并添加索引；在 DESIGN.md 写明查询和典型参数、选择索引列的理由、评审如何复核；用同一份数据和查询参数对比加索引前后的执行计划。

### A.1 选中的查询与典型参数

L1-8 的订单分页查询（`OrderRepository.searchByBuyerPhone` 的数据查询，与接口 `GET /api/orders?buyerPhone=...` 一一对应）：

```sql
SELECT id, order_no, product_id, product_title, quantity, unit_price, total_amount,
       buyer_name, buyer_phone, address, status, created_at, paid_at
FROM orders
WHERE buyer_phone = ?
ORDER BY created_at DESC, id DESC
LIMIT 10 OFFSET 0
```

典型参数为 `buyer_phone = '13800000000'`、`page = 1`、`pageSize = 10`。这个接口按买家手机号筛选订单，再按时间倒序分页。随着订单数量增长，有必要减少每次查询的扫描范围。

### A.2 索引设计与选列理由

```sql
CREATE INDEX idx_orders_buyer_phone_created
    ON orders (buyer_phone, created_at DESC, id DESC);
```

| 列 | 作用 | 选择理由 |
|---|---|---|
| `buyer_phone` | 等值过滤 | 放在联合索引首列，便于按手机号缩小查询范围 |
| `created_at DESC` | 时间排序 | 与订单列表的主要排序条件一致 |
| `id DESC` | 次级排序 | 创建时间相同时按 ID 排序，使静态数据集上的分页顺序保持稳定 |

我也考虑过只给 `buyer_phone` 建索引，它能减少扫描范围，但不能直接提供完整的排序顺序。只给 `created_at` 建索引则不能直接利用手机号等值条件缩小扫描范围。不建索引时，本次实验使用的是全表扫描。

商品标题查询使用 `LIKE '%kw%'`，普通 B 树索引通常无法有效利用前缀定位。`docs/决策与遗留问题.md` 中记录了相关 EXPLAIN 结果，使用 `LOWER()` 与否均为 tableScan，因此本次没有为标题增加普通索引。后续可以考虑全文检索。

### A.3 复现方法

```bash
cd backend
./mvnw test -Dtest=IndexExplainTest
```

测试构造 20 个买家，每人 1000 条订单，共 2 万行，创建时间逐行递增。在相同数据、SQL 和参数下，先删除索引执行 `EXPLAIN ANALYZE`，再按 `schema.sql` 重建索引并执行同样的查询。

测试会输出完整执行计划和 `scanCount`，并对结果进行断言，可与 `SUBMISSION.md` 中 L2-13 的记录核对。

### A.4 实测结果（H2 2.x）

| 场景 | 执行计划关键行 | scanCount |
|---|---|---:|
| 无索引 | `/* PUBLIC.ORDERS.tableScan */` + 内存排序 | 20001 |
| 有索引 | `/* PUBLIC.IDX_ORDERS_BUYER_PHONE_CREATED: BUYER_PHONE = ?1 */` | 1001 |

加索引后，扫描计数从 20001 降到 1001，约为原来的二十分之一。主要收益来自手机号过滤：查询不再扫描所有买家的订单。

不过，本次 H2 执行计划没有在取得 10 条结果后提前停止扫描，仍需对目标买家的订单进行排序。因此这里能确认的是扫描范围缩小，不能据此声称消除了排序，也不能把扫描量下降直接等同于耗时下降 20 倍。

如果以后迁移到 PostgreSQL 或 MySQL，可以用同一份数据和查询重新检查执行计划，验证联合索引是否还能避免额外排序。当前结论仅针对本次 H2 实验。
