# 提交说明（SUBMISSION）

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 陈正阳 |
| 学号 | 320230942931 |
| 方向 | 测试 |
| 分支 | `320230942931` |
| 实际投入 | 约 8 小时 |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Windows 11 + WSL2 Ubuntu；内核 `6.18.58.1-microsoft-standard-WSL2` |
| 运行时 | Python 3.12.3 |
| 依赖与流水线 | venv + pip；GitHub Actions |
| 测试工具 | Apifox、curl、pytest、requests、GitHub Actions |

## 启动方式

SUT 不需要额外服务，在仓库根目录执行：

```bash
python3 testing/sut/app.py --port 18081
```

等待服务就绪：

```bash
curl -fsS http://127.0.0.1:18081/health
```

自动化测试依赖建议在仓库根目录创建虚拟环境后安装：

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r testing/自动化/requirements.txt
```

后续本地命令均假设已激活该虚拟环境。

## 测试方式

### 本地手工测试

手工初筛主要使用 Apifox 和 curl。所有缺陷都用 curl 重新复现，并把可执行命令、HTTP 状态码和真实响应写入 `testing/缺陷报告.md`。

文档中出现的 `alice`、`bob`、`admin`、`tk_2`、手机号和密码均为题目 SUT 的种子数据，不是真实账号或个人信息。

### 本地自动化

两种方式二选一。方式一不预先启动 SUT，未设置 `SUT_BASE_URL` 时，pytest fixture 会自动启动 `127.0.0.1:18081`：

```bash
cd testing/自动化
python -m pytest -q
```

方式二：如果要复用已启动的服务，显式设置 `SUT_BASE_URL`，pytest 只做健康检查，不再启动另一个 SUT：

```bash
cd testing/自动化
SUT_BASE_URL=http://127.0.0.1:18081 python -m pytest -q
```

CI 中使用等价命令：

```bash
cd testing/自动化
SUT_BASE_URL=http://127.0.0.1:18081 pytest -q --junitxml=reports/pytest.xml
```

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | 已完成：测试计划、28 条普通手工用例、15 类有证据缺陷、测试报告和执行结论 |
| L1 | 已完成：64 条 pytest 自动化、legacy 代码审查 10 条，并同步业务结果和静态问题 |
| L2 | 完成 12条 安全专项、3条 并发测试、L2-9 CI |

## 实际结果

| 项 | 结果 |
|---|---|
| 设计用例 | 44 条：L0 普通手工 28 条、安全专项 12 条、并发专项 4 条；18 条通过，26 条失败或部分失败 |
| Apifox 初筛 | 56 个请求、112 条断言；42 个请求被标记失败，39 条断言失败 |
| 缺陷 | 15 类均有真实请求证据；严重程度分布为 S1 11 类、S2 2 类、S3 2 类 |
| 自动化 | 64 条，`32 passed`、`32 xfailed`、`0 failed`、`0 error` |
| 用例映射 | `测试用例.md` 三张表与 `缺陷报告.md` 双向索引一致 |
| xfail 含义 | 断言的是正确业务预期；当前 SUT 存在对应缺陷，因此不是脚本失败 |
| 代码审查 | 10 条，覆盖 SQL 拼接、并发扣库存、金额精度、异常被吞、资源未释放和敏感信息 |
| CI | 本地等价验证通过，workflow 会启动 SUT、运行 pytest、上传 JUnit 报告和 `sut.log` |
| 并发 | 商品 `id=5` 初始库存为 1，10 个并发购买实测 `success_count=10`、`final_stock=-9` |

## 没做完的地方

| 项 | 状态 | 原因 |
|---|---|---|
| UI 自动化 | 未做 | 评价页是服务端渲染 HTML，已用 requests 做转义断言；Playwright 的收益低于安全专项和并发专项 |
| 性能压测 | 未做 | 本轮并发测试关注正确性和库存一致性，不评估吞吐、延迟与资源占用 |

## 自检清单

- [x] 删掉依赖目录后，按上面的命令能重新安装依赖并运行
- [x] 没提交 `node_modules` / `target` / `*.db` / `.env` / 运行日志
- [x] `DESIGN.md` 已按模板逐项填写
- [x] `AI_USAGE.md` 已填写完整
- [x] 前端截图项不适用；本提交方向为测试
- [x] 最终自检未发现临时产物或真实敏感信息进入提交
