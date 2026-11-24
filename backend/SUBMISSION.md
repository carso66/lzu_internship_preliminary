# 提交说明（SUBMISSION）

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 待填写 |
| 学号 | 220250946451 |
| 方向 | 后端 |
| 分支 | `220250946451` |
| 实际投入 | 待填写 |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Windows 11 |
| JDK | 19.0.2 |
| Maven | 3.6.1 |
| Spring Boot | 3.0.13 |
| MyBatis Spring Boot Starter | 3.0.2 |
| 数据库 | H2 内存模式，重启后数据清空 |

## 启动方式

在仓库根目录执行下面的命令。Maven 会在首次启动时下载依赖。

```powershell
cd backend
mvn spring-boot:run
```

## 测试 / 自检

在 `backend/` 下执行 `mvn clean package` 验证构建。启动后在另一个终端执行以下命令。健康检查通过 MyBatis Mapper XML 在 H2 中执行 `SELECT 1`。

```powershell
curl.exe -i http://localhost:8080/api/health
```

已在 Windows 11 上通过构建和启动验证。健康检查实际返回 HTTP 200，响应体如下。

```json
{"code":0,"message":"ok","data":null}
```

## 我做到了哪一步

目前已初始化工程。以下题目计划在正式开发阶段完成。

| 层级 | 计划范围 | 完成情况 |
|---|---|---|
| L0 | 建表和商品接口 | 待开发 |
| L1 | 下单扣库存和订单查询 | 待开发 |
| L2 | L2-9 支付链路、L2-11 鉴权 | 待开发 |

## 自检清单

- [x] 执行干净构建并按上面的命令启动
- [x] 确认提交中没有 `target/`、`*.db` 或 `.env`
- [ ] 填写完整的 `DESIGN.md` 和 `AI_USAGE.md`
