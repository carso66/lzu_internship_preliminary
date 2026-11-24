# 提交说明（SUBMISSION）

> 复制到你的方向目录（`frontend/` / `backend/` / `testing/`）下填写。
> **评审会照着你写的启动命令真的跑一遍**，所以它必须能跑通。

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 |  |
| 学号 |  |
| 方向 | 前端 / 后端 / 测试 |
| 分支 | `320210912345`（你的学号，已预建） |
| 实际投入 | ?? 小时 |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | macOS 14 / Windows 11 / Ubuntu 22.04 |
| 运行时 | Node 20.x / JDK 17 / Go 1.22 / Python 3.12 |
| 包管理器 | npm / pnpm / Maven / pip |

## 启动方式（必填，要能照抄）

```bash
cd frontend          # 或 backend / testing

# 装依赖（不需要就删掉这行）
npm install

# 启动
npm run dev
# 打开 http://localhost:5173
```

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | 做了 ___ / 5 条 |
| L1 | 做了 ___ / 4 条 |
| L2 | 做了第 ___ 项 |

## 测试 / 自检

在你的方向目录下运行测试。后端在 `backend/` 下按项目语言选一条命令，提交时只保留实际使用的命令。

```bash
npm test        # 前端
mvn test        # 后端（Java / Maven）
go test ./...   # 后端（Go）
pytest -q       # 测试方向
```

```
（有输出就贴，没有就写"未写自动化测试"）
```

## 没做完的地方

| 项 | 状态 | 原因 |
|---|---|---|
|  |  |  |

## 自检清单

- [ ] 删掉依赖目录后，按上面的命令能重新跑起来
- [ ] 没提交 `node_modules` / `target` / `*.db` / `.env`
- [ ] `DESIGN.md`、`AI_USAGE.md` 已填写完整
- [ ] 截图放进了 `screenshots/`（前端）
