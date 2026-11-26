# 提交说明（SUBMISSION）

## 基本信息

| 项    | 内容                                |
| ---- | --------------------------------- |
| 姓名   | Zhaohx02（GitHub 用户名，**请替换为真实姓名**） |
| 学号   | 220250947121                      |
| 方向   | 前端                                |
| 分支   | `220250947121`                    |
| 实际投入 | 约 5 小时（按实际修改）                     |

## 环境

| 项    | 版本                                                   |
| ---- | ---------------------------------------------------- |
| 操作系统 | Windows 10（10.0.19045）                               |
| 运行时  | Node 24.22.3（**最低 Node 18**，Vite 5 的要求，`node -v` 自查） |
| 包管理器 | npm 11.19.1                                          |

> 只有 `vite` 和 `vitest` 两个 devDependencies；mock 服务与业务代码都是零依赖的原生 JS。

## 启动方式（必填，要能照抄）

```bash
cd frontend
npm ci                  # 按 package-lock.json 安装；没有 npm ci 就用 npm install
```

**三步，顺序别换**：装依赖 → 起 mock → 起前端。数据服务是零依赖的，**必须另开一个终端**：

```bash
# 终端 1：mock 数据服务（监听 127.0.0.1:3001）
node mock/server.mjs        # 等价于 npm run mock

# 终端 2：前端（监听 127.0.0.1:5173）
npm run dev
```

打开 **http://127.0.0.1:5173**（dev server 显式绑了 IPv4，部分 Windows 环境下 `localhost` 会解析到 `::1` 而打不开）。

**30 秒确认跑起来了**

1. 终端 1 打印 `好物市集 mock 已启动: http://127.0.0.1:3001`，之后每访问一次页面都会多一条 `GET /api/products...` 日志；
2. 浏览器打开 <http://127.0.0.1:3001/api/products> 能看到 JSON；
3. 页面首屏有商品表，底部有「订单列表」区块（初始 2 笔订单）。

> 先开页面、后起 mock 也可以：页面会显示「连不上接口…」+「重试」按钮，起完 mock 点重试即可。

### 端口被占用时

| 现象                             | 改法                                 |
| ------------------------------ | ---------------------------------- |
| 5173 被占                        | Vite 会自动顺延到 5174，以终端打印的 Local 地址为准 |
| 3001 被占（mock 打印「端口 3001 已被占用」） | 换端口起 mock，并把前端指过去（见下）              |

```powershell
# PowerShell：mock 换 4000，前端指向 4000
$env:PORT=4000; node mock/server.mjs                      # 终端 1
$env:VITE_API_BASE="http://127.0.0.1:4000"; npm run dev   # 终端 2
```

```bash
# bash / zsh
PORT=4000 node mock/server.mjs
VITE_API_BASE=http://127.0.0.1:4000 npm run dev
```

> 如果 `VITE_API_BASE` 没生效（页面仍在连 3001），在 `frontend/` 下新建 `.env.local`，内容一行 `VITE_API_BASE=http://127.0.0.1:4000`，再 `npm run dev`。

### 恢复初始数据

把数据恢复成初始状态（评审每轮开始前建议先执行一次）：

```powershell
# PowerShell
Invoke-RestMethod -Method Post http://127.0.0.1:3001/api/_reset
```

```bash
# 装了 curl 也可以用
curl -X POST http://127.0.0.1:3001/api/_reset
```

可选：打开故障注入，验证支付轮询的「最多 10 次后提示未确认」。

```powershell
# PowerShell
$env:ERROR_RATE=1; node mock/server.mjs
```

```bash
# macOS / Linux
ERROR_RATE=1 node mock/server.mjs
```

## 我做到了哪一步

| 层级  | 完成情况                                        |
| --- | ------------------------------------------- |
| L0  | 5 / 5（列表、发布、发布反馈、上下架、加载态/空态 + 搜索）           |
| L1  | 4 / 4（下单、订单列表、金额处理、表单校验）                    |
| L2  | 第 1 项 **L2-10 支付链路**完成；L2-12 要求的「元转分」测试也已满足 |

## 测试 / 自检

```bash
cd frontend
npm test          # vitest run
```

```text
 ✓ tests/money.test.js (20 tests)
 ✓ tests/api.test.js (17 tests)
 ✓ tests/publish-form.test.js (10 tests)
 ✓ tests/payment-poller.test.js (8 tests)
 ✓ tests/purchase-form.test.js (6 tests)
 ✓ tests/text.test.js (6 tests)
 ✓ tests/product-status.test.js (6 tests)
 ✓ tests/order-status.test.js (4 tests)
 ✓ tests/search-box.test.js (4 tests)

 Test Files  9 passed (9)
      Tests  81 passed (81)
```

手工按 README 的验收条目点过的几条：

- **列表**：所有价格都是 `¥xx.xx`；库存 0 的行灰化 + 「缺货」；在售 / 已下架徽标与数据一致；超长标题单行省略。
- **发布**：输入 `12.50` → 请求体 `price: 1250`；51 字标题 / `19.999` 元 / `1000` 库存都被拦在对应字段下方，Network 里没有 `POST /api/products`。
- **上下架**：`PATCH /api/products/{id}/status`，请求体 `{"status":"off"}`；成功后徽标与按钮文案同步；连点只发 1 个请求。
- **搜索**：`zzz` → 「没有找到匹配的商品」+「清空筛选」，点完搜索框和列表一起复原；Slow 3G 下能稳定看到加载提示。
- **下单**：`quantity` 提交为数字；库存不足 / 已下架显示后端 `message`；成功后库存减少、新订单出现在订单列表并高亮。
- **支付**：`POST /api/payments` 后按钮变「支付中…」并禁用；`GET /api/payments/{paymentId}` 每约 1 秒一次；`processing` 期间一直保持「支付中」；`paid` 后提示成功、订单变「已支付」、按钮永久禁用；一直 `processing` 时第 10 次后提示「支付状态未确认，请稍后在订单列表查看」；轮询中关闭弹窗后 5 秒内不再有新请求；对已支付订单点支付显示「订单已支付，请勿重复支付」。

## 没做完的地方

| 项              | 状态   | 原因                                     |
| -------------- | ---- | -------------------------------------- |
| 关掉支付弹窗后的支付跟踪   | 已知问题 | 关弹窗即停轮询（L2-10 要求），需手动点「刷新」看最终状态        |
| L2-11 防重复提交    | 未单独验 | 底子已有（提交中禁用 + 请求锁），没按「连点 5 次只 1 个请求」验过  |
| L2-13 响应式 / 路由 | 未做   | 时间给了支付链路                               |
| 分类筛选、分页 UI     | 未做   | 接口参数已留好，题目 L0 不要求                      |
| 截图             | 部分   | 已有列表、发布、搜索（成功/失败）、上下架共 8 张；订单列表与支付流程待补 |

## 自检清单

- [x] 按上面的命令能跑起来（`npm run dev` / `npm test` / `npm run build` 都实测通过；`node_modules`、`dist` 已在 `.gitignore`）
- [x] 没提交 `node_modules` / `dist` / `*.db` / `.env`
- [x] `DESIGN.md`、`AI_USAGE.md` 已填写完整
- [x] 截图放进了 `screenshots/` —— 已有 8 张，还差**订单列表**和**支付流程**（题目要求至少含列表、发布表单、订单列表）
