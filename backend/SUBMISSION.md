# 提交说明（SUBMISSION）

> 复制到你的方向目录（`frontend/` / `backend/` / `testing/`）下填写。
> **评审会照着你写的启动命令真的跑一遍**，所以它必须能跑通。

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 鲁焕 |
| 学号 | 320250947071 |
| 方向 | 后端 |
| 分支 | `320250947071`（已预建） |
| 实际投入 | ?? 小时 |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Windows 11 24H2 |
| 运行时 | JDK 17（本机装的是 JDK 25，pom 的 `<java.version>` 定为 17，两边都能编译） |
| 包管理器 | Maven 3.9（仓库自带 `mvnw`，没装 Maven 也能跑） |

## 启动方式（必填，要能照抄）

```bash
cd backend

# 方式一：本机装了 Maven
mvn spring-boot:run

# 方式二：没装 Maven，用仓库自带的 wrapper（会自动下载一次 Maven）
./mvnw spring-boot:run        # Windows 用 mvnw.cmd spring-boot:run

# 起来后访问
# http://localhost:8081/api/health
```

> **必须在 `backend/` 目录下启动**：`schema.sql` 的路径是相对启动目录的（`file:./schema.sql`），
> 换目录启动会报"找不到 schema.sql"。
>
> 端口是 **8081**（本机 8080 被占用）。换环境要改端口，改 `src/main/resources/application.properties` 里的 `server.port`。

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | 做了 ___ / 5 条 |
| L1 | 做了 ___ / 4 条 |
| L2 | 做了第 ___ 项 |

## 测试 / 自检

```bash
mvn test        # 后端（Java / Maven）
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
