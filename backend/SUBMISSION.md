# 提交说明

## 基本信息

| 项目 | 内容 |
|---|---|
| 姓名 | 郑满堂 |
| 学号 / 分支 | 220250947191|
| 方向 | 后端 Java |
| 实际投入 | 7小时|

本次提交范围是 L0 商品管理。订单表已建立，但订单、支付、鉴权等功能未实现。

## 环境

| 项目 | 本地使用版本 |
|---|---|
| 操作系统 | Windows 11，PowerShell |
| JDK | Amazon Corretto 17.0.21.1；工程目标 Java 17 |
| Maven | 3.9.11 |
| Spring Boot | 3.3.6 |
| 数据库 | H2 内存库，通过 Maven 依赖启动，无需单独安装 |

依赖为 Spring Web、Spring JDBC、H2。没有 Maven Wrapper，运行机器需先准备 JDK 17 和 Maven。首次运行需要联网下载依赖；已缓存依赖的本机完成过编译和启动检查。

## 启动方式

从仓库根目录执行：

```powershell
cd backend
java -version
mvn -version
mvn spring-boot:run
```

默认端口为 8080。看到 `Started MarketplaceApplication` 后，另开终端检查：

```powershell
curl.exe -i "http://localhost:8080/api/products"
```

空库应返回 HTTP 200，data 中 total=0、page=1、pageSize=10、items=[]。

必须在 backend 目录启动，因为初始化配置使用 `file:./schema.sql`。脚本随启动创建 products、orders 两张表，没有预置商品。当前没有 `/api/health` 接口，请用上面的列表接口检查服务。

停止服务在原终端按 Ctrl+C。修改代码后重新启动。H2 数据只存在于当前应用进程，重启后需要重新发布测试商品。

若默认端口被其他程序占用，可以选择另一个端口，不必强制结束无关程序：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080"
```

此时下面验证命令里的 8080 也要替换为 18080。

## 数据库查看

服务运行时打开 `http://localhost:8080/h2-console`：

- Driver Class：`org.h2.Driver`
- JDBC URL：`jdbc:h2:mem:marketplace`
- 用户名：`sa`
- 密码：留空

在该应用提供的控制台中执行，而不是直接在 PowerShell 中执行 SQL：

```sql
SHOW TABLES;
SELECT id, title, price, stock, status FROM products;
SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
WHERE TABLE_NAME IN ('PRODUCTS', 'ORDERS');
```

## 完成范围

| 层级 | 完成情况 |
|---|---|
| L0 | 5 / 5 项已实现：两张表、发布、列表、详情、上下架。 |
| L1 | 未实现。题目列出的下单、订单详情、订单列表均不在本次完成范围内。 |
| L2 | 未申报任何选做项。临时 HTTP 检查不作为 L2-12 的 JUnit 测试交付。 |
| 附加修复 | 未知路径返回 40400 / HTTP 404，错误方法返回 40500 / HTTP 405；商品不存在保持 40401。 |

## 测试与复核

### 编译

在 backend 下运行：

```powershell
mvn compile
```

本机编译成功。未编写并提交 JUnit 自动化测试；不能把 `mvn test` 的“没有测试”当作业务用例通过。

### 一组可以复制的 HTTP 检查

服务启动后，在另一 PowerShell 窗口运行。请求体写到临时 UTF-8 文件，避免 Windows PowerShell 向 curl 传递 JSON 引号时产生歧义；不会覆盖项目中的 req.json。

```powershell
$api = 'http://localhost:8080'
$bodyFile = [System.IO.Path]::GetTempFileName()
$utf8 = New-Object System.Text.UTF8Encoding($false)

try {
    # 先发布一件商品，从响应中取得实际 ID，不假设它一定是 1。
    [System.IO.File]::WriteAllText($bodyFile, '{"title":"测试商品","price":1990,"stock":10,"category":"文具"}', $utf8)
    curl.exe -i -X POST "$api/api/products" -H 'Content-Type: application/json' --data-binary "@$bodyFile"

    # 将上一步响应 data.id 填入此处。
    $productId = Read-Host '请输入刚发布商品的 id'
    curl.exe -i "$api/api/products/$productId"
    curl.exe -i "$api/api/products?page=1&pageSize=10"

    # 下架后再查详情，确认保存成功。
    [System.IO.File]::WriteAllText($bodyFile, '{"status":"off"}', $utf8)
    curl.exe -i -X PATCH "$api/api/products/$productId/status" -H 'Content-Type: application/json' --data-binary "@$bodyFile"
    curl.exe -i "$api/api/products/$productId"
    curl.exe -i "$api/api/products?status=off"

    # 业务参数错误与非法 JSON 分开测。
    [System.IO.File]::WriteAllText($bodyFile, '{"title":"x","price":"sad"}', $utf8)
    curl.exe -i -X POST "$api/api/products" -H 'Content-Type: application/json' --data-binary "@$bodyFile"
    [System.IO.File]::WriteAllText($bodyFile, '{bad}', $utf8)
    curl.exe -i -X POST "$api/api/products" -H 'Content-Type: application/json' --data-binary "@$bodyFile"

    curl.exe -i "$api/api/nonexistent"
    curl.exe -i "$api/api/products/$productId/status"
    curl.exe -i "$api/api/products/9223372036854775807"
    curl.exe -i "$api/api/products/abc"
    curl.exe -i "$api/api/products?page=2147483648"
}
finally {
    Remove-Item -LiteralPath $bodyFile
}
```

正常新启动、只通过上面的发布请求建数据时，最后使用的最大 long ID 不存在，预期业务 404。若自行插入过这个 ID，应换成确认不存在的合法 ID。

| 检查 | 预期 |
|---|---|
| 发布商品 | HTTP 201，code=0，返回数字 id |
| 详情、列表 | HTTP 200，code=0 |
| 下架 | HTTP 200，data 只有 id、status；再查详情为 off |
| price 为 sad | HTTP 400，code=40001，price 相关提示 |
| 非法 JSON | HTTP 400，code=40001，请求体不是合法的 JSON 格式 |
| 未知路径 | HTTP 404，code=40400，接口不存在 |
| GET 访问上下架地址 | HTTP 405，code=40500，请求方法不被允许 |
| 合法但不存在的 ID | HTTP 404，code=40401，商品不存在 |
| ID 为 abc | HTTP 400，code=40001，id 必须是大于 0 的整数 |
| page 超 int | HTTP 400，code=40001，page 数值超出范围 |

### 已执行的验证

2026-11-26，在我的验证要求下，通过 AI 辅助在独立实例完成了两轮临时 HTTP 检查：

- L0-5 写入后：23 次请求全部通过，涵盖重复上下架、trim、缺失/非法状态、ID 边界及商品接口回归。
- 异常治理后：18 次请求全部通过，涵盖框架 404/405、业务 404、非法 JSON、参数错误及发布、详情、列表、上下架。

第二轮运行日志未出现这些预期错误的 ERROR 堆栈。两次测试实例均已关闭。两轮覆盖有重复，不代表 41 个独立测试用例；临时测试脚本未作为 JUnit 套件提交。新增的类型绑定异常处理、未知异常 500 兜底未通过 HTTP 主动触发验证。

上面的手动复核命令用于重现关键流程，不是带自动断言的测试套件。

## 尚未完成与已知限制

- L1、L2 未实现，不能下单或支付。
- H2 重启丢数据；暂无持久化和数据库迁移方案。
- keyword 中的 `%`、`_` 未转义；分页两次查询没有快照一致性保证。
- 上下架先查后改，不承诺并发删除场景下的正确性。
- 金额只实现商品价格的整数校验，未验证订单总价计算。

设计取舍见 [DESIGN.md](DESIGN.md)，AI 使用过程与待核对记录见 [AI_USAGE.md](AI_USAGE.md)。
