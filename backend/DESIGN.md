# 设计说明（DESIGN）

> 本文件按 `templates/DESIGN.md` 的八个部分逐项填写，内容与 `backend/` 下的实际源码一致。

---

## 1. 我做到了哪一步

- [x] L0：5 / 5 条全部完成（建表、发布商品、商品列表筛选分页、商品详情、上下架），真实 HTTP 验收 57/57 通过
- [x] L1：4 / 4 条全部完成（下单、订单详情、订单列表、参数校验），真实 HTTP 验收 42/42 通过
- [x] L2：做了第 **10** 项（并发不超卖）与第 **12** 项（自动化测试）；第 9 项支付、第 11 项鉴权、第 13 项索引专项未选做

一句话：**我做了一个商品与订单的 Spring Boot 后端，它能发布/筛选/上下架商品、下单并在同一事务里扣库存、按订单号或手机号查订单，还验证了 10 并发抢 1 件库存时不会超卖；但还不能支付和鉴权。**

---

## 2. 我是怎么拆的（最重要）

| 顺序 | 我做的事 | 为什么排在这个位置 |
|---|---|---|
| 1 | 通读 README，先定 L0-1 的两张表（`products` / `orders`）与约束 | 后面所有接口的字段、状态枚举、金额规则都由表结构决定；表不定，接口校验和事务规则无从落笔，改表成本也最高 |
| 2 | 配置 H2 文件库 + `spring.sql.init` 自动建表，先证明「服务能起来、表能建、重启数据还在」 | 建表是地基，先让评审一条命令能起服务，再写业务；否则接口报错时无法分清是逻辑还是环境问题 |
| 3 | 做 L0-2 发布商品（含严格 JSON 类型校验） | 这是第一个写库接口，必须先把「统一响应结构、全局异常、参数校验」三件套立起来，后面每个接口直接复用 |
| 4 | 做 L0-3 列表（筛选 + 分页 + 稳定排序） | 有了数据才能验证筛选与分页；`total`、`page`、`pageSize` 的分页约定在这里定死，L1-8 直接沿用同一套 |
| 5 | 做 L0-4 详情、L0-5 上下架 | 两者都只依赖已建好的表和 Mapper，改动最小；且 L0-5 的状态是 L1-6 下架判断的前提 |
| 6 | 对 L0 做独立验收：补自动化测试 + 真实 HTTP 脚本（57 条） | 先把 L0 的边界钉死，再在它之上做下单，避免 L1 的问题和 L0 的问题混在一起排查 |
| 7 | 做 L1-6 下单（事务 + 原子扣库存 + 服务端算价） | 下单依赖商品的价格、库存与状态，必须等 L0 稳定；这是全项目风险最高的一条链路，单独一个阶段做 |
| 8 | 做 L1-7 详情、L1-8 列表 | 依赖订单已能写入；排序索引 `(buyer_phone, created_at)` 在 L0-1 已建好，这里只需用上 |
| 9 | 做 L2-12 自动化测试（在 L1 源码上进行，边审边补） | 单测和真实 HTTP 脚本要在同一份代码上验证；边做边补比事后集中补更能发现问题 |
| 10 | 做 L2-10 并发不超卖（不改业务代码，只加测试） | 并发验证的是 L1 事务方案是否真的站得住，必须等 L1 定稿；它也反过来给 L1 的原子 UPDATE 提供了证据 |

---

## 3. 我的程序分成哪几块

| 文件 / 模块 | 负责什么 | 为什么单独拆出来 |
|---|---|---|
| `controller/`（`ProductController`、`OrderController`、`HealthController`） | 只做 HTTP 收发：路径、方法、`@RequestParam`/`@RequestBody`、HTTP 状态码 | HTTP 细节与业务规则分开，换路由/加鉴权不影响业务代码 |
| `service/`（`ProductService`、`OrderService`，具体类，无接口+impl） | 参数校验（必填/长度/范围/枚举）、业务规则、事务边界、服务端算价 | 事务必须和参数校验分开写，否则回滚范围会与校验逻辑纠缠；本项目规模无需接口抽象，直接具体类更短更清楚 |
| `mapper/`（`ProductMapper`、`OrderMapper`） | 全部 SQL（注解形式），含原子扣库存、计数、分页查询 | 与业务解耦；参数全部 `#{}` 绑定；若做 L2-13 执行计划对比也只改这一层 |
| `entity/`（`Product`、`Order`） | 数据库行映射（camelCase ↔ 下划线由 `map-underscore-to-camel-case` 完成） | 与表一一对应，不掺接口字段 |
| `dto/` | 接口出入参：`CreateProductRequest`/`CreateOrderRequest`（原始文本保留）、`ProductResponse`/`OrderResponse`、`ProductQuery`/`OrderQuery`、`ProductPageCriteria` | 接口契约不随表结构变化；响应里不出现内部字段，快照字段也能独立表达 |
| `common/` | `ApiResponse`（统一 `code/message/data`）、`ErrorCode`（业务码↔HTTP 状态的唯一映射）、`PageResult`、`ProductStatus` | 返回格式与错误码一处定义，所有接口复用 |
| `exception/` | `BusinessException` + `GlobalExceptionHandler`（异常 → HTTP 状态 + 业务码，堆栈只进日志） | 客户端错误与服务端错误集中分类，避免每个接口各写一套 try/catch |
| `support/` | `RequestBodyParser`：用 Jackson 逐字段读 JSON，严格控制类型 | 见第 9 节：直接把 `price` 绑成 `Long` 会把 `19.9` 静默截断成 `19`，必须自己解析才能既保精度又能报准字段 |
| `schema.sql` / `application.yaml` | 建表脚本与运行配置（H2 文件库、`mode: always` + 全量 `IF NOT EXISTS`） | 让评审一条命令起服务，且重复启动不清数据 |

**如果再加「退款」和「优惠券」，哪里会最先乱掉**：最先乱的是**订单状态流转与金额计算**。现在状态只有 `created`/`paid`，金额恒等于「单价 × 数量」，这条不变量写进了库层 `CHECK (total_amount = unit_price * quantity)`。一旦加优惠券，金额不再等于单价×数量，这个 CHECK 必须改；加退款又要引入新状态与状态机。届时这两件事都必须收敛在 `OrderService` 的同一处，否则会散落到 Controller 与各个 Mapper 里，改一处漏一处。

---

## 4. 一次完整链路是怎么走的

**后端（L1-6 下单，最满意的一条链路）：**

```text
POST /api/orders  { productId, quantity, buyerName, buyerPhone, address }
  → OrderController 只接收字符串体，交给 RequestBodyParser
  → RequestBodyParser：productId/quantity 必须是 JSON 整数（"2"、1.5、true 直接 40001）
  → OrderService.create()（@Transactional 开始）
      ① 校验：quantity ≥ 1、buyerName/address 非空且不超列宽、buyerPhone 纯数字 ≤ 20 位
      ② 查商品：不存在 → 404 / 40401；status='off' → 409 / 40902
      ③ 原子扣库存：UPDATE products SET stock = stock - ?
                       WHERE id = ? AND stock >= ? AND status = 'on'
         → 受影响行数 = 0：回查商品，off → 40902，其余 → 409 / 40901
         → 受影响行数 = 1：继续
      ④ 服务端算价：unitPrice = 商品价格（数据库），totalAmount = multiplyExact(unitPrice, quantity)
      ⑤ 生成订单号 SO+yyyyMMddHHmmss+%06d(单调序号)，插入订单（写商品标题、单价快照，status='created'）
      ⑥ 回读订单，createdAt 取数据库 CURRENT_TIMESTAMP
  → 201 { code:0, message:"ok", data:{ id, orderNo, unitPrice, totalAmount, status:"created", ... } }
  → 任一步抛异常：整个事务回滚，已扣库存自动恢复
```

**后端（L0-3 商品列表）：**

```text
GET /api/products?keyword=笔记本&category=文具&status=on&page=2&pageSize=10
  → ProductService 校验：page ≥ 1、pageSize 1~50、status ∈ {on, off}，keyword 转义 % _ \
  → countByCriteria（筛选后总数 total）+ findByCriteria（ORDER BY created_at DESC, id DESC LIMIT/OFFSET）
  → 200 { code:0, data:{ total, page, pageSize, items:[...] } }
  → 无匹配：total=0、items=[]（空数组，不是 null，也不是 404）
```

---

## 5. 一个取舍：我选了 A，没选 B

- **可以在哪两个方案里选**：**H2 文件模式（`jdbc:h2:file:./data/marketplace`）** / **H2 内存模式（`jdbc:h2:mem:`）**
- **我选了**：文件模式作为正式运行配置；内存模式只用于自动化测试（`src/test/resources/application-test.yaml`）
- **因为**：L0-1 的验收本身就要求「插入测试数据后重启，数据仍在」，只有落盘才能证明；而测试要可重复，必须每次从干净的库开始，所以测试用内存库，两者互不干扰（测试从不碰 `data/`）
- **代价是**：本地会生成 `data/marketplace.mv.db` 等文件（已在 `.gitignore` 忽略 `/data/` 与 `*.mv.db`）；评审第一次跑时没有初始商品，需要自己调接口创建；升级 `schema.sql` 时旧的列不会自动变更，得手工处理旧库文件

> 另一个小取舍：上下架状态存字符串 `'on'/'off'`，而不是 `TINYINT 1/0`。理由是它可以直接照抄 README 的接口契约，H2 控制台里也一眼看懂；代价是多占 2 字节、多一条 `CHECK` 约束。
>
> 还有一个取舍：订单号用「时间戳 + 进程内 `AtomicLong` 序号」，而不是每天重置的数据库序列。理由是单实例场景下实现最简单、并发安全且不额外建表；代价是「序号不按天从 1 开始」，可读性不如 `SO202611190001`（见第 7 节）。

---

## 6. 我卡得最久的一个问题

- **现象**：项目第一轮启动就失败，日志指向数据源，提示 H2 用户名或密码认证失败，连接池无法建立连接，应用启动中断。
- **我一开始以为**：以为是 JDBC URL 写错，或 Spring Boot 4 + H2 2.x 的方言/版本不兼容，也怀疑过 `schema.sql` 里哪句 SQL 把启动带崩了。
- **试过什么**：① 逐字比对 `application.yaml` 里 `url`/`username`/`password` 与连接串；② 确认 `org.h2.Driver` 与 H2 runtime 依赖是否就位；③ 把注意力从日志后半段的建表信息挪回第一个异常。
- **最后靠什么定位的**：读日志里**第一个** `Caused by`——它明确是认证失败，而不是 SQL 语法错误；按该异常去修正 `application.yaml` 中的账号密码配置。
- **真正的原因**：`application.yaml` 里的数据库认证信息与 H2 实际使用的账号不匹配，属配置问题，不是代码或表结构问题。

> 另一个耗时较长的技术问题记在 `AI_USAGE.md` 场景 2：Spring Boot 4 默认使用 **Jackson 3**（包名 `tools.jackson.*`，`JsonNode` 的 `fieldNames()/fields()` 已改为 `propertyNames()/properties()`），第一次编译全部报「找不到符号」，需要按实际依赖版本改 API。

---

## 7. 没做的事和已知问题

| 项 | 状态 | 原因 |
|---|---|---|
| L2-9 支付链路 | 没做 | 按计划只做 L0、L1、L2-10、L2-12；`payments` 表与支付接口均未实现，订单 `status` 只会是 `created`，`paid_at` 恒为 null |
| L2-11 最简鉴权 | 没做 | 未实现 `/api/auth/login`，`POST /api/products` 与 `PATCH /api/products/{id}/status` 当前无需 token |
| L2-13 索引专项（EXPLAIN 对比） | 没做 | 索引 `idx_orders_buyer_phone_created_at` 已随建表创建并服务 L1-8，但没有做加索引前后的执行计划对比证据 |
| 订单删除 / 取消 / 退款 | 不做 | 题目无对应接口，订单状态只保留 `created`/`paid`；若加退款需新增状态并同步改 `CHECK` |
| 更高并发度（如 100 线程） | 未测 | 官方场景是 10 并发；H2 连接池上限制约并发度，更高并发需要换数据库与连接池参数 |
| 订单号跨实例唯一性 | 依赖唯一约束兜底 | 多实例部署时若同一秒重启，`SO+时间戳+序号` 可能撞 `UNIQUE(order_no)`，插入失败回滚返回 50000（不会产生重复订单，但该次下单失败）；单实例无此问题 |
| 订单号序号不按天重置 | 已知 | 序号来自进程内 `AtomicLong`（种子为启动毫秒数 % 100 万），因此形如 `SO20261126004239919909`，可读性弱于「日期 + 当日序号」 |
| 分页 `page` 上限 1000 万 | 自定规则 | 主要防 `LIMIT/OFFSET` 偏移量过大（配合 `offset ≤ Integer.MAX_VALUE` 校验）；不是防溢出所必需，属防御性上限 |
| 自动化测试共享上下文 | 已知 | `HttpEndToEndTest` 与 `L210ConcurrentOrderTest` 复用同一 Spring 上下文与内存库，各自用 `@BeforeEach` 清表，用例之间不串数据 |
| H2 控制台 | 默认开启 | `application.yaml` 里 `spring.h2.console.enabled: true` 仅用于本地验收，对外部署需关闭 |
| 数据库密码 | 明文在本地配置 | 仅本地开发库，仓库中不记录；对外部署应改为环境变量注入 |

---

## 8. 如果还有一天

1. **做 L2-13 的索引证据（排第一）**。理由：这是唯一「代码已完成、只差证据」的项——`idx_orders_buyer_phone_created_at` 已经在用，只需灌数据后用 `EXPLAIN` 对比前后即可产出可复核结论，投入最小、确定性最高；而且它也能顺带说明 L1-8 的分页查询在数据量增长后的表现。（注：L2 两个选题已封顶，此项只作补充验证，不影响现有 L2 完成情况。）
2. **做 L2-11 最简鉴权**。理由：商品发布与上下架目前是裸接口，任何调用方都能改数据，这是当前最明显的安全缺口；实现只需拦截器 + 写死账号 token，改动面小，但能把「谁能改商品」这件事说清楚。
3. **做 L2-9 支付链路 + 订单号按天序号**。理由：支付依赖订单，且要求幂等（同一 `orderNo` 第二次必须 409/40903 且不能产生两条成功记录），需要新增 `payments` 表并给 `order_no` 加唯一约束——正好可以顺带把订单号生成改成「日期 + 当日序号」，同时解决第 7 节里「序号不按天重置、可读性弱」的已知问题。

---

## 9. 补充说明（模板未要求，但评审常追问的几点）

### 9.1 两张表的结构与约束

**`products`**：`id`(自增主键) / `title` VARCHAR(100) 非空 / `price` BIGINT 非空（**单位：分**）/ `stock` INT 非空默认 0 / `category` VARCHAR(32) 非空 / `description` VARCHAR(1000) 可空 / `status` VARCHAR(4) 非空默认 `'on'` / `seller_name` VARCHAR(64) 非空 / `created_at` TIMESTAMP 默认 `CURRENT_TIMESTAMP`。
约束：`CHECK (price > 0)`、`CHECK (stock >= 0)`、`CHECK (status IN ('on','off'))`。

**`orders`**：`id`(自增主键) / `order_no` VARCHAR(32) 非空唯一 / `product_id` BIGINT 外键→`products.id` / `product_title` VARCHAR(100)（快照）/ `quantity` INT / `unit_price` BIGINT（快照，分）/ `total_amount` BIGINT（分）/ `buyer_name` VARCHAR(64) / `buyer_phone` VARCHAR(20) / `address` VARCHAR(200) / `status` VARCHAR(16) 默认 `'created'` / `paid_at` TIMESTAMP 可空 / `created_at` TIMESTAMP 默认 `CURRENT_TIMESTAMP`。
约束：`UNIQUE (order_no)`、`FOREIGN KEY (product_id)`、`CHECK (quantity >= 1)`、`CHECK (unit_price > 0)`、`CHECK (total_amount >= 0)`、**`CHECK (total_amount = unit_price * quantity)`**、`CHECK (status IN ('created','paid'))`；索引 `idx_orders_buyer_phone_created_at (buyer_phone, created_at)` 直接服务 L1-8「按手机号等值 + 按创建时间倒序」。

三个关键决定：① 金额一律 BIGINT 存分，不出现浮点/DECIMAL；② 价格下限收紧为 `> 0`（本期无赠品/优惠券，0 元商品无业务意义，还会把支付金额拖到 0 的边界）；③ 订单状态只保留 `created`/`paid`，删掉 `cancelled`——本期没有取消接口，留着就是「库允许、代码永不产生」的僵尸状态。

### 9.2 技术选型理由

- **Spring Boot 4 + Spring MVC**：题目要求的起步方式（start.spring.io 的 Spring Web + H2），自带依赖注入、事务管理（`@Transactional`）、参数绑定与 Jackson 序列化，避免自己写 Servlet 与 JSON 拼装。
- **MyBatis（注解 Mapper）**：SQL 自己写、看得见，原子 UPDATE 这类需要精确控制的条件扣减比 ORM 生成更可靠；`#{}` 参数绑定天然防注入；没有引入 JPA 那样的一层映射规则。
- **H2 2.4.240（文件模式）**：单文件、零安装，评审一条命令即可起服务；支持 `LIMIT/OFFSET`、`CHECK`、行锁语义，足够验证事务与并发；正式运行用文件库，测试用内存库。
- **Maven**：题目指定的构建方式，`mvn test` 一条命令跑全部测试。

### 9.3 为什么用原子 UPDATE，而不是「先查库存再扣减」或应用层锁

- **先查后扣（SELECT 再 UPDATE）有竞态窗口**：两个请求都读到 `stock=1`，都会认为库存够，然后各自扣减，最终库存变成 -1（超卖）。这是本项目最需要避免的缺陷。
- **加应用层锁（`synchronized` / 进程内 lock）看似简单，但**：① 只对单实例有效，多实例部署立刻失效；② 把并发压力转成线程阻塞，吞吐下降；③ 掩盖了数据库本身的并发问题，评审会追问「换实例怎么办」。
- **带库存条件的原子 UPDATE 把「判断」与「扣减」合并成一条语句**：`WHERE stock >= ?` 由数据库在同一行锁内完成判断与写入，不存在窗口；受影响行数就是结果的唯一真相（1 = 扣成功，0 = 失败），无需二次确认。并发下依靠数据库行锁天然串行化，不依赖任何应用层组件，这正是 L2-10 10 并发实测「恰好 1 成功」能成立的原因。
- **为什么不用 `SELECT ... FOR UPDATE`**：同样能防超卖，但需要「持锁 → 判断 → 更新」三步，锁持有时间更长、死锁面更大，且依赖数据库方言细节；原子 UPDATE 更短、更直接。

### 9.4 金额、快照与订单号的设计理由

- **整数分**：浮点在累加/乘法时有误差（`0.1+0.2 != 0.3`），金额对账会直接崩；`BIGINT` 存分，Java 侧用 `Long`，乘法用 `Math.multiplyExact` 防溢出。
- **订单快照（`product_title` / `unit_price`）**：商品可能改价甚至下架，历史订单必须保留「当时买的是什么、什么价」，否则对账会随商品变化而漂移；同时 `CHECK (total_amount = unit_price * quantity)` 把「金额 = 单价 × 数量」变成库层不变量，服务端算错价根本插不进去。
- **`UNIQUE (order_no)`**：订单号是 L1-7 查询与后续支付的唯一入口，必须唯一。生成用 `SO + yyyyMMddHHmmss + %06d(AtomicLong)`，`%06d` 是**最小宽度**（不足补零、超出按原样），实测 22~24 字符，远小于 `VARCHAR(32)`；唯一性最后靠库约束兜底，绝不产生重复订单。

### 9.5 为什么必须做严格 JSON 类型校验

如果直接把请求体绑到 `Long price`，Jackson 会把 `19.9` **静默截断成 19**（金额精度丢失），把 `"1990"` 抛成笼统的解析错误（指不到字段）。本项目的做法是 `RequestBodyParser` 逐字段读 `JsonNode`：整数字段要求 `isIntegralNumber()`（拒绝小数、字符串、布尔），字符串字段要求 `isTextual()`，非法时返回 `40001` 并明确指出字段名。这样既保住金额精度，又能给出可操作的错误信息；同时未声明字段（如客户端试图传 `price` 改价）一律 400，防止越权改金额。

---
