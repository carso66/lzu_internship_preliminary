# 提交说明（SUBMISSION）

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 彭家宝 |
| 学号 | 320240944401 |
| 方向 | 后端（BE） |
| 分支 | `320240944401` |
| 实际投入 | 8小时 |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Windows 11 |
| 运行时 | JDK 17 |
| 包管理器 | Maven 3.9.14 |
| 后端框架 | Spring Boot 3.5.16、Spring JDBC（JdbcTemplate） |
| 数据库 | H2 文件数据库，启动时通过 `schema.sql` 初始化 |

## 启动方式（必填）

在仓库根目录执行：

```bash
cd backend
mvn spring-boot:run
```

默认访问地址为 `http://localhost:8080`。健康检查：`GET /api/health`，预期返回 `{"code":0,"message":"ok","data":null}`。

Maven 自动解析项目依赖；无需另行安装数据库服务或配置真实账号、密钥。H2 数据文件位于 `backend/data/`（从 backend 启动），项目不依赖预置测试数据。

本机 8080 曾被其他程序占用，实际接口验收使用以下等效的启动命令（不改项目默认端口）：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081 --server.address=127.0.0.1"
```

此时健康检查地址为 `http://127.0.0.1:8081/api/health`。下列示例按默认端口书写；使用 8081 时只需替换 URL 中的端口。

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | 已实现 5 / 5 项：建表、商品发布、列表、详情、上下架；主要接口已实测 |
| L1 | 已实现四个评分项：下单、订单详情、订单列表、参数校验；主要正常和异常路径已实测，事务异常回滚待专项验证 |
| L2 | 本次不申报选做项目。条件扣库存已实现，但未提交并发专项测试或完整业务自动化测试 |

### 接口一览

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| POST | `/api/products` | 发布商品 |
| GET | `/api/products` | 商品查询，支持 keyword、status、category 叠加筛选和分页 |
| GET | `/api/products/{id}` | 商品详情 |
| PATCH | `/api/products/{id}/status` | 上架或下架 |
| POST | `/api/orders` | 创建订单并扣减库存 |
| GET | `/api/orders/{orderNo}` | 按订单号查询详情 |
| GET | `/api/orders` | 按 buyerPhone 查询订单，支持 page、pageSize |

所有已实现业务接口采用 `code`、`message`、`data` 三字段响应结构，金额统一使用整数分。具体设计见 `DESIGN.md`。

## 测试 / 自检

在 `backend/` 目录运行现有自动化测试（运行前关闭本项目的其他服务实例，避免 H2 文件锁）：

```bash
mvn test
```

已有健康检查测试。商品模块完成后实际执行 `mvn clean test` 的结果为：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

订单模块修复编译错误后已成功启动，并完成真实 HTTP 验收。上述自动化测试输出来自商品模块阶段，不冒充最终版本的业务自动化测试结果。

### 人工 HTTP 验收记录

| 场景 | 实际结果 |
|---|---|
| 健康检查 | HTTP 200，code=0 |
| 发布商品 | 成功返回新 ID 和默认 on 状态；未单独记录此次成功响应的精确 HTTP 状态码 |
| 商品列表、组合筛选、详情 | HTTP 200，字段、筛选结果和分页结构符合本轮预期 |
| 商品上下架 | 状态成功切换，详情查询与修改结果一致 |
| 商品不存在 | HTTP 404 / 40401 |
| 非法分页、小数价格 | HTTP 400 |
| 未知接口 | HTTP 404 / 40400 |
| 正常下单 | HTTP 201，单价 1990 分、数量 2、金额 3980 分，库存 10 → 8，订单状态 created |
| 订单详情、买家订单列表 | HTTP 200，返回对应订单 |
| 库存不足 | HTTP 409 / 40901 |
| 商品下架后下单 | HTTP 409 / 40902 |
| quantity=0 | HTTP 400 / 40001 |
| 商品或订单不存在 | HTTP 404 / 40401 |
| 缺少 buyerPhone、pageSize=51 | HTTP 400 / 40001 |
| 查询无订单的买家 | HTTP 200，total=0，items=[] |
| 异常请求后核对 | 原商品库存仍为 8，测试买家订单数仍为 1 |
| 重启后查询商品 | 旧商品仍存在，验证文件数据库持久化 |

### 评审复核示例（Windows PowerShell）

服务启动后，在另一终端复制运行以下命令。示例自行创建商品，不依赖数据库已有的 ID；手机号为非真实测试数据。默认端口如有冲突，可将 `$base` 改为 8081。

```powershell
$base = 'http://127.0.0.1:8080/api'
Invoke-RestMethod "$base/health"

$productBody = @{
    title = 'Review Test Product'
    price = 1990
    stock = 10
    category = 'Digital'
    description = 'Synthetic test data'
    sellerName = 'Test Seller'
} | ConvertTo-Json
$p = Invoke-RestMethod -Method Post -Uri "$base/products" -ContentType 'application/json' -Body $productBody -ErrorAction Stop
$id = $p.data.id
if ($p.code -ne 0 -or -not $id) { throw 'Product creation failed' }

$phone = '00000' + (Get-Date -Format HHmmssfff)
$orderBody = @{
    productId = $id
    quantity = 2
    buyerName = 'Test Buyer'
    buyerPhone = $phone
    address = 'Test Address'
} | ConvertTo-Json
$o = Invoke-WebRequest -Method Post -Uri "$base/orders" -ContentType 'application/json' -Body $orderBody -UseBasicParsing -ErrorAction Stop
$r = $o.Content | ConvertFrom-Json
$o.StatusCode
$r | ConvertTo-Json -Depth 10
$orderNo = $r.data.orderNo
if ($o.StatusCode -ne 201 -or $r.code -ne 0 -or -not $orderNo) { throw 'Order creation failed' }

curl.exe -i "$base/products/$id"
curl.exe -i "$base/orders/$orderNo"
curl.exe -i "$base/orders?buyerPhone=$phone"
```

预期订单金额 3980 分，库存为 8，详情与买家列表均能查到新订单。上述复核示例根据已执行的同类 HTTP 验收整理，完整脚本尚未作为整体单独执行。

## 没做完的地方

| 项 | 状态 | 原因 |
|---|---|---|
| L2 支付链路、鉴权及索引专项 | 未选做 | 本次将交付范围限定在商品与订单核心链路 |
| L2 并发与自动化业务测试 | 未选做 | 已实现条件库存 UPDATE，但未完成可重复的并发测试及业务测试套件 |
| 事务回滚专项测试 | 未验证 | 尚未人为构造扣减库存后插单失败的情况；不能将事务设计预期当成实测结论 |
| 其他边界 | 未全面验证 | 包括金额溢出、全部 JSON 类型错误、405/415 和多记录分页 |


