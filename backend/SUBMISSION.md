# 提交说明（SUBMISSION）

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 周义浩 |
| 学号 | 220250947201 |
| 方向 | 后端 |
| 分支 | `220250947201` |
| 实际投入 | 11小时 |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Windows 11，amd64 |
| 运行时 | **JDK 21.0.12.1（Oracle）** 运行 Maven 与该服务；`pom.xml` 目标版本 `java.version = 17`（release 17 编译，17+ 均可运行） |
| 包管理器 | **Apache Maven 3.10.0**（仓库也自带 `mvnw` / `mvnw.cmd`） |
| 框架 | **Spring Boot 4.0.10**（Spring Framework 7.0.11 / Tomcat 11.0.27） |
| JSON | **Jackson 3.1.8**（包名 `tools.jackson.*`，不是 Jackson 2） |
| 持久层 | MyBatis 3.5.19 + mybatis-spring-boot-starter 4.0.1 |
| 数据库 | H2 2.4.240（文件模式 `./data/marketplace`，启动时由 `schema.sql` 自动建表） |

> `application.yaml` 中只有本地开发库的账号与密码，未写入仓库文档；`/data/`、`*.mv.db`、`*.trace.db` 已在 `.gitignore` 忽略。

## 启动方式（必填，要能照抄）

```bash
cd backend

# 启动（工作目录必须是 backend/，因为 schema-locations 是 file:./schema.sql）
mvn spring-boot:run
# 或使用仓库自带 wrapper：
#   Windows: mvnw.cmd spring-boot:run
#   macOS/Linux: ./mvnw spring-boot:run

# 健康检查：浏览器或 curl 打开 http://localhost:8080/api/health
# 期望：{"code":0,"message":"ok","data":null}
```

首次启动会创建 `backend/data/marketplace.mv.db` 与 `products` / `orders` 两张表；重复启动不会清数据（`schema.sql` 全量 `IF NOT EXISTS`，`spring.sql.init.mode=always`）。

### 接口使用示例（可直接复制执行）

> 适用终端：**bash / Git Bash / WSL / macOS / Linux 终端**（下面用的是单引号包 JSON 与续行符 `\`）。
> 如在 **PowerShell** 里执行，请把续行符 `\` 换成反引号 + 换行，或把整条命令写在一行；JSON 用单引号包裹在 PowerShell 里也可直接工作。
> 若首次执行前库里已有商品，`POST /api/products` 返回的 `id` 会不同于下文的 `97`，请用实际返回的 `id` 替换示例中的 `97`。

```bash
# 1. 健康检查
curl -s http://localhost:8080/api/health

# 2. 发布商品（L0-2）→ 201，price 单位为分；返回体里的 data.id 就是后续要用的 productId
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"title":"测试商品","price":1990,"stock":10,"category":"文具","description":"备注","sellerName":"校园数码铺"}'
# 实测返回：
# {"code":0,"message":"ok","data":{"id":97,"title":"测试商品","price":1990,"stock":10,"category":"文具",
#  "description":"备注","status":"on","sellerName":"校园数码铺","createdAt":"2026-11-26 01:24:15"}}
# ↓ 下面统一用这个真实返回的 id = 97

# 3. 商品列表（L0-3）：keyword 模糊、category/status 精确、可叠加，page/pageSize 默认 1/10（1~50）
curl -s "http://localhost:8080/api/products?page=1&pageSize=10"
curl -s --get --data-urlencode "keyword=测试" http://localhost:8080/api/products
curl -s "http://localhost:8080/api/products?status=on&pageSize=5"

# 4. 商品详情（L0-4）
curl -s http://localhost:8080/api/products/97

# 5. 下单（L1-6）→ 201：商品必须是 on 状态；unitPrice/totalAmount 由服务端按数据库价格计算
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":97,"quantity":2,"buyerName":"张三","buyerPhone":"13800138000","address":"兰州市城关区天水南路222号"}'
# 实测返回：
# {"code":0,"message":"ok","data":{"id":65,"orderNo":"SO20261126012425431906","productId":97,
#  "productTitle":"测试商品","quantity":2,"unitPrice":1990,"totalAmount":3980,"status":"created",
#  "buyerName":"张三","buyerPhone":"13800138000","address":"兰州市城关区天水南路222号",
#  "paidAt":null,"createdAt":"2026-11-26 01:24:25"}}

# 6. 订单详情（L1-7）：用上一步返回的真实 orderNo
curl -s http://localhost:8080/api/orders/SO20261126012425431906

# 7. 订单列表（L1-8）：按手机号精确查询，创建时间倒序
curl -s "http://localhost:8080/api/orders?buyerPhone=13800138000&page=1&pageSize=10"

# 8. 上下架（L0-5）——注意顺序：必须放在下单之后，先下架再下单会返回 409 / 40902
curl -s -X PATCH http://localhost:8080/api/products/97/status \
  -H "Content-Type: application/json" -d '{"status":"off"}'
# 实测返回：{"code":0,"message":"ok","data":{"id":97,"status":"off"}}

> `keyword` / `category` 含中文时请做 UTF-8 URL 编码（上面用了 `--data-urlencode`）；把未编码的 GBK 字节直接塞进 URL 会得到 `400 / 40001`（不会被误判成 500）。

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | **做了 5 / 5 条**（L0-1 建表、L0-2 发布、L0-3 列表、L0-4 详情、L0-5 上下架） |
| L1 | **做了 4 / 4 条**（L1-6 下单、L1-7 详情、L1-8 列表，以及贯穿其上的参数校验） |
| L2 | **做了第 10 项（并发不超卖）与第 12 项（自动化测试）**；第 9 项支付、第 11 项鉴权、第 13 项索引专项未选做 |

真实验收数据（本轮实测）：

| 项 | 结果 |
|---|---|
| Maven 全量自动化测试 | **147 条，全部通过**（`Tests run: 147, Failures: 0, Errors: 0, Skipped: 0`） |
| L0 真实 HTTP 验收（`scripts/verify-l0.ps1`） | **57 / 57 PASS** |
| L1 真实 HTTP 验收（`scripts/verify-l1.ps1`） | **42 / 42 PASS** |
| L2-10 并发实验 | **10 轮，每轮 10 个有效请求：恰好 1 个 201 成功、9 个 409/40901；最终库存 0；每轮恰好新增 1 条订单** |

## 测试 / 自检

```bash
cd backend

# 全量自动化测试（使用隔离内存库，不会触碰 data/marketplace）
mvn test
```

```
[INFO] Tests run: 147, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

测试类与用例数（`mvn test` 实际输出）：

| 测试类 | 用例数 | 覆盖内容 |
|---|---|---|
| `SchemaInitTests` | 4 | L0-1 两表、products 列布局、关键 CHECK/UQ/FK、订单索引 |
| `L02CreateProductApiTest` | 30 | 发布商品全部边界（含参数化空标题、非法 JSON） |
| `L03ListProductsApiTest` | 26 | 列表/筛选/组合/分页边界/分页溢出 |
| `L04ProductDetailApiTest` | 8 | 详情正常、下架、不存在、非法 ID |
| `L05UpdateStatusApiTest` | 23 | 上下架全流程 + 详情/列表同步 |
| `L1OrderSmokeTest` | 21 | 下单、算价、扣减、404/40901/40902、校验、详情、列表分页排序、SQL 注入 |
| `L1OrderBoundaryTest` | 4 | 订单号长度约束、客户端改价被拒、快照价冻结、跨页稳定排序 |
| `L1TransactionBoundaryTest` | 4 | `@Transactional` 代理生效、失败请求不扣库存、买空库存边界 |
| `L1TransactionRollbackTest` | 1 | 插订单失败 → 库存回滚（含「异常发生在扣减之后」的前置证明） |
| `L210ConcurrentOrderTest` | 10 | 并发不超卖，10 轮（`@RepeatedTest(10)`） |
| `CommonApiRegressionTest` | 9 | health、统一结构、405、SQL 注入、LIKE 转义、不泄露内部信息 |
| `HttpEndToEndTest` | 3 | 随机端口真实 Tomcat：中文查询、非法编码 400、路由状态码 |
| `GlobalExceptionHandlingTest` | 3 | 500 不泄露堆栈、非法参数不 5xx |
| `MarketplaceApplicationTests` | 1 | 上下文加载 |
| **合计** | **147** | |

### L2-12 三项必需测试对应的方法名

官方要求的三项，对应 `src/test/java/com/zyh/marketplace/L1OrderSmokeTest.java` 中的：

| 官方要求 | 测试方法 | 断言 |
|---|---|---|
| 正常下单后**库存减 1** | `normalOrderDecreasesStockByOne()` | 库存 10 的商品 `quantity=1` 下单 → HTTP 201 / code 0，库存 **10 → 9**，订单落库且 `unitPrice = totalAmount = 1990` |
| 库存不足返回 **HTTP 409** | `insufficientStockRejected()` | 库存 10 买 11 件 → HTTP **409** / code **40901**，库存不变、订单不落库 |
| `quantity=0` 返回 **HTTP 400** | `invalidQuantityRejected()` | `quantity` 为 `0` / `-1` / `"2"` / `1.5` / `true` / 缺失 → HTTP **400** / code **40001** |

### 并发测试（L2-10）复现方式

```bash
cd backend
mvn test -Dtest=L210ConcurrentOrderTest
```

**如何准备「库存为 1 的商品」**：测试自动准备——每一轮（共 10 轮）先清空测试库，再通过**真实 HTTP** 调用 `POST /api/products` 创建全新商品（`stock=1`、默认 `status=on`），并断言初始库存确为 1，因此不依赖任何历史数据、可重复执行。

**如何核对结果**：测试在每轮内断言并打印，以下全部满足才算 PASS：

1. **响应**：10 个请求（`quantity=1`，共用 `CountDownLatch` 起跑门同时发出）中恰好 1 个为 `HTTP 201 + code 0`，恰好 9 个为 `HTTP 409 + code 40901`，不允许出现其他状态码或异常；
2. **最终库存**：`SELECT stock FROM products WHERE id = ?` 必须为 `0`；
3. **订单数量**：`SELECT COUNT(*) FROM orders WHERE product_id = ?` 必须恰好为 `1`，且其 `order_no` 与成功响应里的 `orderNo` 一致、`total_amount = 单价 × 1`；
4. **同步起跑机制**：10 个请求提交到固定 10 线程池，全部 `await` 同一个 `CountDownLatch` 起跑门，主线程 `countDown()` 一次放行，因此 10 个请求是**同时发出**的，不是串行伪装成并发；测试会断言起跑门确实生效（若请求分散发出则该轮判定失败）。单轮耗时数据受机器负载影响、未做统一口径统计，本文不引用耗时结论，只引用可复现的「响应分类 + 库存 + 订单数」结果。

`@AfterAll` 会打印 10 轮汇总表（轮次、初始库存、并发数、201 数、40901 数、其他数、最终库存、新增订单数、订单号核对、PASS/FAIL）。

### 真实 HTTP 验收脚本（可选）

```powershell
# 先以独立文件库启动服务（不碰 ./data/marketplace）
mvn -DskipTests package
java -jar target/marketplace-0.0.1-SNAPSHOT.jar --spring.config.additional-location=file:./config/verify.yaml

# 另开一个终端（PowerShell 5.1 需按 UTF-8 载入脚本）
powershell -NoProfile -Command "& ([ScriptBlock]::Create([IO.File]::ReadAllText('scripts\verify-l0.ps1',[Text.UTF8Encoding]::new(`$false))))"
powershell -NoProfile -Command "& ([ScriptBlock]::Create([IO.File]::ReadAllText('scripts\verify-l1.ps1',[Text.UTF8Encoding]::new(`$false))))"
```

## 自定义规则（题目未规定、由我决定的部分）

| 项 | 我定的规则 | 说明 |
|---|---|---|
| 业务码 `40004` | HTTP **405**（请求方法不支持），如 `DELETE /api/products/1` | 不用 `40001`：请求格式没错，只是该方法不被支持；选 4 开头而非 5 开头，让调用方仅凭首位就知道「是客户端错误、不要重试」 |
| 时间格式 | 响应中 `createdAt` / `paidAt` 为 `yyyy-MM-dd HH:mm:ss` 字符串 | 与题目示例的 ISO 风格不同，选择可读性更高的固定格式；`paidAt` 当前恒为 `null`（未做支付） |
| 分页上限 | `page ≥ 1`，`pageSize` **1~50**，`page ≤ 10 000 000`，且 `offset = (page-1)*pageSize ≤ Integer.MAX_VALUE` | `pageSize` 上下限为题目规定；`page` 上限是防御性规则，防止偏移量过大 |
| 关键词匹配 | `keyword` 对标题模糊匹配，并对 `%`、`_`、`\` 转义（`ESCAPE '\'`） | 避免用户输入 `%` 变成「匹配全部」 |
| 手机号 | 下单与列表查询**共用**一个规则：去空白后必须是 1~20 位纯数字 | 两者一致，杜绝「能下单却查不到」；不强制 11 位，以兼容带区号的固话 |
| 订单号 | `SO` + `yyyyMMddHHmmss` + 至少 6 位单调序号（实测 22~24 字符，列宽 32） | 序号进程内单调，唯一性由 `UNIQUE(order_no)` 兜底 |
| 列表排序 | 商品、订单列表统一 `created_at DESC, id DESC` | 用唯一字段 `id` 兜底，保证同时间记录顺序固定、翻页不重不漏 |
| 未声明字段 | 请求体出现未定义字段（如下单时传 `price`）→ `400 / 40001` | 防止客户端越权指定价格，同时提示字段名拼写错误 |
| 错误路径 | 无法识别的路径 → `404 + 40401`；非法 URL 编码 → `400 + 40001`（不是 500） | 统一响应结构在错误路径上也成立 |

## 没做完的地方

| 项 | 状态 | 原因 |
|---|---|---|
| L2-9 支付链路 `POST /api/payments` | 未做 | 按计划只做 L0、L1、L2-10、L2-12；`payments` 表未建，订单 `status` 只会是 `created`，`paid_at` 恒为 null |
| L2-11 最简鉴权 `POST /api/auth/login` | 未做 | 商品发布与上下架当前无需 token |
| L2-13 索引专项（EXPLAIN 前后对比） | 未做 | 索引 `idx_orders_buyer_phone_created_at` 已随建表创建并服务 L1-8，但没有执行计划对比证据 |
| 订单取消 / 退款 | 未做 | 题目无对应接口，状态只保留 `created`/`paid` |
| 更高并发度（如 100 线程） | 未测 | 官方场景为 10 并发；H2 连接池上限制约 |
| 订单号跨实例唯一性 | 已知限制 | 多实例同秒重启可能撞唯一约束 → 该次下单失败返回 50000（不会产生重复订单）；单实例无此问题 |
| 订单号序号不按天重置 | 已知限制 | 序号来自进程内 `AtomicLong`，形如 `SO20261126004239919909`，可读性弱于「日期 + 当日序号」 |

## 自检清单

- [x] 按上面的命令能启动服务并跑通 `/api/health`、商品与订单接口（本轮已重复验证）
- [x] 没提交 `target` / `*.db` / `data/` / `data-verify/` / `.env`（`.gitignore` 已覆盖 `/data/`、`/data_backup/`、`*.mv.db`）
- [x] `DESIGN.md`、`AI_USAGE.md`、`SUBMISSION.md` 已按官方模板填写完整
- [x] `mvn test` 全部通过（147 / 147），测试使用隔离内存库，不修改 `data/marketplace`
- [ ] 截图（仅前端方向需要，本方向不适用）
