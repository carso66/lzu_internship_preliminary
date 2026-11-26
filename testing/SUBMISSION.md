# 提交说明（SUBMISSION）

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 墨池语 |
| 学号 | 320240920480 |
| 方向 | 测试 |
| 分支 | `320240920480`（已预建） |
| 实际投入 | 约 24 小时（11-23 至 11-25，可用 `git log --date=format:"%m-%d %H:%M"` 核对提交时间分布） |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Windows 11（PowerShell 5.1） |
| 运行时 | Python 3.13.5 |
| 包管理器 | pip |
| 依赖 | `pytest 8.3.4`、`requests 2.32.3`（SUT 本身只用标准库，无需额外安装） |

## 启动方式（必填，要能照抄）

一键入口（推荐，封装了「查依赖 → 跑 pytest → 汇总退出码」）：

```powershell
cd testing\自动化
.\run.ps1 -Install     # 第一次；之后直接 .\run.ps1
```

```bash
cd testing/自动化
./run.sh -Install      # macOS / Linux / Git Bash
```

不想用脚本，等价的裸命令：

```bash
cd testing/自动化

# 装依赖（只有这两个）
pip install -r requirements.txt

# 跑全部自动化用例
python -m pytest
```

**不需要手动启动被测服务**：`conftest.py` 会向系统申请一个空闲端口、自动拉起 `testing/sut/app.py`、轮询 `/health` 等待就绪，用例跑完后终止进程。

三种运行模式（详见 [`自动化/README.md`](./自动化/README.md)）：

| 模式 | 命令 | 谁负责启停 SUT |
|---|---|---|
| 默认 | `.\run.ps1` | 夹具，端口随机 |
| 固定端口 | `.\run.ps1 -Port 8090` | 夹具，端口固定 |
| 复用实例 | `.\run.ps1 -BaseUrl http://127.0.0.1:8080` | 你自己 |

只想手工看接口返回：

```bash
cd testing/sut
python app.py --port 8080
# 另开一个终端
curl.exe -s http://127.0.0.1:8080/health
```

> Windows 注意：PowerShell 中 `curl` 被别名为 `Invoke-WebRequest`，必须写 `curl.exe`；JSON 用 `-d '{\"k\":\"v\"}'` 转义或 `--data-binary "@body.json"` 传文件。

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | 做了 4 / 4 条（L0-1 测试计划、L0-2 测试用例、L0-3 缺陷报告、L0-4 测试报告） |
| L1 | 做了 2 / 2 条（L1-5 pytest 自动化、L1-6 代码审查） |
| L2 | 未做（L2 是选做。同样的时间投给了 L0-3 的证据质量；并发场景在 L1-5 的 TC-032 里以 3 线程用例覆盖，未另做 L2-8 专项） |

## 测试 / 自检

```bash
cd testing/自动化
python -m pytest
```

真实输出（缩掉 `-ra` 逐条列出的 xfail）：

```text
...xx.xxxx..xx.......xxxxxx............xxxx.......xxxxx...........x..... [ 86%]
..........x                                                              [100%]
============================== 58 passed, 25 xfailed in 10.06s ==============================
```

收集项确认：

```text
$ python -m pytest --collect-only -q
83 tests collected in 0.07s
```

三种入口都实跑过，结果一致：

| 命令 | 结果 |
|---|---|
| `.\run.ps1` | 58 passed, 25 xfailed |
| `.\run.ps1 -Port 8090 test_auth.py` | 3 passed, 2 xfailed |
| `.\run.ps1 -BaseUrl http://127.0.0.1:8091 test_health.py` | 1 passed（夹具未启停进程） |
| `bash ./run.sh test_health.py`（Git Bash） | 1 passed |
| `python -m pytest testing/自动化`（从仓库根目录） | 58 passed, 25 xfailed |

`83 = 58 passed + 25 xfailed`，**0 failed、0 error**。

关于 25 个 `xfailed`：不是脚本写错，而是断言按接口文档写了正确预期、SUT 实际行为不符。用 `@pytest.mark.xfail(strict=True, reason="BUG-xx …")` 表达「期望它失败」。这样一旦 SUT 修好，`strict=True` 会让对应项变成 failed，强制提醒更新用例，不会出现「缺陷修了但用例还挂着 xfail」的假绿。

## 交付物

| 文件 | 内容 | 对应 |
|---|---|---|
| [`测试计划.md`](./测试计划.md) | 测什么（16 接口 × 6 个方面）、不测什么及原因、方法与工具、结束条件 | L0-1 |
| [`测试用例.md`](./测试用例.md) | 66 条用例（TC-001~TC-066）；正常 18 / 异常 37 / 边界 11；P0 43 / P1 20 / P2 3 | L0-2 |
| [`缺陷报告.md`](./缺陷报告.md) | 19 条缺陷，每条含可复制的 `curl.exe` 命令与真实返回原文 | L0-3 |
| [`测试报告.md`](./测试报告.md) | 用例与执行情况、16 个已测接口、问题按模块 × 严重程度分布、未覆盖内容、上线建议 | L0-4 |
| [`代码审查.md`](./代码审查.md) | 15 条问题、10 种问题类型，附 6 段最小复刻脚本的真实验证输出 | L1-6 |
| [`自动化/`](./自动化) | 7 个测试文件 + `conftest.py`，83 个 pytest 测试项；另含 `pytest.ini`、`requirements.txt`、`run.ps1`、`run.sh`、`README.md` | L1-5 |
| [`DESIGN.md`](./DESIGN.md) | 设计说明 8 节 | 表达与设计 |
| [`AI_USAGE.md`](./AI_USAGE.md) | AI 使用说明 6 节 | AI 使用 |

## 关键数字

| 指标 | 数值 |
|---|---|
| 设计用例 | 66 条 |
| 自动化测试项 | 83 个（参数化展开后） |
| 覆盖接口 | 16 / 16 |
| 发现缺陷 | 19 条（S1 致命 10 / S2 严重 7 / S3 一般 2） |
| 其中必须实跑才能发现的 | 3 条（BUG-07 浮点误差、BUG-09 并发超卖、BUG-12 回调伪造） |
| 经实测确认「不是缺陷」的行为 | 11 项（记在缺陷报告附录，用于说明这些点已覆盖、不是漏测） |
| 上线结论 | **不建议上线** |

## 没做完的地方

| 项 | 状态 | 原因 |
|---|---|---|
| L2 加分项（L2-7 安全专项 / L2-8 并发 / L2-9 CI / L2-10 Playwright） | 未做 | L2 选做；相同时间投在 L0-3 的证据质量上收益更高 |
| 并发规模 | 3 线程 + 10 线程两组：3 个 201/库存 -2、8 个 201+2 个 409/库存 -7 | 两组都稳定复现超卖，已足以证明 BUG-09 存在；更高并发下的失败形态未观察 |
| 压力测试 / 长稳测试 | 未做 | SUT 用 `ThreadingHTTPServer` + SQLite，压测数字对真实架构没有参考意义 |
| 权限矩阵完整笛卡尔积 | 只取代表性组合 | 5 用户 × 16 接口 = 80 组，穷举性价比低 |
| 重复支付 / 重复评价的并发竞态 | 单线程验证 | BUG-11、BUG-19 串行即可复现，未再测并发下的放大效应 |

## 自检清单

- [x] 删掉依赖目录后，按上面的命令能重新跑起来（`pip install pytest requests` 后即可）
- [x] 没提交 `node_modules` / `target` / `*.db` / `.env`（`testing/sut/sut.db` 在 `.gitignore` 内，每次启动重建）
- [x] `DESIGN.md`、`AI_USAGE.md` 已填写完整
- [x] `requirements.txt` 已固定版本（`pytest==8.3.4`、`requests==2.32.3`），与本文「环境」表一致
- [x] `pytest.ini` 写成纯 ASCII（`iniconfig` 按 GBK 读 ini，非 ASCII 注释会让 pytest 收集前就崩）
- [x] 截图：**测试方向不需要截图**，本条不适用

## 复现注意事项

1. **不要一边手工跑着 SUT 一边跑 pytest**。所有实例共用同一个 `testing/sut/sut.db`，两边会互相重置数据库，结论不可信。
2. 想对已经跑着的实例做测试，用环境变量接管：

   ```bash
   set SUT_BASE_URL=http://127.0.0.1:8080      # Windows
   export SUT_BASE_URL=http://127.0.0.1:8080   # macOS / Linux
   ```

3. 夹具按 `module` 维度各起一个全新实例。原因：下单、发布商品会改库存，若整个 session 共用一个实例，后面的用例会读到被前面污染的数据（第一版就这么踩坑了，商品列表 `total` 从 11 变成 10）。
