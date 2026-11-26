# 后端提交说明

## 基本信息

| 项目 | 内容 |
|---|---|
| 姓名 | 苏鑫 |
| 学号 / 分支 | 220260946800 |
| 方向 | 后端 BE |
| 实际投入 | 6小时 |
| 环境 | Windows，JDK 17.0.21.1，Maven 3.9.16，Spring Boot 3.5.0 |

## 启动方式

在仓库根目录执行：

```powershell
cd backend
mvn spring-boot:run
```

健康检查：`http://localhost:8080/api/health`，预期 HTTP 200、code=0。
若 8080 被旧服务占用，请先在原终端 Ctrl+C 停止旧服务。修改 Java 文件后需重启服务。
也可以执行 `mvn package` 后用 `java -jar target/marketplace-0.0.1-SNAPSHOT.jar` 启动。
依赖首次下载需要联网；无需安装独立数据库。

## 完成范围

- L0：1～5 全部实现，包含商品、订单建表，发布、筛选分页、详情、上下架。
- L1：6～8 全部实现，包含事务下单扣库存、服务端算价、订单详情及手机号分页查询。
- L2：9 支付、12 自动化测试已实现；额外实现并验证 10 并发不超卖。L2 按题目封顶计分。
- L2-11 鉴权、L2-13 索引证据未选做。

## 接口及参数

所有 API 返回 `{code,message,data}`；金额单位为整数分，成功 code=0。

| 方法和路径 | 说明 | 成功 HTTP |
|---|---|---|
| GET /api/health | 健康检查 | 200 |
| POST /api/products | 发布商品 | 201 |
| GET /api/products | 商品筛选和分页 | 200 |
| GET /api/products/{id} | 商品详情 | 200 |
| PATCH /api/products/{id}/status | 上下架，请求含 status=on/off | 200 |
| POST /api/orders | 下单 | 201 |
| GET /api/orders/{orderNo} | 订单详情 | 200 |
| GET /api/orders | 手机号查订单 | 200 |
| POST /api/payments | 模拟支付 | 200 |

发布字段：title（非空白，最多120字符）、price（正整数分，long范围）、stock（非负int）、
category（非空白，最多60字符）、sellerName（非空白，最多60字符）。description最多2000字符，
可省略或为null，入库为空字符串。JSON整数拒绝字符串、小数和布尔值，文本拒绝数字、布尔值转换。
商品初始状态由服务端设为on。

商品列表：keyword匹配标题，status只允许on/off，category精确匹配，三个条件AND叠加。
空白筛选参数视为未传；keyword中的SQL通配符 % 和 _ 按LIKE语义匹配。
page默认1且至少1，pageSize默认10且范围1～50，按id倒序，返回total、page、pageSize、items。
分页偏移使用long，无匹配结果返回空items。

下单字段：productId（正整数）、quantity（正int）、buyerName（非空白，最多60字符）、
buyerPhone（必填11位数字）、address（非空白，最多500字符）。测试样例使用00000000000等虚构数据。
手机号仅验证11位数字，不校验运营商号段。单价、标题、总金额从数据库生成，客户端额外价格字段不参与计算。
数量乘价格超出long范围返回400；订单号为SO加UUID，数据库同时保证唯一。
订单列表buyerPhone必填；分页规则同商品列表，按created_at、id倒序。

支付字段：orderNo（非空白，最多64字符）、method（alipay或wechat）。金额从订单读取，
同步模拟成功，返回paymentId、orderNo、amount、status、paidAt。无需真实支付账号。

| 错误 | HTTP / code |
|---|---|
| 参数、JSON类型、范围错误 | 400 / 40001 |
| 商品或订单不存在 | 404 / 40401 |
| 库存不足 | 409 / 40901 |
| 商品已下架 | 409 / 40902 |
| 订单已支付 | 409 / 40903 |
| 内部异常 | 500 / 50000（完整异常仅记录在服务端） |

## 数据库与手工核对

使用H2内存数据库，应用进程结束后数据清空。启动自动执行唯一的`schema.sql`，
Maven将它复制到classpath，包含products、orders、payments三张表。

控制台：`http://localhost:8080/h2-console`。
JDBC URL：`jdbc:h2:mem:marketplace;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`。
用户名sa，密码留空。控制台仅允许本机访问。

```sql
SELECT * FROM products;
SELECT * FROM orders;
SELECT * FROM payments;
```

## 自动化测试和验收

在backend目录执行：

```powershell
mvn test
mvn package
```

本次实际结果：60项测试通过，0失败、0错误，JAR打包成功。
`OrderTest`含5个测试方法，覆盖正常下单库存减1、库存不足409、非法数量400、
订单插入失败库存回滚、下架/不存在/金额溢出。测试数据与开发数据库隔离。
部分测试故意制造数据库异常以检查回滚，服务端出现对应ERROR日志是预期现象，应以测试结果判断。

完整HTTP验收：先启动服务，再在另一个PowerShell终端的backend目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

脚本使用虚构数据新增商品、订单和支付记录，18项HTTP检查实际通过。
可用`-BaseUrl http://localhost:18080`指定其他端口。
手动测试也可用VS Code REST Client打开`requests/api.http`，按顺序点击Send Request；
先执行命名请求createProduct、createOrder，后面的请求自动引用它们返回的ID和订单号。

### 并发复核（L2-10）

```powershell
mvn "-Dtest=ConcurrencyTest" test
```

测试自动准备一件库存为1的已上架商品，释放同一个起跑门闩，同时提交10个有效下单请求。
断言恰好1个201，其余9个409/40901，并核对库存为0、本轮新增1条订单。
同一测试类另外验证10次并发支付恰好1个200，其余9个409/40903，数据库仅1条支付记录。
不需要事先手动创建数据，测试可重复运行。

## 已知限制与提交

- 数据不跨进程重启保留；支付为同步模拟。
- 手机号查询未加身份权限，仅实现题目规定的查询接口。
- 未实现退款、取消订单、真实支付回调。
- 姓名：苏鑫；实际投入6小时。学校AI平台请求36次、花费3.53元，使用Qwen3.8 Flash、DeepSeek V4 Pro、DeepSeek V4.1 Flash（本人提供）。
- 开发分支220260946800；PR base为carso66仓库同名分支，标题为[BE] 苏鑫 220260946800。
- Git提交作者使用本人提供的Avery-Ethan及邮箱；代码按商品、订单、支付、文档四个部分保存本地提交。
- 不提交target、数据库文件、密钥和.env。
