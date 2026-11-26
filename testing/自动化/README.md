# 自动化测试入口

被测对象是 [`../sut/app.py`](../sut/app.py)（题目给定的 Python SUT，故意植入 19 个缺陷）。
本目录的 7 个测试文件 + `conftest.py` 就是 L1-5 要求的 pytest 自动化套件，**83 个测试项
= 58 passed + 25 xfailed**，覆盖全部 16 个接口。

## 一条命令跑起来

```powershell
# Windows / PowerShell
cd testing\自动化
.\run.ps1 -Install
```

```bash
# macOS / Linux / Git Bash
cd testing/自动化
./run.sh -Install
```

`-Install` 只在第一次或换机器时需要；之后直接 `.\run.ps1`。不想用脚本也可以，等价于：

```bash
python -m pip install -r requirements.txt
python -m pytest
```

**不需要手动启动被测服务。** `conftest.py` 会向系统申请一个空闲端口、拉起
`../sut/app.py`、轮询 `/health` 等到就绪，用例跑完后终止进程。夹具按 **模块** 各起
一个全新实例，因为 SUT 每次启动都会删除重建 `../sut/sut.db`；下单、发布商品这类
会改数据的用例因此不会污染后面的模块。

## 三种运行模式

| 模式 | 命令 | 谁负责启停 SUT | 什么时候用 |
|---|---|---|---|
| 默认 | `.\run.ps1` | 夹具 | 日常跑全量，端口随机不撞车 |
| 固定端口 | `.\run.ps1 -Port 8090` | 夹具 | 想同时看服务日志或抓包 |
| 复用实例 | `.\run.ps1 -BaseUrl http://127.0.0.1:8080` | 你自己 | 调某个接口时反复重跑单条用例 |

第三种模式下夹具不启停任何进程，等价于手工设置环境变量：

```powershell
$env:SUT_BASE_URL = "http://127.0.0.1:8080"   # PowerShell
```

```bash
export SUT_BASE_URL=http://127.0.0.1:8080      # bash
```

> 所有实例共用同一个 `../sut/sut.db`。**不要**一边手工跑着 SUT 一边让夹具再起一个，
> 两边会互相重置数据库，结论不可信。要并行就统一走 `-BaseUrl`。

## 参数透传

脚本只认 `-Install` / `-Port` / `-BaseUrl` / `-h` 四个选项，其余参数（包括以 `-` 开头的）
原样转给 pytest：

```powershell
.\run.ps1 test_orders.py -k oversell -x        # 单文件 + 名字过滤 + 遇错即停
.\run.ps1 test_auth.py::test_forged_token_rejected   # 单条用例
.\run.ps1 --collect-only -q                    # 只收集不执行
.\run.ps1 -m "not slow" --lf                   # 标记过滤 + 只跑上次失败
```

## 文件说明

| 文件 | 作用 |
|---|---|
| `conftest.py` | 公共夹具：申请端口、启停 SUT、轮询 `/health`、登录并下发 token |
| `pytest.ini` | 入口配置：`testpaths`、`addopts = -ra`、`xfail_strict = true` |
| `requirements.txt` | 只有两个依赖：`pytest`、`requests`（SUT 本身只用标准库） |
| `run.ps1` / `run.sh` | 一键入口，封装「查依赖 → 跑 pytest → 汇总退出码」 |
| `test_health.py` | TC-001，`/health` 探活 |
| `test_auth.py` | TC-002~006，登录 / 登出 / token |
| `test_authz.py` | TC-058~066，越权与角色矩阵 |
| `test_products.py` | TC-007~021，列表、筛选、分页、发布、上下架 |
| `test_orders.py` | TC-022~036，下单、金额、库存、并发 |
| `test_payments.py` | TC-037~045，支付与回调 |
| `test_reviews.py` | TC-046~057，评价的创建与查询 |

每个测试函数的 docstring 首行就是 `测试用例.md` 里的用例编号（TC-xxx），
两边可以直接对照。

## 怎么读结果

```text
58 passed, 25 xfailed in 10.10s
```

- **passed 58** —— 断言与接口文档一致，SUT 行为正确。
- **xfailed 25** —— 断言写的是文档承诺的正确行为，SUT 实际不符。每个都带
  `@pytest.mark.xfail(strict=True, reason="BUG-xx ...")`，`-ra` 会在结尾逐条列出。
  这不是脚本写错，而是已知缺陷的登记方式。
- **failed 0** —— 没有真实失败。一旦出现 `failed`，要么是回归，要么是某条 xfail 被
  修好了（`strict=True` 会让它变成 failed，提醒摘掉标记），两种都必须处理。

退出码约定：`0` 通过（含只有 xfail）／`5` 没收集到用例（路径或 `-k` 写错）／其他为真实失败。

25 个 xfail 与缺陷报告的对应关系：BUG-01(2) BUG-02(1) BUG-03(1) BUG-04(3) BUG-05(2)
BUG-06(1) BUG-07(1) BUG-08(1) BUG-09(1) BUG-10(1) BUG-11(2) BUG-12(2) BUG-13(1)
BUG-14(1) BUG-15(1) BUG-16(2)。

## 用例 66 条与测试项 83 个

`测试用例.md` 里是 66 条设计用例，pytest 收集到 83 个 item，差额来自**参数化**：
一条用例带多组数据时会被展开成多个 item。例如：

```python
@pytest.mark.parametrize("page_size", [0, 51])
def test_product_list_page_size_out_of_range(page_size):
    ...
```

一条「pageSize 越界」用例因此贡献 2 个 item。汇报时两个口径都成立，说明清楚即可。

## 为什么改成 ini 而不是 tox.ini / setup.cfg

这个目录只有测试、没有可打包的模块，`pytest.ini` 足够。另外 `pytest.ini` 特意写成
**纯 ASCII**：`iniconfig` 用平台默认编码读 ini（简体中文 Windows 上是 GBK），注释里
只要有非 ASCII 字节，pytest 会在收集前就抛 `UnicodeDecodeError`。