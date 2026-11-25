# 提交说明（SUBMISSION）

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 待本人填写 |
| 学号 | `320250945481` |
| 方向 | 后端 |
| 分支 | `320250945481` |
| 实际投入 | 待本人填写（以实际记录为准） |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Linux（本次验证环境为 WSL2 / Ubuntu） |
| 运行时 | JDK 17 或更高；本次使用 JDK 21.0.13，编译目标为 Java 17 |
| 框架 | Spring Boot 3.5.16、Spring MVC、JdbcTemplate、Flyway |
| 数据库 | H2（运行时文件库，测试时内存库），无需安装数据库服务 |
| 包管理器 | Maven Wrapper 3.10.1（`mvnw`），首次运行会下载依赖 |

## 启动方式（必填，要能照抄）

在项目根目录执行：

```bash
cd backend
./mvnw spring-boot:run
```

Windows 可使用 `mvnw.cmd spring-boot:run`。如果本机已安装 Maven，也可将 `./mvnw` 换成 `mvn`。

启动后访问：

```bash
curl http://localhost:8080/api/health
# {"code":0,"message":"ok","data":null}
```

- 默认端口为 **8080**，服务地址为 `http://localhost:8080`。
- 首次启动时 Flyway 自动执行 `src/main/resources/db/migration/` 中的 V1、V2 迁移并建表，**不需要手工执行 `schema.sql`**。`schema.sql` 是与当前迁移一致的手写完整结构快照。
- 运行数据保存在 `backend/data/marketplace.mv.db`，重启不会自动清空。仓库不预置业务数据，商品需通过 `POST /api/products` 创建。
- `GET /h2-console` 提供 H2 控制台；JDBC URL 为 `jdbc:h2:file:./data/marketplace`，用户名 `sa`，密码为空。该配置用于本地作业，不建议暴露到公网。

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | **5 / 5**：建表、商品发布、组合筛选分页列表、商品详情、商品上下架 |
| L1 | **4 / 4**：下单（事务 + 服务端算价）、订单详情、买家手机号订单列表、参数校验 |
| L2 | 完成 **L2-10 并发不超卖**、**L2-12 自动化测试** |

已实现接口：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| POST | `/api/products` | 发布商品，成功 201 |
| GET | `/api/products` | `keyword`、`status`、`category` 可组合，默认 `page=1&pageSize=10` |
| GET | `/api/products/{id}` | 商品详情 |
| PATCH | `/api/products/{id}/status` | 商品上下架 |
| POST | `/api/orders` | 下单，成功 201 |
| GET | `/api/orders/{orderNo}` | 按订单号查询详情 |
| GET | `/api/orders` | 必须传 `buyerPhone`，支持分页，按创建时间及 ID 倒序 |

所有已实现接口的成功及业务失败响应均包含 `code`、`message`、`data`。金额全程为整数分。未实现支付和鉴权；虽然数据库已有预留的 `payments` 表，但没有 `POST /api/payments`，不申报 L2-9。

## 测试 / 自检

在 `backend/` 下运行全部测试：

```bash
./mvnw clean test
```

本次实际结果（2026-11-24）：

```text
Tests run: 115, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

其中商品接口 77 条、订单接口 31 条、真实 HTTP 并发 1 条、事务回滚 1 条、数据库迁移 3 条、健康检查和应用上下文各 1 条。测试使用独立 H2 内存库，不读写 `backend/data/` 中的运行数据。

核心覆盖包括：

- 正常下单后库存减少，单价和总额取数据库商品价格；客户端附带的价格、状态、订单号不会覆盖服务端结果。
- `quantity=0`、负数、字符串、小数、布尔值和缺失字段返回 400 / 40001。
- 商品或订单不存在返回 404 / 40401；库存不足返回 409 / 40901；商品下架返回 409 / 40902。
- 商品筛选叠加、模糊匹配通配符转义、分页默认值与范围、订单手机号精确匹配和稳定倒序。
- 订单插入被强制异常时，已扣库存真实回滚。

### L2-10 并发测试：准备、运行、核对

**数据准备**：无需启动应用或手工建数据。`OrderConcurrencyTest` 自动在独立的 H2 内存库中插入一个 `status='on'`、`stock=1` 的商品，然后创建 10 个客户端线程。每个线程通过 JDK `HttpClient` 向随机端口上真实运行的 Spring MVC 服务发送 1 个购买 1 件的 HTTP 请求。

单独运行：

```bash
cd backend
./mvnw -Dtest=OrderConcurrencyTest test
```

**核对方式**：测试使用 `ready` / `start` 两个闩锁让 10 个线程就绪后同时放行，等待全部 HTTP 响应后再自动断言：

1. 恰好 **1 个响应为 HTTP 201**，业务码为 0。
2. 其余 **9 个响应均为 HTTP 409**，业务码为 40901、消息为“库存不足”。
3. 最终商品库存为 **0**，该商品本轮订单数为 **1**，订单总额为 1000 分。

任一项不满足测试都会失败，不需要人工读取临时数据库。命令显示 `Tests run: 1, Failures: 0, Errors: 0` 即通过；摘要也可查看 `target/surefire-reports/com.lzu.marketplace.order.OrderConcurrencyTest.txt`。测试结束清理内存库数据，可直接重复运行。

### 事务回滚专项测试

```bash
./mvnw -Dtest=OrderTransactionTest test
```

测试没有在外层添加 `@Transactional`，通过 Spy 强制 `OrderRepository.insert` 抛出数据库异常，然后在独立数据库连接中确认库存仍为 5、订单数为 0，避免外层测试事务掩盖真实提交/回滚行为。

## 没做完的地方

| 项 | 状态 | 原因 |
|---|---|---|
| L2-9 支付接口 | 未实现 | 本次选择 L2-10、L2-12；支付表和订单支付状态仅作结构预留 |
| L2-11 鉴权 | 未实现 | 本次优先保证商品、订单及并发正确性 |
| L2-13 索引对比说明 | 未申报 | 已有订单查询索引，但尚未完成同数据的索引前后 `EXPLAIN` 对照 |
| `AI_USAGE.md` | 待本人补充 | 调用次数、费用及具体交互必须与平台记录一致，不能由代码推断 |

## 自检清单

- [x] 已从 `backend/` 执行 `./mvnw clean test`，115 条测试通过。
- [x] 未提交 `target/`、本地 H2 数据文件或 `.env`；根 `.gitignore` 的 `*.db` 可匹配 H2 的 `.mv.db` 文件。
- [x] `DESIGN.md` 已按当前实现填写。
- [ ] `AI_USAGE.md` 仍需本人结合平台日志填写完整。
- [x] `backend/README.md` 题目文件未修改。
