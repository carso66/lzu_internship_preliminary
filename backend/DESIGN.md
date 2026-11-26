# 设计说明（DESIGN）

**项目**：校园好物市集 · 后端服务

**技术栈**：Java 17 编译目标 / Spring Boot 4.2.0 / JdbcTemplate / H2 / Jackson 3

**目录**：`backend/`；建表脚本：`backend/schema.sql`

本工程沿用当前已运行验证的 Spring Boot 4.2.0 与 Jackson 3。题目中的 Spring Boot 3.x 是入门示例；接口和验收规则仍按题目实现，依赖及异常 API 按工程实际版本适配。

## 1. 我做到了哪一步

- [x] L0：商品/订单建表，发布商品，筛选分页，商品详情，上下架。
- [x] L1：事务下单、服务端算价、扣库存、订单快照、详情与手机号分页查询。
- [x] L2：选择 L2-11 最简鉴权、L2-12 自动化测试。

我实现了校园市集的商品与订单后端，支持商品发布、组合筛选、详情、上下架、事务下单及订单查询；固定账号登录后，可通过 Bearer token 使用两个商品写接口。

当前版本 **11 项 JUnit 测试全部通过**；三份 HTTP 脚本分别 8、22、11 项通过，共 41 项检查。指定平台模型负责静态审查。结果与复跑命令见 [SUBMISSION.md](SUBMISSION.md)，实际报告见 [test-evidence](test-evidence/)。

## 2. 我是怎么拆的

| 顺序 | 做的事 | 为什么先做它 |
|---|---|---|
| 1 | 整理 backend 工程、Maven 依赖、包路径，运行 /api/health | 先确认编译、启动、HTTP 和 JSON 能衔接，后面才好区分业务错误与环境错误 |
| 2 | 建 products、orders 两张表，配置 H2 和初始化 SQL | 先确定字段、金额单位和约束，避免写接口时反复改数据结构 |
| 3 | 添加统一响应、业务码和全局异常处理 | 让后续接口共用同一套成功、失败结构 |
| 4 | 实现发布和商品详情 | 先跑通接收参数、校验、INSERT、回读的一条完整链路 |
| 5 | 实现上下架和商品筛选分页 | 在商品基础流程上增加状态更新和查询条件 |
| 6 | 实现下单和订单查询 | 下单依赖已有商品；先把金额、库存和订单的一致性边界说清楚 |
| 7 | 增加异常输入、真实 SQL 失败回滚、分页和库存并发测试 | 正常响应不能证明失败时数据正确，必须检查数据库最终状态 |
| 8 | 加登录和两个商品写接口的 token 校验，再回归旧脚本 | 鉴权基于已有接口；加完要确认合法请求仍正常、非法请求不改数据 |
| 9 | 统一当前两项 L2 的提交范围、文档和实际报告 | 使评审看到的源码、申报项和测试数字一致 |

## 3. 我的程序分成哪几块

| 模块 | 负责什么 | 为什么单独拆 |
|---|---|---|
| controller | HTTP 路径、请求参数、响应状态 | 接口协议与业务规则分开 |
| dto | 请求字段 | 避免把数据库 ID、初始状态和服务端金额当作可随意提交的字段 |
| service | 校验、业务步骤、事务边界 | 下单规则集中在 OrderService，而不是散在 Controller 和 SQL 中 |
| repository | JdbcTemplate SQL、参数绑定、行映射 | 把业务判断与数据库访问分开 |
| model | 商品和订单的查询结果 | 使用 record 表达不可变的数据结构 |
| common | ApiResponse、PageResult、ErrorCode、异常处理 | 各接口复用统一 JSON 和错误处理 |
| config | Jackson 严格类型规则、RequireToken、TokenInterceptor、WebConfig | 类型转换和通用鉴权不重复写进各业务方法 |

如果以后增加退款和优惠券，OrderService 会先变复杂：退款需要处理库存恢复和状态迁移，优惠券会影响金额计算及使用记录。届时应分开定义退款、优惠计算规则及各自数据，再明确与订单操作的事务边界，而不是都塞进 Controller。当前没有实现这些扩展。

### 3.1 核心类怎样协作

| 场景 | Controller | Service | Repository/共享模块 |
|---|---|---|---|
| 发布、详情、列表、上下架 | ProductController | ProductService | ProductRepository、Product、PageResult |
| 下单和订单查询 | OrderController | OrderService | ProductRepository、OrderRepository、Order |
| 登录 | AuthController | AuthService | 内存 token Set，当前不写数据库 |
| 商品写接口鉴权 | 原 ProductController 方法 | AuthService 校验 token | RequireToken、TokenInterceptor、WebConfig |
| JSON 类型不符或业务异常 | 无需每个接口重复 try/catch | 可抛 BizException | JacksonConfig、GlobalExceptionHandler、ApiResponse |

Controller 确定路由和 HTTP 成功状态，Service 决定业务是否允许继续，Repository 负责读写数据。测试从 HTTP 入口检查契约，必要时直接查询数据库核对最终结果。

### 3.2 DTO、模型与统一响应的区别

- CreateProductRequest 和 CreateOrderRequest 只描述客户端可提交的业务字段，不要求客户端提供主键、创建时间或订单总金额。
- Product 和 Order 是完整查询结果，包含数据库生成的字段和业务状态。
- ApiResponse 统一顶层 code/message/data；PageResult 统一 total/page/pageSize/items。
- 使用 record 后对象不可变；Service 对文本做规范化时创建新的 DTO，而不是在原请求对象上逐字段赋值。

DTO 的输入规则和数据库约束各有作用：前者给请求方明确错误，后者在其他写入路径存在时继续保护数据库。两者不是互相替代关系。


## 4. 一次完整链路是怎么走的

以创建订单为例：

```text
POST /api/orders + productId、quantity、买家信息
  -> Jackson 严格转换 DTO，字符串/小数 quantity 等类型错误返回 40001
  -> Controller 调用 Spring 管理的 OrderService.create
  -> @Transactional 开启事务，Service 校验必填、数量和文本长度
  -> SELECT FOR UPDATE 锁定商品行
  -> 商品不存在：40401；下架：40902；库存不足：40901
  -> 从数据库取单价，Math.multiplyExact 计算整数分金额
  -> 溢出返回 40001，此时尚未扣库存
  -> UPDATE ... WHERE stock >= quantity 条件扣库存
  -> INSERT 订单，保存标题、单价和总额快照
  -> 查回订单；Spring 提交事务；Controller 返回 HTTP 201
  -> 数据库异常向外传播时回滚，释放行锁
```

扣库存与插订单是同一事务。行锁让同一商品的并发下单等待，条件 UPDATE 继续保护库存不被扣成负数。代价是热门商品的下单会串行等待，当前规模下选择容易解释和验证的实现。

最简鉴权链路：

```text
POST /api/auth/login + username/password
  -> AuthService 比较固定演示账号 demo/demo123
  -> SecureRandom 生成 32 字节，Base64URL 无填充编码为 token
  -> 保存到进程内并发 Set，返回 data.token

POST /api/products 或 PATCH /api/products/{id}/status
  -> Spring 找到对应 HandlerMethod
  -> TokenInterceptor 发现方法上的 @RequireToken
  -> 无效 Bearer token：抛 BizException，统一返回 401/40101
  -> 有效 token：继续解析请求体、执行业务校验和 SQL
```

其他查询及下单方法不加该注解，保持题目范围内的匿名访问。token 与进程同生命周期，重启后失效，不提供角色或订单归属权限。

### 4.1 下单金额为何只能取数据库

下单只接收 productId 和 quantity 等业务输入，unitPrice 从被锁定商品读取。先用 long 的 Math.multiplyExact 计算 totalAmount，再扣库存，避免乘法溢出后留下库存变化。

例如单价 1990 分、买 2 件，服务器得到 3980 分。请求中即使夹带一个不同价格，也不会成为本方法的计算来源。订单插入时写入 productTitle/unitPrice/totalAmount，使历史订单不跟随商品当前价格变化。

### 4.2 事务为什么必须覆盖扣库存和插订单

```sql
SELECT id, title, price, stock, category, description,
       image_url, status, seller_name, created_at, updated_at
FROM products WHERE id = ? FOR UPDATE;

UPDATE products
SET stock = stock - ?, updated_at = CURRENT_TIMESTAMP
WHERE id = ? AND stock >= ?;
```

第一条语句锁住当前商品，锁保持到事务结束。其他事务如果也修改同一行，需要等待。第二条语句再检查库存条件，Service 核对更新行数，未成功扣减就不插订单。

OrderService.create 是 public 事务方法，由 Controller 通过注入的 Spring Bean 调用。BizException、DataIntegrityViolationException 等运行时异常向外传播，触发默认回滚。Repository 不把数据库异常吞掉后假装成功，Controller 也不提前生成成功响应。

测试会先让 UPDATE 真正执行，再通过 orders 的临时 CHECK 约束强制 INSERT 失败，最终从事务外读取库存和订单数。库存恢复到原值、订单数不变，才是这条失败路径的证明。它比“quantity=0 在写库前失败”验证了不同的事情。

### 4.3 token 校验与业务校验的先后

对于被保护的两个商品写接口，先经过映射方法上的鉴权拦截，token 有效后才进入请求体解析和业务处理。因此未登录时不能通过反复提交非法 body 进入商品写逻辑。成功通过 token 校验也不等于商品参数合法，仍需校验 title、price、stock 或 status。

SecureRandom 生成随机字节；Base64URL 把它变成便于放进 HTTP header 的字符串，不是对账号密码的加密。并发 Set 只保存已经签发的 token。使用 contains 校验时不对 token 本身 strip 或拼接；错误账号密码、未知 token 等失败都不创建商品或修改状态。

### 4.4 失败响应怎样保持一致

| 失败来源 | 处理位置 | 对外结果 |
|---|---|---|
| JSON 非法、整数被写成字符串/小数 | Jackson 转换与 GlobalExceptionHandler | 400/40001，字段路径可识别时指出字段 |
| 缺失查询参数、id/page 类型错误 | Spring 参数绑定与全局处理器 | 400/40001 |
| 空标题、负价格、数量为零等业务参数 | Service 抛 BizException | 400/40001 |
| 缺少/无效 token 或错误账号密码 | AuthService、拦截器与全局处理器 | 401/40101 |
| 商品或订单不存在 | Service | 404/40401 |
| 库存不足、商品下架 | OrderService | 409/40901 或 40902 |
| 不支持的方法/内容类型 | 保留 Spring 已有的 4xx 状态 | 40501/41501 等，仍用统一结构 |
| 未预期服务端异常 | 全局处理器记录日志 | 500/50000，客户端不收到堆栈 |

这让客户端既能用 HTTP 状态识别错误类别，也能用业务码区分库存不足和已下架。日志与响应分开：定位问题需要的异常留在服务端，外部只拿到通用错误提示。


## 5. 一个取舍：我选了 A，没选 B

### H2 内存库与外部数据库

- 选择：H2 内存库。
- 原因：本地和评审不必额外安装、建库、配置外部数据库，能直接启动验证。
- 代价：应用进程退出后数据丢失，手动测试需重新准备商品；不是持久化部署方案。
- 当前做法：schema.sql 随项目提交，通过 file:./schema.sql 初始化，所以运行工作目录必须是 backend。IF NOT EXISTS 不承担迁移旧表结构的职责。

### JdbcTemplate 与 ORM

- 选择：JdbcTemplate。
- 原因：已有基本 SQL 阅读能力，直接看得见 INSERT、条件 UPDATE、分页和行锁语句，便于理解下单事务。
- 代价：SQL 和 RowMapper 需要手写；商品行映射有重复，后续可小范围提取复用。

### 金额和表结构

products 存当前商品数据，orders 存下单时的标题、单价和金额快照。这样商品后来改名改价，旧订单仍能解释当时成交的内容；代价是订单里存在有意保留的冗余字段。

金额用数据库 BIGINT 和 Java long，单位为分，不用浮点数。CHECK 约束保护非负金额、数量和金额关系；应用仍需用 Math.multiplyExact 先检查乘法溢出。数据库约束不能替代 HTTP 参数校验。

updated_at 的默认值只负责插入，更新 SQL 显式维护它。商品只上下架，不提供物理删除，以保留订单外键关联。

### 当前范围

固定账号和进程内 token 满足题目要求的最简鉴权。两个商品写接口按方法注解统一拦截，查询及下单保持公开，职责和保护范围清楚。

当前只申报 L2-11、L2-12，把讲解和验证集中在两项上。常规商品状态/分类索引及订单手机号联合索引继续保留，但不申报索引实验。普通索引对应已有筛选、排序查询，不等于已经完成性能对比实验。

### 5.1 两张表的字段与约束

#### products：当前商品

| 字段 | 类型及规则 | 原因 |
|---|---|---|
| id | BIGINT 自增主键 | 给商品稳定标识，订单通过它关联商品 |
| title | VARCHAR(100)，非空 | 商品标题；Service 先做非空和长度检查 |
| price | BIGINT，非负，单位分 | 避免浮点金额误差 |
| stock | INT，非负 | 数据库继续约束库存下界 |
| category | VARCHAR(50)，非空 | 列表按分类精确筛选 |
| description | VARCHAR(2000)，非空，默认空串 | 可不传的商品说明，避免响应中反复处理 null |
| image_url | VARCHAR(500)，非空，默认空串 | 仅保存可选图片地址，当前不实现文件上传 |
| status | VARCHAR(10)，默认 on，CHECK on/off | 通过上下架表达是否可继续交易 |
| seller_name | VARCHAR(50)，非空 | 按题目保存卖家名称，与商品内容一并返回 |
| created_at / updated_at | TIMESTAMP，默认当前时间 | 记录创建和最近业务更新，更新 SQL 显式维护 updated_at |

#### orders：订单及下单时快照

| 字段 | 类型及规则 | 原因 |
|---|---|---|
| id | BIGINT 自增主键 | 内部标识，也用于相同时间下的稳定排序 |
| order_no | VARCHAR(64)，非空、唯一 | 对外查询标识；应用生成日期加完整 UUID，数据库唯一约束兜底 |
| product_id | BIGINT，非空、外键 | 保证引用的商品存在，不产生无对应商品的订单 |
| product_title | VARCHAR(100)，非空 | 保留成交时的标题 |
| quantity | INT，CHECK 至少 1 | 订单不能为零件或负数 |
| unit_price / total_amount | BIGINT，非负、单位分 | 保存成交单价与服务器算出的总额 |
| buyer_name | VARCHAR(50)，非空 | 题目简化买家信息 |
| buyer_phone | VARCHAR(20)，非空 | 当前订单列表的查询条件，保存和查询统一 strip |
| address | VARCHAR(500)，非空 | 保存下单填写的地址文本 |
| remark | VARCHAR(500)，非空、默认空串 | 可选备注 |
| status | VARCHAR(10)，默认 created，CHECK created/paid | 保留题目规定的状态范围，新订单写入 created |
| created_at / updated_at | TIMESTAMP，默认当前时间 | 用于排序和变更追踪 |
| paid_at | 可空 TIMESTAMP | 按题目保留的时间字段，当前新订单为 null |

额外 CHECK 要求 total_amount=unit_price×quantity。这个约束保护入库数据一致性，但大整数乘法仍需应用提前检查。当前不提供商品物理删除接口，避免历史订单引用被随意移除；外键未设置级联删除。

### 5.2 查询条件与分页的处理

商品列表从固定的 WHERE 条件片段构造查询，用户输入通过问号参数绑定，不能直接成为 SQL 语句的一部分。keyword 中的 !、%、_ 另外做 LIKE 转义，是为了按字面字符搜索；参数绑定与通配符转义解决的是两个不同问题。

同一份筛选条件和参数分别用于 COUNT 与分页 SELECT，避免 total 是全表数量而 items 已筛选。page 至少 1，pageSize 为 1～50；offset 使用 `((long) page - 1) * pageSize`，先转 long 再乘。

排序使用 created_at DESC、id DESC；当创建时间相同，唯一 id 继续确定顺序。这里“稳定”指同一数据集上的确定顺序，不承诺并发新增或删除时 OFFSET 分页不漂移，也不承诺两条查询共享同一个数据库快照。

订单列表按规范化后的 buyerPhone 筛选，保存与查询使用相同 strip 和长度规则，避免首尾空白造成同一查询条件无法匹配。

### 5.3 常规索引与范围控制

products 保留 status、category 索引；orders 的 order_no 唯一约束支持详情查找，另保留 buyer_phone、created_at DESC、id DESC 的联合索引，对应手机号等值筛选和倒序分页。

索引会增加写入维护与空间成本，优化器是否选用还取决于数据分布。本次只说明这些常规索引的用途，不凭索引名字宣称性能收益，也不把它作为 L2-13 申报。

### 5.4 为什么这次只提交两项 L2

最终范围选择最简鉴权和自动化测试，让提交与后续讲解集中在固定账号登录、token 拦截、参数与数据断言。支付流水和完整账户体系会引入更多状态和一致性问题，不扩大当前交付范围。

L1 下单事务与库存保护仍是业务正确性的一部分。保留已有回滚、并发回归测试，是为避免后续修改破坏订单，不额外累计申报 L2 分数。


## 6. 一次实际排错：公共类与源码文件不匹配

### 现象

工程编译提示：BizException.java 中声明了 public 类 HealthController，该类应放在 HealthController.java 中。这个问题使服务无法启动，先解决编译才能继续验证接口。

### 排查过程

1. 先按报错给出的文件路径打开源文件，对照文件名与 public 类名。
2. 继续检查 common 目录下 BizException、ErrorCode 的内容和 package，确认类、文件与包目录是否一一对应。
3. 将错位内容分别恢复到对应文件，统一包声明为 com.example.demo.common，重新编译并请求健康检查。

### 原因与处理

原因是复制代码时文件内容错位，且曾存在包目录不一致。修复集中在源码组织：一个 public 顶层类型对应同名 Java 文件，package 与源码路径保持一致。

随后编译恢复，接口可以继续运行。现在的工程从清理 target 后的源码构建，11 项 JUnit 和 41 项 HTTP 检查通过。

### 另一次 API 适配

异常处理最初参考的 JsonMappingException/getFieldName 不适用于本项目实际 Jackson 3 依赖。核对依赖后改用 DatabindException/getPropertyName，保留字段名提示以及 400/40001 响应。实际 HTTP 输入中，stock 字符串、price 小数和 quantity 类型错误均有对应验证。

## 7. 没做的事和已知问题

| 项 | 当前范围或行为 | 说明 |
|---|---|---|
| 其他 L2 选做 | 本次不申报 | 当前选择最简鉴权、自动化测试两项；支付和索引专项不纳入本次提交 |
| 数据生命周期 | H2 内存库 | 应用进程退出后业务数据清空；重新运行手动流程需准备商品 |
| 鉴权范围 | 两个商品写接口 | 登录 token 保存在内存，重启后重新登录；查询与下单保持公开 |
| 列表查询 | COUNT 和 SELECT 独立执行 | 使用相同筛选条件，不提供跨查询快照读 |
| AI 使用材料 | 使用说明与脱敏日志已整理 | 见 AI_USAGE.md 和 ai-evidence；模型记录已对照备份，费用按本人记录填写 |

### 7.1 我用什么证据判断实现正确

| 验证方式 | 负责发现什么 | 本次结果与边界 |
|---|---|---|
| 默认启动测试 | 应用装配和初始化是否成功 | 1 项通过；不能代替接口业务断言 |
| 订单 HTTP 集成测试 | 金额、快照、错误输入、分页与数据库终态 | 5 项通过；直接用真实数据库与随机端口 |
| 鉴权 HTTP 集成测试 | 登录、header 拒绝、业务校验保留、匿名范围 | 5 项通过；拒绝后还检查商品数据未变 |
| 真实 SQL 失败回滚用例 | 已执行扣库存后发生插单错误能否恢复 | CHECK 强制失败，验证 SQLState 与最终库存/订单数，无外层测试事务 |
| 三份命令行脚本 | 启动后的接口能否按使用方式操作 | 8/22/11 项通过；会新增虚构测试数据 |
| 静态源码审查 | 控制流、约束、事务边界是否合理 | 与运行证据互相补充，不把审查模型的“通过”当成新执行结果 |

JUnit 测试分别使用独立命名的 H2 库和随机 HTTP 端口，@BeforeEach 先删 orders 再删 products，符合外键顺序；测试不套一层外部事务。这样观察的是 Service 自己事务结束后的数据，而不是测试框架最后统一回滚造成的表面正确。

测试重点对应本次的订单必测场景、鉴权行为和数据一致性；接口操作示例、复跑命令与实际报告通过 SUBMISSION 统一索引。


## 8. 如果还有一天

1. **优先增加商品列表组合条件的回归用例。** 覆盖筛选后的 total、空结果、同时间排序和通配符字面搜索，使后续修改查询 SQL 时更容易发现行为变化。
2. **评估持久化运行方式。** 在保持接口不变的前提下验证文件数据库或外部数据库配置，增加重启前后数据检查，方便持续演示和使用。
3. **整理重复行映射。** 提取可复用的 RowMapper，结合现有回归测试验证字段与响应一致，降低后续增加字段时的维护成本。

## 9. 本次实现的几个要点

### 9.1 订单快照与整数金额

当前商品数据和历史订单分开保存。订单记录 product_title、unit_price、total_amount，商品改名或改价后不会重算历史订单。金额从数据库价格出发，使用 long 按分计算，Math.multiplyExact 在扣库存前检查溢出，数据库 CHECK 再约束入库关系。

对应验证：正常下单得到服务器计算的金额；随后修改商品标题和价格，重新查订单仍保留原快照。金额溢出请求返回参数错误，库存不变。

### 9.2 数据库约束与应用事务配合

orders 的商品外键、订单号唯一约束和金额 CHECK 保护数据结构；Service 的事务和行锁保护一次下单中的多条语句。两层分别处理结构规则与跨语句一致性。

订单号为 SO + yyyyMMdd + 32 位 UUID，数据库 UNIQUE 继续限制重复值；不依赖进程内计数器续号。商品只提供上下架，保留已有订单到商品的关联。

### 9.3 参数错误和业务错误有明确语义

Jackson 严格规则拒绝数字字段的字符串、小数和布尔输入，文本字段拒绝数字和布尔转换。Service 处理空白、长度、非负价格和库存等业务规则。全局异常处理统一返回 code/message/data。

例如库存不足返回 40901、商品下架返回 40902、无效 token 返回 40101；JSON 字段路径可识别时，错误信息直接指出 stock、price 或 quantity。客户端可以按状态码和业务码处理，不需要从通用 500 猜原因。

### 9.4 用真实失败验证回滚

订单集成测试使用 Spring 管理的真实 Service 和 H2，通过临时 CHECK 约束令 INSERT 发生数据库错误，核对 SQLState 23513，以及事务结束后的库存恢复和订单数不变。

鉴权测试从真实 HTTP 入口提交缺失或错误的 token，除了检查 401，还核对商品总数、状态、库存没有变化。验证同时关注返回值与最终数据，避免只检查响应文案。
