# 提交说明（SUBMISSION）

## 基本信息

| 项       | 内容         |
| -------- | ------------ |
| 姓名     | 孙润楠       |
| 学号     | 220250946691 |
| 方向     | 测试         |
| 分支     | 220250946691 |
| 实际投入 | 10 小时      |

## 环境

| 项       | 版本          |
| -------- | ------------- |
| 操作系统 | Windows 11    |
| 运行时   | Python 3.14.7 |
| 包管理器 | pip           |

## 启动方式（必填，要能照抄）

```bash
cd testing/sut
python app.py --port 8080
# 打开 http://127.0.0.1:8080
```

被测服务启动后会自己建 `sut.db` 并写入种子数据，不需要额外步骤。跑自动化测试时请**另开一个终端**，不要把服务停掉。

想从干净数据重跑（并发测试和"库存减 1"这类用例需要）：

```bash
# 停掉服务，删库，再用同一条命令启动（app.py 会把 sut.db 建在脚本同目录）
rm testing/sut/sut.db
cd testing/sut && python app.py --port 8080
```

## 我做到了哪一步

| 层级 | 完成情况 | 说明 |
| ---- | --------------- | ---- |
| L0   | 做了 4 / 4 项 | 测试计划、22 条用例、11 条缺陷、测试报告，四项齐全 |
| L1   | 做了 2 / 2 项 | L1-5 自动化 8 条（`testing/自动化/`）、L1-6 代码审查 10 条 |
| L2   | 做了第 7、8 项 | 安全专项（认证/越权/输入/金额四方向，各 3 条）、并发测试（10 并发买库存 1，库存 -6） |

## 测试 / 自检

被测服务需先启动（见上方「启动方式」），然后：

```bash
cd testing/自动化
pip install -r requirements.txt
pytest -q
```

实际输出（一次完整运行的原文，耗时数字每次会略有差异）：

```
=========================== short test summary info ===========================
FAILED test_sut.py::test_create_order_normal - AssertionError: 总额应为整数分...
FAILED test_sut.py::test_order_off_shelf_product - AssertionError: 商品已下架...
FAILED test_sut.py::test_product_pagesize_over_limit - AssertionError: pageSi...
FAILED test_sut.py::test_order_quantity_zero - AssertionError: quantity=0 应...
4 failed, 4 passed in 0.66s
```

单条失败的完整断言（`pytest -q --tb=line` 的原文，行号为当前文件）：

```
test_sut.py:72: AssertionError: 总额应为整数分 1990，实际返回 1989.9999999999998
test_sut.py:108: AssertionError: 商品已下架时应拒绝下单，实际返回 201
test_sut.py:118: AssertionError: pageSize 超上限应报参数错误，实际返回 200
test_sut.py:133: AssertionError: quantity=0 应报参数错误，实际返回 201
```

**4 条失败全部对应 `缺陷报告.md` 中的已登记缺陷，不是脚本写错**：

| 失败用例 | 缺陷编号 | 原因 |
|---|---|---|
| `test_create_order_normal` | BUG-11 | 总额经「分→元→分」浮点往返，返回 `1989.9999999999998` |
| `test_order_off_shelf_product` | X-03 | 下单未校验商品 `status`，下架商品仍可买 |
| `test_product_pagesize_over_limit` | X-01 | `pageSize` 未做 1~50 范围校验 |
| `test_order_quantity_zero` | BUG-06 | `quantity` 未校验正整数，0 与负数都能下单 |

断言依据是接口文档里的承诺，不是服务的现有行为；服务修好后这 4 条会自然转绿，可以当作修复后的回归基线。脚本支持换端口：`SUT_BASE=http://127.0.0.1:8090 pytest -q`。

并发验证单独跑（两组脚本，结论互相印证）：

```bash
python 并发测试.py    # 10 线程同时下单
python 串行对照.py    # 同样 10 条，但一条一条发
```

实测输出：

```text
并发 10 个请求：201 成功 7 个，409 库存不足 3 个，其他 0 个
商品 5 下单后库存 = -6
新增订单数 = 7
```

```text
串行 10 条：201 成功 1 个，409 库存不足 9 个
商品 5 结束后库存 = 0，新增订单 = 1 条
结论：串行结果完全正确，库存没有被扣成负数
```

两个脚本的唯一差别是并发度：串行就对、并发就错。连跑 3 轮并发，成功数为 7 / 7 / 8，库存 -6 / -6 / -7 ——「库存变负」每轮都复现，成功个数随调度波动，所以报告里不把「7」当常量。

## 没做完的地方

| 项  | 状态 | 原因 |
| --- | ---- | ---- |
| pytest 的覆盖范围 | 8 条，只到接口主路径 | 时间关系，只挑了「正常/异常/边界各两条 + 4 条对照已知缺陷」的样本，22 条手工用例没有全部脚本化；断言只比 HTTP 响应，没有查数据库落库结果 |
| 商品模块的组合筛选 | 只测了 `keyword` 单条件 | 深度优先策略的代价：订单与支付占了约六成时间，商品与评价只做基础用例 |
| 评价模块的重复评价 | 没测 | 文档未约定同一用户能否重复评价，缺判定基准，不做主观推断 |
| 性能与长稳 | 没做 | 48 小时窗口内不具备条件，题目也未要求 |
| L2-9 CI / L2-10 UI 自动化 | 没做 | L2 只选了两项（7、8），封顶已是 10 分，再补不增分 |

## 自检清单

- [x] 删掉依赖目录后，按上面的命令能重新跑起来（依赖只有 `pytest`、`requests`，已写入 `requirements.txt`）
- [x] 没提交 `node_modules` / `target` / `*.db` / `.env`（`sut.db` 由 `.gitignore` 排除，首次启动自动生成）
- [x] `DESIGN.md`、`AI_USAGE.md` 已填写完整
- [x] 缺陷报告里 11 条缺陷均有可执行的 curl 与真实返回，未全填 S1（9 条 S1 / 2 条 S2），同根因问题已合并
