# CampusMall 自动化测试工程 v1

## 设计目标
针对主办方原始 SUT 编写可重复执行的 API 与数据库联合测试。只测试，不修改 `testing/sut/app.py`、`testing/legacy/` 或原始 `testing/sut/sut.db`。

## 隔离机制
- 每个测试用例使用 pytest `tmp_path` 创建独立 SQLite 数据库。
- 动态导入原始 `testing/sut/app.py`，先将模块全局 `DB_PATH` 重定向到临时数据库，再调用原始 `init_database()`。
- 使用原始 `MallServer` 与 `ApiHandler`，绑定 `127.0.0.1` 和系统分配的临时端口。
- 测试结束关闭 HTTP 服务和线程。

## 目录
```text
testing/自动化/
├── conftest.py
├── pytest.ini
├── requirements.txt
├── README.md
└── tests/
    ├── test_smoke.py
    ├── test_authorization.py
    ├── test_payments.py
    ├── test_orders.py
    ├── test_injection.py
    └── test_xss.py
```

## 安装
在项目根目录执行：
```cmd
conda activate D:\workspace\envs\qa-maas-win
python -m pip install -r testing\自动化\requirements.txt
```

## 运行
```cmd
python -m pytest testing\自动化 -v
```

单独运行：
```cmd
python -m pytest testing\自动化\tests\test_smoke.py -v
python -m pytest testing\自动化\tests\test_orders.py -v
python -m pytest testing\自动化\tests\test_payments.py -v
python -m pytest testing\自动化\tests\test_injection.py -v
python -m pytest testing\自动化\tests\test_xss.py -v
```

## 结果解释
- `test_smoke.py` 应先通过，证明隔离启动和 HTTP 链路正常。
- 其他测试按“修复后应满足的安全行为”编写。当前 SUT 已经通过黑盒测试确认存在相关缺陷，因此出现断言 FAIL 是预期的缺陷发现结果。
- `ERROR` 通常表示测试基础设施、依赖或接口契约问题，应先排查，不要直接登记为产品缺陷。

## 覆盖范围
普通用户越权、无签名伪造支付回调、客户端金额篡改、非法购买数量、并发库存超卖、商品搜索 SQL 注入、商品评价存储型 XSS。

## 已知前置条件
- 种子用户 Alice 的测试 Token 当前为 `tk_2`。
- 商品 ID=1 当前价格为 1990（最小货币单位）。
- 商品 ID=5 当前初始库存为1。
- 商品评价接口按当前已观察契约使用 `productId`、`content`、`rating`。
若主办方修改种子数据或接口契约，应先核对源码并更新测试，不应盲目修改断言。
