# 设计说明（L0＋L1＋L2 鉴权与测试）

## 1. 我做到了哪一步

- L0：已实现建表、发布商品、商品列表、详情和上下架。
- L1：下单、事务扣库存、服务端算价、订单快照、详情和手机号分页查询已实现。
- L2：仅申报 L2-11 最简鉴权、L2-12 自动化测试。当前提交共 11 项 JUnit 测试；支付和索引实验不在本次范围。

目前提供商品、订单和最简登录后端，商品写接口需要 token；查询及下单允许匿名。L1 原有的事务扣库存与库存保护继续保留。

## 2. 我是怎么拆的

| 顺序 | 实际工作 | 排在这个位置的原因 |
|---|---|---|
| 1 | 整理 Maven 工程位置、导入项目、运行健康检查 | 先确认编译、启动、HTTP 请求和 JSON 返回能够衔接 |
| 2 | 编写商品和订单表，配置 H2 与初始化脚本 | 确定字段和数据约束，准备持久化操作 |
| 3 | 添加统一返回、错误码和全局异常处理 | 让后续接口复用相同的成功与失败结构 |
| 4 | 实现商品发布和详情查询 | 先验证请求、校验、插入和查询的一条完整链路 |
| 5 | 实现上下架 | 在已有商品查询上增加状态校验和更新 |
| 6 | 实现列表、组合筛选和分页 | 最后处理动态条件、筛选后总数与当前页查询 |
| 7 | 根据代码审查调整索引和 JSON 错误提示 | 对发现的问题逐项修正，再补充回归验证 |
| 8 | 实现订单数据类、Repository、Service 和 Controller | 基于已验证商品功能增加订单，明确库存与订单的一致性边界 |
| 9 | 增加真实 SQL 失败回滚、HTTP 边界、分页和并发测试 | 用数据库最终状态验证事务，而不只检查正常响应 |

## 3. 程序分成哪几块

| 模块 | 负责什么 | 单独拆分的原因 |
|---|---|---|
| `controller` | HTTP 路径、接收参数、返回 HTTP 状态 | 与业务规则及 SQL 分开 |
| `dto` | 请求字段 | 发布商品不接收客户端指定的 ID、状态和创建时间 |
| `service` | 参数校验、业务流程、事务边界 | 减少控制器中的业务逻辑 |
| `repository` | JdbcTemplate 参数化 SQL 和行映射 | 统一管理数据库操作 |
| `model` | 查询结果和响应中的商品数据 | 使用 record 表示一份不可变的数据 |
| `common` | ApiResponse、PageResult、业务异常、错误码、全局异常处理 | 复用响应和错误处理 |
| `config` | Jackson 类型转换规则 | 明确拒绝字符串、小数等向整数字段的隐式转换 |

订单沿用分层结构，扣库存与插订单已放入同一个 public Service 事务方法，由 Controller 通过 Spring Bean 调用。退款和优惠券会带来额外状态、金额分配及库存恢复规则，需要进一步明确业务边界，不能全部堆入控制器。

## 4. 一次完整链路

商品发布：

```text
POST /api/products + JSON
  -> Jackson 转换为 CreateProductRequest
  -> 类型不符合：统一返回 400 / 40001
  -> Controller 调用 ProductService.create
  -> 检查必填、长度、价格和库存
  -> 校验失败：抛 BizException，统一返回 400 / 40001
  -> ProductRepository 执行参数化 INSERT
  -> 获取生成的 ID，并查回默认状态和时间
  -> 返回 HTTP 201、code=0、完整商品
```

列表查询中复用同一份 WHERE 条件分别执行 COUNT 和分页 SELECT，保证 total 统计的是筛选后的记录。通过 `created_at DESC, id DESC` 保持相同创建时间下的确定顺序。OFFSET 使用 long 计算，避免 int 乘法溢出。

订单下单：

```text
POST /api/orders
  -> 严格 JSON 类型转换和字段校验
  -> OrderService.create 开启事务
  -> SELECT FOR UPDATE 锁定商品行
  -> 检查存在、on 状态和库存
  -> Math.multiplyExact 计算整数分金额；溢出在扣库存前返回 400
  -> 生成完整 UUID 订单号
  -> 条件 UPDATE 扣库存并维护 updated_at
  -> INSERT 保存订单及商品快照
  -> 按 ID 查回完整订单，提交事务并返回 201
  -> 数据库异常向外传播时事务回滚，锁随事务结束释放
```

在同一事务中锁定商品后，上下架更新及其他下单更新需要等待行锁，从而使本次读取的状态、价格和库存保持到事务结束。条件 UPDATE 继续作为库存保护，不依赖客户端传来的单价或总额。此方案会让同一热门商品的下单串行等待，但逻辑直接，适合本次规模。

最简鉴权：

```text
POST /api/auth/login + username/password
  -> AuthService 校验固定账号 demo/demo123
  -> SecureRandom 生成 32 字节，Base64URL 无填充编码为 token
  -> 存入进程内 ConcurrentHashMap 并发 Set，返回 data.token

POST /api/products 或 PATCH /api/products/{id}/status
  -> Spring 映射到标有 @RequireToken 的方法
  -> TokenInterceptor 校验 Authorization: Bearer <token>
  -> 缺失/无效时抛 BizException，统一返回 401/40101
  -> 有效时继续原业务校验和 SQL
```

按实际映射的 HandlerMethod 注解判断保护对象，避免维护另一套 URL 匹配规则。查询和下单没有该注解，仍可匿名。固定账号与进程内 token 符合本次最简要求，重启后 token 失效；不扩展用户表、角色、到期、退出或 JWT。此设计不提供订单归属权限。

## 5. 设计取舍

### H2 内存库与外部数据库

选择 H2 内存模式，减少评审和本地运行的外部依赖；代价是应用退出后数据丢失。建表 SQL 保留在 `backend/schema.sql`，启动工作目录必须为 `backend/`。当前没有引入数据库迁移工具，`IF NOT EXISTS` 只处理对象已存在，不会自动更新旧表字段。

### JdbcTemplate 与 ORM

选择 JdbcTemplate，直接使用已经能够阅读的 SQL，便于理解参数绑定、分页和后续事务；代价是行映射需要手写。当前商品行映射存在重复，可在后续提取 RowMapper。

### 表结构与金额

商品表保存当前标题、单价、库存和状态。订单表保存下单时的商品标题、单价和总金额快照，后续商品修改不会重算历史订单；集成测试通过修改商品后查询订单验证了这一点。

金额在数据库中使用 BIGINT、Java 中使用 Long/long，单位统一为分。CHECK 限制负值和订单金额一致性。数据库约束不能代替 HTTP 参数校验，也不能避免 BIGINT 乘法溢出；下单已使用 Math.multiplyExact 检查乘法溢出，并在扣库存前返回参数错误。

`updated_at` 默认值只负责插入，更新 SQL 显式维护它。商品只提供上下架，不提供物理删除，保留订单到商品的外键关联。

暂不增加用户表。当前任务使用 sellerName、buyerName、buyerPhone 表示交易信息，完整账户体系会增加当前范围之外的工作。

### 索引

商品保留状态和分类索引；订单使用 `(buyer_phone, created_at DESC, id DESC)` 联合索引，对应已经实现的手机号筛选和倒序查询。所有显式索引定义使用 IF NOT EXISTS。当前提交保留这些常规索引；索引对比实验已移出本次提交范围，不申报 L2-13。

## 6. 实际遇到的排错问题

没有分别记录每个问题的耗时，以下选取实际发生的一次排错，不声称它耗时最长。

- 现象：编译提示 BizException.java 中声明了公共类 HealthController，应放入同名文件。
- 定位：对照文件路径、第一行 package 和 public 类名，检查实际粘贴的内容。
- 原因：HealthController 代码被粘贴进 BizException.java，BizException 代码又被放进 ErrorCode.java；common 文件夹一度也放在了顶层源码目录。
- 修复：恢复每个文件对应的类或枚举，将 common 放入 `com/example/demo/common`，统一包声明。
- 验证：随后继续完成编译和接口运行，逐项使用 curl 验证商品功能。

另一次审查中发现，AI 建议使用的 JsonMappingException 和 getFieldName 不适用于本项目实际 Jackson 3 依赖。当前改为 DatabindException 和 getPropertyName；订单 HTTP 测试实际验证了字符串和小数 quantity 被拒绝并提示字段；L0 专项脚本仍可另行复跑。

## 7. 测试证据与未完成项

本次精简提交版本在隔离副本实际执行 mvn -B -ntp clean test：OrderIntegrationTest 5 项、AuthIntegrationTest 5 项、默认启动测试 1 项，共 11 项全部通过。报告对应当前两表、鉴权和订单版本。TXT 报告和 Maven 摘要保存于 test-evidence，复跑命令见 SUBMISSION.md。

回滚测试在独立 H2 中增加只针对 ROLLBACK_TEST 买家的 CHECK 约束，使真实订单 INSERT 触发 SQLState 23513。测试不加外层事务，通过真实 Spring Service 调用后，检查事务结束后的库存恢复和订单数不变，并在 finally 中删除测试约束。

并发测试自动准备库存为 1 的商品，使用屏障同时释放 10 个真实 HTTP 下单请求，等待完成后检查 1 个成功、9 个库存不足、库存 0 和订单 1 条。该测试是并发最终行为证据，不宣称单独测量了行锁持有时长。

同一版本在随机本地端口 50812 启动后实际运行 L0/L1/L2 三份脚本，分别 8、22、11 项全部通过，共 41 项。默认服务与脚本仍是 8080，本次显式向脚本传入随机端口。L0/L1 自动登录，L2 脚本仅验证鉴权与基本订单流程。原始输出归档于 test-evidence；SQL 失败回滚与库存并发最终状态另由 JUnit 验证。

| 项目 | 当前状态或限制 |
|---|---|
| 支付与支付流水 | 不在当前提交范围，当前仅两张业务表 |
| 身份鉴权和用户表 | 已实现固定账号 token；未做用户表、角色或订单归属权限 |
| 数据持久化 | 内存库在应用退出后清空 |
| 列表并发一致性 | COUNT 和 SELECT 是两次查询，不承诺同一快照 |
| 索引实验 | 不在当前提交范围，不申报 L2-13 |
| AI 使用最终说明 | 后续按真实平台日志整理，本轮不提交本地草稿更新 |

当前空白查询条件行为：纯空白 keyword 不筛选，非空白 keyword 不去除首尾空白；category 精确匹配原值；status 传空字符串返回 400。订单手机号保存和查询都使用 strip，并校验非空及长度不超过 20。

## 8. 后续优先级

1. 提交当前两项 L2 的精简版本，并保持文档与实际测试报告一致。
2. 按真实平台记录完善 AI_USAGE，解释登录 token、401 拦截、订单参数与事务测试。
3. 其他实验保留在本地归档，不扩大此次申报范围。
