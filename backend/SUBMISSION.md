# 提交说明（SUBMISSION）

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 李松岩 |
| 学号 | 220250946451 |
| 方向 | 后端 |
| 分支 | 220250946451 |
| 实际投入 | 7 小时 |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Windows 11 |
| 运行时 | JDK 17（Amazon Corretto 17.0.21.1） |
| 包管理器 | Maven 3.6.1 |

## 启动方式（必填，要能照抄）

在仓库根目录执行以下命令。首次启动时 Maven 会自动下载依赖。

```powershell
cd backend
mvn spring-boot:run
```

启动后访问 `http://localhost:8080/api/health`，返回 HTTP 200。

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | 做了 5 / 5 条 |
| L1 | 做了 4 / 4 条 |
| L2 | 做了第 9 项（支付链路）和第 10 项（并发不超卖） |

## 测试 / 自检

在 `backend/` 目录下执行。

```powershell
mvn test
```

```text
[INFO] Tests run: 76, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

单独复跑并发不超卖测试。

```powershell
mvn '-Dtest=OrderConcurrencyApiTest' test
```

测试会创建一个已上架、库存为 1 的商品，再并发发起 10 个各买 1 件的下单请求。测试断言 1 个请求返回 HTTP 201，9 个返回 HTTP 409（`40901`），最终库存为 0，并且只新增 1 条订单。

## 没做完的地方

| 项 | 状态 | 原因 |
|---|---|---|
| 剩下的L2需求| 待完成 | 没时间了 |

## 自检清单

- [x] 删掉依赖目录后，按上面的命令能重新跑起来
- [x] 没提交 `node_modules` / `target` / `*.db` / `.env`
- [x] `DESIGN.md`、`AI_USAGE.md` 已填写完整
- [ ] 截图放进了 `screenshots/`（前端，本项目为后端）
