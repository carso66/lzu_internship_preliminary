# 提交说明（SUBMISSION）

## 基本信息

| 项    | 内容             |
| ---- | -------------- |
| 姓名   | 王菊艳            |
| 学号   | 320240941900   |
| 方向   | 前端             |
| 分支   | `320240941900` |
| 实际投入 | 5h             |

## 环境

| 项    | 版本            |
| ---- | ------------- |
| 操作系统 | Windows 11     |
| 运行时  | Node v22.22.2  |
| 包管理器 | npm 10.9.7     |
| 测试框架 | Vitest 5.1.1   |
| 浏览器  | Chrome / Edge  |

## 启动方式

```bash
cd frontend
npm install     # 只装 Vitest；不装也能 npm start 直接跑起页面
npm start       # 等价于 npm run dev
# 打开 http://127.0.0.1:5173
```

`server.mjs` 会先检测 3001 端口：若已有 mock 在跑就直接复用，否则自动拉起 `frontend/mock/server.mjs`，再把页面的 `/api/*` 代理过去。所以**一条命令**就能同时起来页面和数据服务，不需要另开终端、也不会有跨域问题。

如果按题目 README 第 3 节另开终端跑了 `node frontend/mock/server.mjs`，本服务检测到 3001 已被占用会直接复用，不会端口冲突。

```bash
npm run mock                     # 只起数据服务：http://127.0.0.1:3001/api/products
PORT=5174 MOCK_PORT=4000 npm start   # 换端口
npm test                         # 跑自动化测试
```

重置数据：

```bash
curl -X POST http://127.0.0.1:3001/api/_reset
```

## 我做到了哪一步

| 层级 | 完成情况                                 |
| -- | ------------------------------------ |
| L0 | **5 / 5 条**                          |
| L1 | **4 / 4 条**                          |
| L2 | 做了第 **11**（防重复提交）、**12**（Vitest 测试）项 |

- **L0-1 列表**：标题 / `¥xx.xx` 价格 / 库存 / 分类 / 状态徽标；库存 0 灰化并标「缺货」；长标题用省略号 + `title` 悬停查看；全部文本用 `textContent` 写入。
- **L0-2 发布**：5 个字段，分类下拉恰好 5 项；价格以「元」输入、请求体 `price` 是整数分；返回 201。
- **L0-3 反馈**：成功 toast + 表单 `reset()` 清空 5 个输入 + 重新拉列表（`pageSize=50` 一次取全，新商品排在最后也能看到）。
- **L0-4 上下架**：按钮文案随状态变「下架 / 上架」，点击后徽标与文案同步更新；点击期间禁用按钮，连点不会发两次。
- **L0-5 加载 / 空态**：请求中有「加载中…」；`keyword=zzz` 显示「没找到相关商品」+「清空筛选」按钮；两者互斥。
- **L1-6 下单**：数量输入提交的是数字；成功后库存减少、订单写入；`40901 库存不足` / `40902 商品已下架` 的后端 `message` 显示在 toast；失败后按钮恢复。
- **L1-7 订单列表**：订单号 / 商品名 / 数量 / 金额 / 状态五项齐全，状态显示「待支付 / 已支付」中文。
- **L1-8 金额**：`toFen()` 用字符串按小数位拆成整数相加，避开 `19.99*100=1998.9999999999998`；`formatYuan()` 用整数拆分输出 `¥xx.xx`，无浮点尾巴、无 NaN；数量变化时显示前端预估总价，下单成功后与服务端 `totalAmount` 比对。
- **L1-9 校验**：价格 >0 且最多两位小数、库存 0~999 整数、标题 1~50 字；错误显示在对应字段下方，**校验失败不发 POST**。
- **L2-11 防重复提交**：点击即 `disabled` + 「提交中…」，全局只允许一个在途下单，连点只产生 1 个请求，失败也能重试。
- **L2-12 测试**：Vitest 12 条用例覆盖整数元 / 一位小数 / 两位小数 / 极小金额 / 大额 / 非法输入。

## 测试 / 自检

```bash
cd frontend
npm test
```

实际输出：

```
> vitest run
 RUN  v5.1.1 C:/Users/.../frontend
 ✓ tests/money.test.mjs (12 tests) 25ms
 Test Files  1 passed (1)
      Tests  12 passed (12)
```

另做了一轮接口链路自检：

```
GET  /                                  -> 200（含 <title>校园好物市集</title>）
GET  /src/app.js  /src/styles.css        -> 200
GET  /api/products?pageSize=50          -> 200, code=0, total=24
GET  /api/products?keyword=zzz          -> total=0（空态分支）
POST /api/products {price:1990,...}     -> 201, id=25
PATCH /api/products/25/status {off}     -> 200, {id:25,status:"off"}
POST /api/orders {productId:1,quantity:2} -> 201, totalAmount=39800（= 19900×2，与前端预估一致）
POST /api/orders {productId:5,...}      -> 409 / 40902 商品已下架
POST /api/orders {productId:7,quantity:5} -> 409 / 40901 库存不足
POST /api/_reset                        -> 恢复初始数据（24 商品 / 2 订单）
```

按题目 L0 验收流程又核对了一次：先 `_reset` 恢复初始数据，再刷新首页，逐条点 L0-1~L0-5 与 L1 的抽值、错误提示，均通过；F12 Network 里 `price` 为整数分、`quantity` 为数字。

## 没做完的地方

| 项                          | 状态 | 原因                                                                         |
| -------------------------- | -- | -------------------------------------------------------------------------- |
| L2-10 支付轮询                 | 没做 | L2 已用第 11、12 项封顶 10 分；轮询边界最多（1 秒间隔、10 次上限、离开页面停止、processing 不能提前报成功），优先级最低 |
| L2-13 详情路由 `/products/:id` | 没做 | 只做了 375px 响应式基础适配，未做路由                                                     |

## 自检清单

- [x] 删掉 `node_modules` 后，`npm install && npm start` 能重新跑起来
- [x] 没提交 `node_modules` / `dist` / `.env`（`.gitignore` 已排除前两者）
- [x] `DESIGN.md` 已填写完整（8 节逐条作答）
- [x] `AI_USAGE.md` 已填写完整（6 节，含场景 4「让 AI 检查」与第 4 节「被 AI 带偏」两个必答项）
- [x] 截图放进 `screenshots/`：`01-products-list.png`（商品列表）、`02-publish-form.png`（发布表单）、`03-orders-list.png`（订单列表），均为本机浏览器实际运行页面
