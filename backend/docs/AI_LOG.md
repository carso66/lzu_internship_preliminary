# AI 开发记录（AI_LOG）

> 本文档是**过程记录**，不是交付物，用于最终整理 `AI_USAGE.md` 时挑选素材。
> 每个阶段完成后追加一条。模型型号与分工已按实际使用确认（DeepSeek-V4-Pro 架构 / DeepSeek-V4.1-Flash 开发 / GLM-5.3 审查）。**调用消耗按模型合计已记录进 `AI_USAGE.md` 概览（合计 ≈37.70 元）**；平台用量导出未按阶段拆分，故阶段级的 `【待核对】` 保留说明性占位，不逐段编造。
>
> Commit 时间取自 `git log --date=format:"%Y-%m-%d %H:%M"`，可直接复核。

---

## P01：需求分析

- **日期与时间**：2026-11-24 上午（P01 文档无独立 commit）
- **对应需求**：全部（`backend/README.md`）、根 `README.md`
- **实际使用模型**：DeepSeek-V4-Pro（架构师）
- **选择该模型的理由**：需求分析要同时对照题目、根 README、前端 mock 三份材料并识别其中的矛盾（比如 mock 的夹断 vs 题目的 400），并据此定出整体边界与验收矩阵——这是后续所有阶段的地基，需要较强的归纳与推理
- **实际提示词摘要**：「读 backend/README.md 和根目录 README，按官方编号整理出接口验收矩阵、硬性要求与自定要求清单、依赖关系、风险分析」
- **提供给 AI 的上下文**：`backend/README.md` 全文 + 根 `README.md` + `frontend/mock/` 目录说明（明确告知 mock 仅供参考，不是后端规范）
- **AI 给出的方案或修改**：产出了 `docs/P01-需求分析.md`，包含 20 条待自定清单、接口验收矩阵、风险分析
- **我的判断及修改**：采纳主体结构；**亲自核对了码表 8 行是否齐全**（确认齐全）；在文档里显式标注了「前端 mock 的 40002/40400 不是后端规范」
- **实际执行的验证命令**：无（纯文档阶段）
- **测试结果**：N/A
- **尚未解决的问题**：`schema.sql` 放 `backend/` 根还是 `src/main/resources`（在 P02 解决）
- **实际调用次数及消耗**：【待核对】
- **对应 Git Commit**：无（P01 文档在 `b2602c6 feat: initialize Spring Boot backend` 前后，未单独提交）

---

## P02：工程骨架与统一响应

- **日期与时间**：2026-11-24 11:18（`b2602c6`）、11:58（`cad9b30`）
- **对应需求**：题目 §3 统一响应结构、§2 技术栈
- **实际使用模型**：DeepSeek-V4-Pro（架构师）定统一响应/异常结构；DeepSeek-V4.1-Flash（开发者）按结构实现
- **选择该模型的理由**：骨架的结构和异常映射属于「架构」范畴，由 V4-Pro 敲定（决定哪些异常映射到哪个码、JSON 严格类型怎么配置）；具体编码由 V4.1-Flash 完成——这是本项目第一次按三角色分工协作
- **实际提示词摘要**：「实现统一响应 `{code,message,data}`、`ErrorCode` 枚举绑定 HTTP 状态、`@RestControllerAdvice` 全局异常处理、`schema.sql` 通过 classpath 加载、`/api/health`」
- **提供给 AI 的上下文**：`backend/README.md` §3、P01 文档里的码表与歧义清单、`pom.xml`
- **AI 给出的方案或修改**：`ApiResponse` record、`ErrorCode` 枚举、`GlobalExceptionHandler`；`schema.sql` 通过 Maven `<resources>` 复制到 classpath
- **我的判断及修改**：采纳。**额外要求它列出所有可能走到兜底 `Exception` 分支的异常类型**——这一步发现了 `HttpMediaTypeNotSupportedException`（text/plain 请求）会被兜底成 500，从而在 P08 补了处理器
- **实际执行的验证命令**：`./mvnw test`（`CommonLayerTest` 23 例）
- **测试结果**：全绿
- **尚未解决的问题**：`createdAt` 输出微秒精度（P08 解决）；`text/plain` 请求返回 50000（P08 解决）
- **实际调用次数及消耗**：【待核对】
- **对应 Git Commit**：`b2602c6`、`cad9b30`

---

## P03：L0 商品模块

- **日期与时间**：2026-11-24 12:05（`bf0dbfc`）、12:29（`a134588`）
- **对应需求**：L0-2 ~ L0-5
- **实际使用模型**：DeepSeek-V4.1-Flash（开发者）
- **选择该模型的理由**：模式固定（Controller/Service/Repository + DTO），题目字段和状态码已给死
- **实际提示词摘要**：「实现商品发布/列表/详情/上下架，三条件叠加筛选，pageSize 1~50，越界报 400」
- **提供给 AI 的上下文**：只给了 `products` 表 DDL + README §4，**没给订单/支付**
- **AI 给出的方案或修改**：完整 CRUD；但 `pageSize` 越界是夹断（`Math.min(50, …)`）
- **我的判断及修改**：**否掉了夹断实现**，改为抛 `BusinessException(BAD_REQUEST)`；理由见 P01 §2.2（mock 夹断 ≠ 后端规范）。补了 `pageSizeOverMax`/`pageSizeZero` 测试
- **实际执行的验证命令**：`./mvnw test -Dtest=ProductListTest,ProductCreateTest,ProductDetailAndStatusTest`
- **测试结果**：87 例全绿
- **尚未解决的问题**：`LOWER(title) LIKE` 是否影响索引（P07 实测）
- **实际调用次数及消耗**：【待核对】
- **对应 Git Commit**：`bf0dbfc`、`a134588`

---

## P08：L0 独立验收 + 异常映射修复

- **日期与时间**：2026-11-24 12:17 前后（`1175a7c fix:修复已发现的遗留问题`）
- **对应需求**：L0 全部已实现部分的验收 + 题目 §3 统一响应
- **实际使用模型**：GLM-5.3（审查者）
- **选择该模型的理由**：审查必须用「没参与开发」的视角，且与架构/开发用不同模型；开新会话、只给题目和代码
- **实际提示词摘要**：「你是没有参与之前开发的后端评审工程师。只根据 backend/README.md 的 L0 要求做只读审查……不要因为代码看起来合理就认定通过」
- **提供给 AI 的上下文**：`backend/README.md` + 当时的 `backend/` 全量代码，无历史对话
- **AI 给出的方案或修改**：实际跑了 `mvn test`（当时 138 例）+ 启动服务 curl 约 40 个场景；指出 3 条问题（`SUBMISSION.md` 空模板、PATCH 无请求体时 message 语义偏、COUNT/SELECT 两条查询的瞬时不一致窗口）
- **我的判断及修改**：第 1 条采纳；第 2 条判断不值得改（code/HTTP 都对）；**第 3 条否掉了**（单机内存库 + 列表弱一致，加事务是过度设计）。同时据它的发现补了 P08 的异常映射修复：`HttpMediaTypeNotSupportedException` → 415/41500，`createdAt` 截断到毫秒
- **实际执行的验证命令**：`./mvnw test -Dtest=CommonLayerTest,ExceptionMappingRegressionTest`
- **测试结果**：23 + 3 例全绿
- **尚未解决的问题**：无（`Accept: xml` 空 body 记录为开放项 F）
- **实际调用次数及消耗**：【待核对】
- **对应 Git Commit**：`1175a7c`

---

## P04/P09：L1-6 下单事务

- **日期与时间**：2026-11-24 13:23（`0c3abf6`）
- **对应需求**：L1-6 核心（7 分）
- **实际使用模型**：DeepSeek-V4-Pro（架构师）
- **选择该模型的理由**：**这是全项目唯二由架构师模型主导的地方之一**。事务方案选错、锁没加对、扣减后回滚失败，都是静默错误——不报错，但会超卖，测试没覆盖到就发现不了。需要一次想清楚时序和失败边界
- **实际提示词摘要**：「比较悲观行锁与条件原子 UPDATE 两种方案，从 7 个维度分析；用并发事务时序证明推荐方案；说明 `@Transactional` 在什么情况下失效」
- **提供给 AI 的上下文**：`schema.sql` 全文 + README §5 L1-6 原文 + 题目给的 6 步固定顺序
- **AI 给出的方案或修改**：推荐条件原子 UPDATE；给了时序证明；给了 `@Transactional` 失效清单（自调用/吞异常/受检异常）
- **我的判断及修改**：**否掉了它的推荐**，选行锁（3 个理由见 DESIGN §5）；**采纳了失效清单**。这是一个「部分采纳」的案例
- **实际执行的验证命令**：`./mvnw test -Dtest=OrderCreateTest,OrderRollbackTest,OrderValidationTest`
- **测试结果**：17 + 2 + 20 = 39 例全绿
- **尚未解决的问题**：H2 上 `FOR UPDATE` 的真实并发行为待验证（P11）
- **实际调用次数及消耗**：【待核对】
- **对应 Git Commit**：`0c3abf6`

---

## P10：L1-7 / L1-8 订单查询

- **日期与时间**：2026-11-24 13:28（`c15e71e`）
- **对应需求**：L1-7、L1-8
- **实际使用模型**：DeepSeek-V4.1-Flash（开发者）
- **选择该模型的理由**：纯查询，不涉及事务
- **实际提示词摘要**：「订单详情按 orderNo 查；列表按 buyerPhone 精确匹配、创建时间倒序、分页；buyerPhone 必填」
- **提供给 AI 的上下文**：`orders` 表 DDL + README §5
- **AI 给出的方案或修改**：初版排序只用了 `ORDER BY created_at DESC`
- **我的判断及修改**：**补了 `, id DESC`**——`created_at` 是 TIMESTAMP，同毫秒批量插入会并列，翻页会重复或漏行。补了 `stableTieBreakBySameTimestamp` 测试（3 条相同时间戳，逐页取，断言序列与整体一致）
- **实际执行的验证命令**：`./mvnw test -Dtest=OrderQueryTest`
- **测试结果**：20 例全绿
- **尚未解决的问题**：无
- **实际调用次数及消耗**：【待核对】
- **对应 Git Commit**：`c15e71e`

---

## P11：L2-10 并发不超卖

- **日期与时间**：2026-11-24 13:42（`8fc8ab4`）
- **对应需求**：L2-10
- **实际使用模型**：DeepSeek-V4-Pro（架构师）定并发方案；DeepSeek-V4.1-Flash（开发者）实现测试代码
- **选择该模型的理由**：并发测试本身容易写成「伪装并发」（顺序发请求、MockMvc 同线程），需要架构师模型理解什么样的测试才算真并发；具体测试代码由 Flash 按方案实现
- **实际提示词摘要**：「用 RANDOM_PORT 真实 Tomcat + JDK HttpClient + 两级 CountDownLatch，10 个并发请求抢库存 1 的商品，断言 1 个 201、9 个 409/40901、库存 0、订单 1 条」
- **提供给 AI 的上下文**：`OrderService`、`OrderRepository` 全文 + L2-10 验收口径
- **AI 给出的方案或修改**：测试结构正确（真实 HTTP、不让异常静默）
- **我的判断及修改**：基本采纳；**额外要求它加 `@RepeatedTest(5)`**——单次通过不等于稳定，5 轮排除偶然
- **实际执行的验证命令**：`./mvnw test -Dtest=ConcurrentOrderTest`
- **测试结果**：5 轮全绿，每轮 1×201 + 9×409
- **尚未解决的问题**：无（H2 锁语义由这次测试反证为「正常工作」）
- **实际调用次数及消耗**：【待核对】
- **对应 Git Commit**：`8fc8ab4`

---

## P12：L2-9 模拟支付

- **日期与时间**：2026-11-24 13:55（`21b2624`）
- **对应需求**：L2-9
- **实际使用模型**：DeepSeek-V4-Pro（架构师）定幂等方案；DeepSeek-V4.1-Flash（开发者）实现
- **选择该模型的理由**：支付逻辑不复杂，但幂等设计需要一点判断力（应用层锁 + DB 约束），由架构师定方案、开发者实现——分工与 P04/P09 一致
- **实际提示词摘要**：「实现模拟支付，订单不存在 404，已支付 409 40903，金额取订单总额，新增 payments 表，幂等」
- **提供给 AI 的上下文**：`orders` 表 DDL + L2-9 原文 + `OrderRepository` 已有的 `findByOrderNoForUpdate`
- **AI 给出的方案或修改**：初版 `amount` 从请求体读；`payments` 无唯一约束
- **我的判断及修改**：**两处都否掉**——`amount` 改为 `order.totalAmount()`，请求体不定义 amount 字段；`payments.order_no` 加 UNIQUE。形成「应用层锁 + DB 约束」双保险
- **实际执行的验证命令**：`./mvnw test -Dtest=PaymentTest,PaymentRollbackTest,ConcurrentPaymentTest`
- **测试结果**：10 + 1 + 5 = 16 例全绿
- **尚未解决的问题**：无
- **实际调用次数及消耗**：【待核对】
- **对应 Git Commit**：`21b2624`

---

## P13：独立代码审查（AI 做验证）

- **日期与时间**：2026-11-24 14:14 前后（`8d75326` 之后）
- **对应需求**：全部（质量把关）
- **实际使用模型**：GLM-5.3（审查者）
- **选择该模型的理由**：审查的价值是「没参与开发」的视角。**必须开新会话、只给题目和代码**，不能在实现它的同一个上下文里审；且刻意与架构/开发用不同模型
- **实际提示词摘要**：「你是没有参与此前开发的 Java 后端评审工程师。以 backend/README.md 为需求依据只读审查。优先检查 P0 事务失效/数据不一致/库存超卖/重复支付；P1 接口契约/金额溢出/非法 JSON 类型绕过/分页错误。每个问题必须给文件位置、违反的要求、最小触发、实际影响、复现方法、最小修复。禁止修改文件，禁止凭空捏造」
- **提供给 AI 的上下文**：整个 `backend/`（浅层遍历）+ 题目全文，不含任何此前的对话
- **AI 给出的方案或修改**：`mvn test` 全量 231 例 + 启动服务 curl 若干场景 + 自己跑了 10 并发复核；指出文档问题 3 条、字符串字段数字强转边界 1 条
- **我的判断及修改**：
  - 文档 3 条（DESIGN 空模板、SUBMISSION 未更新 L2-9、AI_USAGE 半空）→ **全部采纳并修复**（就是 P14）
  - 字符串字段强转 → 实测确认，**判断为接受而非修**（不影响金额/库存，题目未明确要求）
- **实际执行的验证命令**：`./mvnw clean test`（我在它之后亲自跑了一遍确认）
- **测试结果**：231 例全绿，`BUILD SUCCESS`，18.458 秒
- **尚未解决的问题**：L2-13 索引专项未按模板成文（已在 DESIGN §7 记录）
- **实际调用次数及消耗**：【待核对】
- **对应 Git Commit**：`8d75326`（审查后补测试）；文档修复在 P14 的 commit

---

## P14：三份文档

- **日期与时间**：2026-11-24 15:00 前后
- **对应需求**：DESIGN（15 分）、AI_USAGE（20 分）、SUBMISSION
- **实际使用模型**：DeepSeek-V4.1-Flash（开发者）
- **选择该模型的理由**：文档整理是「素材重组」，素材已分散在 `docs/P01-需求分析.md`、`docs/决策与遗留问题.md` 和 git 历史里，不需要新推理
- **实际提示词摘要**：「按官方 DESIGN 八部分模板重写 DESIGN.md，重点写事务设计；AI_USAGE 按四个场景 + 一次被带偏组织；SUBMISSION 保证评审复制命令能跑」
- **提供给 AI 的上下文**：`docs/P01-需求分析.md`、`docs/决策与遗留问题.md`、`git log`、已有三份文档模板
- **AI 给出的方案或修改**：三份文档成稿
- **我的判断及修改**：核对所有数字与 git 历史一致（231 例、18.458 秒取自我亲自执行的 `clean test`）；**AI_USAGE 里需要平台日志的字段保留 `【需核对平台日志】` 标记**，没有编造数字
- **实际执行的验证命令**：`./mvnw clean test`
- **测试结果**：231 例全绿
- **尚未解决的问题**：`AI_USAGE.md` 概览/模型选型的具体型号与花费需对照平台日志填写
- **实际调用次数及消耗**：【待核对】
- **对应 Git Commit**：待提交

---

## P15：L2-11 鉴权 + L2-13 索引专项（提交前补全）

- **日期与时间**：2026-11-24 下午（P14 之后）
- **对应需求**：L2-11、L2-13；同时适配受鉴权影响的全部既有测试
- **实际使用模型**：**Claude Code（Anthropic，本机 CLI，不在学校平台白名单内）**——如实记录：本阶段不是通过平台的 DeepSeek/GLM 完成的，平台日志中不会有这一段的调用记录，也不占用 40 元额度。这样记是为了与平台日志保持一致，不虚报
- **任务提示摘要**：「把 L2 剩下的也全部完成」（L2-11 最简鉴权、L2-13 索引专项成文）
- **AI 给出的方案或修改**：
  - 鉴权链路：`auth/` 包（登录 Controller/Service、SecureRandom token、HandlerInterceptor），拦截器抛 `BusinessException` 复用统一异常出口，401 也是 `{code,message,data}`；
  - 保护范围严格按题目原文：只拦 `POST /api/products` 与 `PATCH /api/products/{id}/status`（按方法过滤，`GET /api/products` 列表保持公开）；
  - 业务码 40101（缺头）/ 40102（无效或格式错），写进 SUBMISSION；
  - 全部既有商品/流程测试补「真实登录拿 token」后重跑；
  - L2-13：`IndexExplainTest` 用 EXPLAIN ANALYZE 做加索引前后对比（自带 2 万行数据构建），实测 scanCount 20001 → 1001，并**如实记录 H2 不沿索引序提前终止 LIMIT（没有免排序收益）**；结论整理成 DESIGN 附录 A
- **我的判断及修改**：要求 401 也走统一结构（不手写响应体）；要求测试覆盖「两次登录 token 不同且都可用」与「公开接口不受影响」；索引实验必须如实记录 H2 的排序限制，不许写成「加了索引就免排序」
- **实际执行的验证命令**：`./mvnw clean test`
- **测试结果**：**242 例全绿**（新增 AuthTest 9 例 + IndexExplainTest 2 例），`BUILD SUCCESS`
- **尚未解决的问题**：无（`AI_USAGE.md` 概览的调用消耗已按平台用量页填入：三模型合计 ≈37.70 元；平台未导出调用次数与阶段级明细，已在文中如实说明）
- **实际调用次数及消耗**：非平台工具，不占额度；本机会话计数未导出
- **对应 Git Commit**：待提交

---

## 踩过的坑汇总（供 AI_USAGE 第 4 节素材）

| # | 坑 | 发现阶段 | 根因 | 状态 |
|---|---|---|---|---|
| 1 | `new Jackson2ObjectMapperBuilder().build()` 静默丢弃 `spring.jackson.*` 配置 | P04/P08 | 手工新建的 ObjectMapper 不继承 Boot 配置 | 已修（改 SimpleModule bean） |
| 2 | `text/plain` 请求被兜底成 50000 | P08 | 缺 `HttpMediaTypeNotSupportedException` 处理器 | 已修（415/41500） |
| 3 | `createdAt` 输出微秒，JS 解析丢精度 | P08 | H2 TIMESTAMP 存微秒 | 已修（截断到毫秒） |
| 4 | `pageSize` 越界被夹断而非报 400 | P03 | AI 照搬了前端 mock 的行为 | 已修（抛 40001） |
| 5 | 排序只按 `created_at` 翻页会重复/漏行 | P10 | 同毫秒并列无确定次序 | 已修（追加 `id DESC`） |
| 6 | 支付金额取自请求体 + 无唯一约束 | P12 | AI 初版实现 | 已修（取订单金额 + UNIQUE） |
| 7 | `LOWER(title) LIKE` 是否影响索引 | P07 | 疑虑 | 实测排除：`LIKE '%kw%'` 本身就用不上 B 树，有无 `LOWER` 都是全表扫描 |
| 8 | 加鉴权后既有 100+ 用例全部 401 | P15 | 拦截器在 Controller 之前执行，测试没带 token | 已修：测试统一真实登录拿 token；401 拒绝路径单列 AuthTest |
| 9 | 「索引序 = 查询序就应该免排序」的想当然 | P15 | 引擎能力边界 | 实测 H2 不提前终止 LIMIT（scanCount 1001 而非 11），如实记录而非美化成免排序 |
