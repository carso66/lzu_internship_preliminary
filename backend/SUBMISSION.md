# 提交说明（L0 阶段）

## 基本信息

| 项 | 内容                     |
|---|------------------------|
| 姓名 | 郭子杰；GitHub 账号为 `gzzjj` |
| 学号 | `220250946261`         |
| 方向 | 后端 `[BE]`              |
| 分支 | `220250946261`         |
| 当前范围 | L0 商品管理；L1、L2 尚未完成     |
| 实际投入 | 未单独统计，待本人补充            |

本文件是阶段性提交说明。最终提交前继续完善设计和 AI 使用记录。

## 环境

| 项 | 内容 |
|---|---|
| 操作系统 | Windows；具体版本待本人补充 |
| Java | 编译目标 Java 17；运行需要 JDK 17 或以上，实际运行版本以 `java -version` 为准 |
| 框架 | Spring Boot `4.2.0` |
| 数据访问 | Spring JDBC / JdbcTemplate |
| 数据库 | H2 内存数据库 |
| 构建 | Maven；仓库提供 Maven Wrapper，分发版本 `3.10.1` |

首次构建需要能够访问 Maven 依赖仓库。使用 Maven Wrapper 无需另行安装 Maven。

## 启动方式

以下命令从仓库根目录执行。Windows PowerShell：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

如果已安装 Maven，也可在 `backend/` 中运行：

```text
mvn spring-boot:run
```

必须将 `backend/` 作为运行工作目录，因为初始化配置读取 `file:./schema.sql`。IDE 的运行配置也应使用这个工作目录。

- 服务地址：`http://localhost:8080`
- 健康检查：`GET /api/health`
- 健康检查预期：`{"code":0,"message":"ok","data":null}`
- 商品接口：`http://localhost:8080/api/products`

数据库连接为 `jdbc:h2:mem:market;DB_CLOSE_DELAY=-1`，启动时执行 `backend/schema.sql`。不需要外部数据库。应用进程退出后数据清空，新启动时商品和订单表为空，需要通过发布接口准备商品。

本地 H2 控制台：`http://localhost:8080/h2-console`。

```text
Driver Class: org.h2.Driver
JDBC URL: jdbc:h2:mem:market
User Name: sa
Password: 留空
```

## 完成情况

| 层级 | 当前情况 |
|---|---|
| L0 | 5/5 项已实现：建表、发布商品、列表筛选分页、详情、上下架 |
| L1 | 尚未实现下单和订单查询；订单表已预建 |
| L2 | 尚未完成，不申报挑战项 |

## 已实现接口

| 方法 | 路径 | 行为 |
|---|---|---|
| GET | `/api/health` | 健康检查，200 |
| POST | `/api/products` | 发布商品，成功 201 |
| GET | `/api/products` | 列表、组合筛选和分页，200 |
| GET | `/api/products/{id}` | 商品详情，200；不存在 404 |
| PATCH | `/api/products/{id}/status` | `on` / `off` 上下架，200 |

### 发布商品

示例请求：

```json
{"title":"Notebook","price":1990,"stock":10,"category":"Stationery","description":"","sellerName":"Test seller","imageUrl":""}
```

- `title`、`category`、`sellerName` 必填，去除首尾空白后分别最多 100、50、50 个 Java 字符串长度单位。
- `price` 为非负整数分，使用 `Long/long`；`stock` 为非负整数，使用 `Integer/int`。
- `description`、`imageUrl` 可不传，分别最多 2000、500 个 Java 字符串长度单位，缺失或为 null 时保存空字符串。
- 商品 ID、初始状态 `on` 和时间由服务器/数据库生成。
- 整数字段拒绝字符串、小数和布尔值；文本字段拒绝数字和布尔值。

### 商品列表

- `page` 默认 1，必须至少为 1；`pageSize` 默认 10，范围 1～50。
- `keyword` 标题包含匹配；null 或纯空白时不筛选，非空白关键词保留原始首尾空白；`%`、`_`、`!` 按字面字符处理。
- `status` 仅允许 `on` / `off`；不传表示全部，显式空字符串属于非法状态。
- `category` 精确匹配；不传表示全部，显式空字符串按空字符串查询。
- 三个条件通过 AND 叠加；总数与当前页查询使用相同条件。
- 排序为 `created_at DESC, id DESC`；超出页数返回空数组，`total` 仍是筛选后总数。

示例：

```text
GET /api/products?keyword=Notebook&status=on&category=Stationery&page=1&pageSize=10
```

`data` 包含 `total`、`page`、`pageSize`、`items`。

### 商品上下架

请求 `{"status":"off"}` 或 `{"status":"on"}`。响应返回更新后的完整商品，包含题目要求的 `id`、`status`；价格和库存不因上下架操作改变。

## 响应和错误码

```json
{"code":0,"message":"ok","data":null}
```

所有已实现接口使用 `code`、`message`、`data`。失败时 `data` 为 null；未预期错误返回通用提示，不向客户端返回堆栈。

| code | HTTP | 当前场景 |
|---|---|---|
| 0 | 200 / 201 | 成功 |
| 40001 | 400 | 参数校验、类型错误、非法 JSON |
| 40401 | 404 | 商品或请求路径不存在 |
| 40501 | 405 | 请求方法不支持 |
| 41501 | 415 | 请求内容类型不支持 |
| 50000 | 500 | 服务内部错误 |

库存不足、下架商品下单、重复支付的错误码已经预留，但对应业务接口尚未实现。

## 测试与复核

### Maven 测试

在 `backend/` 中执行：

```powershell
.\mvnw.cmd test
```

已安装 Maven 时等价命令为 `mvn test`。目前只有默认的 `contextLoads` 启动测试，不能据此宣称完成 L2-12 的订单业务自动化测试。

本次 Maven 测试结果：提交准备时执行 Maven test 成功；当前测试范围仅为默认 contextLoads，不包含商品或订单的业务断言。

### 手动接口验收

开发过程中已通过 curl 逐步验证发布、详情、上下架、列表组合筛选和分页，以及负数、非法状态、非法 ID 等异常请求。具体终端记录由本人保留，最终提交前补齐可复核记录。

JSON 类型错误字段提示修复后，需要单独回归 `stock="10"`、`price=19.9`、损坏 JSON 和正常发布。

已提供 `scripts/test-l0-validation.ps1`。服务运行后，另开终端，在 `backend/` 中执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-l0-validation.ps1
```

脚本检查五种错误请求、正常发布、错误请求不新增商品和成功请求恰好新增一件商品，共八项断言。它会新增一件 `NotebookValidation` 商品，执行期间应暂停其他客户端的商品写入操作。只有实际显示 `RESULT: 8 passed, 0 failed.` 才记录为通过；此阶段文档不预先宣称该结果。

数据库复核：

```sql
SHOW TABLES;
SELECT COUNT(*) FROM products;
SELECT id, title, price, stock, status FROM products ORDER BY id DESC;
```

## 未完成和已知限制

- L1 下单、扣库存事务和订单查询尚未实现。
- 支付、鉴权、并发下单验证、订单业务自动化测试尚未实现。
- 内存数据库在应用进程退出后丢失数据。
- 当前没有用户表，卖家和买家姓名按题目简化字段设计。
- 尚未提供索引前后的 EXPLAIN 对比，不申报 L2-13。
- AI 平台模型、费用、调用次数和日志附件待本人据实补齐。
- 姓名、实际运行环境和投入时长待本人确认。

提交范围限于 `backend/`。原题目 README 保留；`target/`、IDE 配置和数据库数据文件不属于提交内容。
