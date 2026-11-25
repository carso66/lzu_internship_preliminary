# 校园好物市集 · 前端

这是一个基于 Vue 3 + Vite 的校园商品交易前端，对接 `mock/server.mjs` 提供的模拟接口。当前已经打通商品浏览与筛选、商品发布、商品上下架、数量下单、订单列表、支付轮询和商品详情查看等链路。

## 技术栈

| 项 | 使用方案 |
|---|---|
| 框架 | Vue 3.5 |
| 构建工具 | Vite 8 |
| 路由 | vue-router 4（History 模式） |
| 语言 | JavaScript |
| 样式 | 原生 CSS，组件内 scoped style |
| 单元测试 | Vitest 5 |
| 数据服务 | `mock/server.mjs`，零额外依赖 |

## 快速启动

先启动 Mock API（终端 A）：

```powershell
cd D:\Projects\personal\lzu_internship_preliminary\frontend
node mock/server.mjs
```

再启动前端（终端 B）：

```powershell
cd D:\Projects\personal\lzu_internship_preliminary\frontend
npm install
npm run dev
```

默认地址：

- 前端：<http://localhost:5173>
- Mock API：<http://127.0.0.1:3001/api>

如果需要恢复 Mock 初始数据，可在服务运行时执行：

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:3001/api/_reset
```

Mock 数据保存在内存中，重启 `mock/server.mjs` 会恢复初始状态。不要修改 `mock/` 目录。

## 页面与路由

| 路径 | 页面 | 说明 |
|---|---|---|
| `/` | `ProductListView` | 商品列表、标题搜索、发布商品、上下架和购买入口 |
| `/orders` | `OrderListView` | 订单列表、支付宝/微信支付、支付状态轮询 |
| `/products/:id` | `ProductDetailView` | 商品详情；直接打开 URL 也会自行请求数据；不存在时显示“商品不存在” |

`App.vue` 顶部提供“商品列表 / 我的订单”导航。商品详情属于商品相关页面，导航中的“商品列表”会保持激活状态。

## 目录结构

```text
frontend/
├── mock/                       # 题目提供的 Mock 服务，未修改
├── screenshots/                # 验收截图
├── src/
│   ├── api/index.js            # API 基础地址与统一请求封装
│   ├── components/
│   │   ├── ProductCard.vue     # 商品展示、数量输入、购买和上下架事件
│   │   ├── ProductForm.vue     # 商品发布表单与字段校验
│   │   └── OrderCard.vue       # 订单展示与支付入口
│   ├── router/index.js         # 路由配置
│   ├── utils/
│   │   ├── money.js            # 元/分转换与金额展示
│   │   ├── money.test.js       # 金额单元测试
│   │   ├── product.js          # 商品标题清洗
│   │   └── product.test.js     # 标题清洗单元测试
│   ├── views/
│   │   ├── ProductListView.vue
│   │   ├── OrderListView.vue
│   │   └── ProductDetailView.vue
│   ├── App.vue
│   ├── main.js
│   └── style.css
├── package.json
└── vite.config.js
```

## 已实现功能

### 商品列表

- 进入页面调用 `getProducts({ pageSize: 50 })`，使用返回的 `data.items` 渲染，不硬编码商品。
- 卡片显示标题、`formatPrice(price)`、库存、分类以及“在售 / 已下架”徽标。
- 库存为 0 的卡片整体灰化、显示“缺货”、购买按钮禁用。
- 过长标题使用单行省略，不撑破卡片布局。
- 支持按标题前端过滤，并提供“清空筛选”按钮。
- 页面使用 `loading / error / empty / ready` 互斥状态，分别显示加载、错误、空结果和列表内容。
- 商品标题只通过 Vue 的 `{{ }}` 渲染，没有使用 `v-html`。

### 商品发布

- 字段包括标题、价格、库存、分类和描述。
- 分类固定为 5 项：数码、图书、服饰、食品、文具。
- 标题校验 1~50 字；价格必填、大于 0、最多两位小数；库存为 0~999 的整数。
- 校验错误显示在对应字段下方，不使用 `alert`。
- 提交前通过 `yuanToFen()` 将元金额转换为整数分。
- 提交中按钮禁用并显示“提交中…”，避免重复提交。
- 成功后显示 toast、重置 5 个字段，并触发父组件重新拉取商品列表。
- 失败时展示后端返回的 `message`，按钮恢复可点。

### 商品上下架

- 在售商品显示“下架”，已下架商品显示“上架”，请求中显示“处理中…”并禁用按钮。
- 调用 `updateProductStatus(id, status)`；成功后只更新对应商品的徽标和按钮状态。
- `ProductListView` 使用 `statusUpdatingIds` 按商品 ID 加锁，同一商品快速连点只会产生一次 PATCH。
- 列表中已下架商品仍可点击购买，以便展示后端返回的“商品已下架”错误；库存为 0 时购买按钮在 UI 层禁用。

### 数量选择与下单

- 商品卡片和详情页提供购买数量输入，范围为 1 到当前库存。
- 购买事件传递 `{ product, quantity }`，`App.vue` 再次校验数量必须是大于 0 的整数。
- 请求体为 `{ productId, quantity }`，例如购买 2 件时提交 `{"productId":1,"quantity":2}`。
- 创建订单期间所有购买按钮锁定，避免快速连点产生重复订单。
- 下单成功后递增 `ordersRefreshKey`、跳转 `/orders` 并提示订单号；订单页挂载或收到刷新信号时会重新调用 `getOrders()`。

### 订单与支付

- 订单页调用 `getOrders()`，兼容 `data.items` 和直接返回数组两种情况。
- 订单卡片显示订单号、商品名、数量、`formatPrice(totalAmount)` 和中文状态：`created → 待支付`、`paid → 已支付`。
- 待支付订单可选择支付宝（`alipay`）或微信（`wechat`）。
- 点击后调用 `createPayment({ orderId, method })`，取得 `paymentId`。
- 使用 `setInterval` 每 1 秒调用一次 `getPayment(paymentId)`，最多 10 次。
- `processing` 时保持“支付中…”；`paid` 时停止轮询、显示成功并更新订单状态；达到上限后停止并提示“支付状态未确认，请稍后在订单列表查看”。
- 页面卸载时通过 `onUnmounted`、`cancelled`、`pollToken` 和 `clearInterval` 清理轮询，并阻止在途请求返回后继续修改状态。
- 已支付订单的支付按钮永久禁用；Mock 接口自身也会对重复支付返回 HTTP 409 和“订单已支付，请勿重复支付”。

### 商品详情与标题清洗

- `/products/:id` 由详情组件根据路由参数自行请求，不依赖从列表页传参，因此可以直接在新标签页打开。
- `/products/9999` 等不存在商品会显示“商品不存在”，并提供重试和返回入口。
- Mock 数据中存在把 `<script>` 标签混入标题的情况。展示层通过 `normalizeProductTitle()` 清理 script/style 内容、HTML 标签、控制字符和零宽字符。
- `id=24` 的异常标题显示为“二手教材”；`id=23` 这类正常长标题不会被删除，仍由 CSS 单行省略。

## 金额处理

`src/utils/money.js` 不满足于“能选到值”，而是按金额规则处理：

- `yuanToFen(yuan)` 使用 `Math.round(Number(raw) * 100)`，覆盖 `0.01 → 1`、`19.9 → 1990`、`12.5 → 1250`、`1 → 100`、`100 → 10000`。
- 不使用 `parseInt`，因为它会丢掉小数：`parseInt('19.9')` 得到 `19`，`parseInt('0.01')` 得到 `0`。
- 不直接提交 `yuan * 100`，因为 JavaScript 浮点误差可能得到 `1989.9999999999998` 一类非整数结果。
- `fenToYuan()` 保留两位小数；`formatPrice()` 返回带 `¥` 的展示字符串。
- `formatPrice(0)` 返回 `¥0.00`；脏金额用 `Number.isFinite` 兜底，不出现 `NaN`。

## API 封装

`src/api/index.js` 中集中定义了基础地址：

```js
export const API_BASE_URL = 'http://127.0.0.1:3001/api'
```

如果 Mock 端口改变，只需要改这一处。统一请求函数会解析 `{ code, message, data }`：

- `code === 0`：返回 `data`。
- 业务失败：抛出带后端 `message` 的 `Error`。
- HTTP 非 2xx：优先读取响应体中的 `message`，便于页面直接展示。

目前导出的接口函数：

```js
getProducts({ page, pageSize } = {})
getProduct(id)
createProduct(payload)
updateProductStatus(id, status)
createOrder(payload)
getOrders()
createPayment({ orderId, method })
getPayment(paymentId)
```

## 验收对应关系

| 验收项 | 状态 | 说明 |
|---|---|---|
| L0-1 商品列表 | 已实现 | 接口渲染、金额格式、缺货灰化、状态徽标、长标题省略 |
| L0-2 发布表单 | 已实现 | 元输入、分类 5 项、整数分提交 |
| L0-3 发布反馈 | 已实现 | toast、字段重置、父组件刷新 |
| L0-4 上下架 | 已实现 | 卡片操作、PATCH 请求锁、成功同步状态、失败显示 message |
| L0-5 加载/空态 | 已实现 | 四种状态互斥，空态可清空筛选 |
| L1-6 下单 | 已实现 | 数量选择、库存上限、请求体使用真实 quantity、成功刷新订单 |
| L1-7 订单列表 | 已实现 | 订单五项字段、中文状态、金额格式、新订单可见 |
| L1-8 金额处理 | 已实现 | 元/分转换、两位小数展示、多件订单金额由后端整数分计算 |
| L1-9 表单校验 | 已实现 | 标题、价格、库存校验均在前端拦截 |
| L2-10 支付轮询 | 已实现 | 1 秒轮询、10 次上限、processing 不提前成功、卸载清理 |
| L2-11 防重复提交 | 已实现 | 发布锁、单商品上下架锁、全局下单锁 |
| L2-12 自动化测试 | 已实现（单测） | Vitest 覆盖金额转换、展示兜底和标题清洗，共 15 个用例 |
| L2-13B 路由详情 | 已实现 | `/products/:id`、直接打开、返回、商品不存在提示 |

## 自检与验证

运行单元测试：

```powershell
npm test
```

本次结果：

```text
 RUN  v5.1.1 D:/Projects/personal/lzu_internship_preliminary/frontend

 Test Files  2 passed (2)
      Tests  15 passed (15)
   Duration  235ms
```

运行生产构建：

```powershell
npm run build
```

本次结果：

```text
vite v8.3.8 building client environment for production...
✓ 44 modules transformed.
dist/index.html                   0.47 kB │ gzip:  0.33 kB
dist/assets/index-98yttaWV.css   22.01 kB │ gzip:  3.92 kB
dist/assets/index-CCwSLHrO.js   124.31 kB │ gzip: 46.10 kB
✓ built in 153ms
```

另使用临时 Playwright 脚本做过浏览器全链路验证，主要结果：

- 商品列表加载 24 条数据，页面无 `NaN` / `undefined`。
- 长标题保持单行省略；异常标题清洗后显示“二手教材”，不执行脚本。
- 51 字标题校验、`19.999` 价格和 `1.5` 库存均在字段下方显示错误。
- `19.9` 元发布时实际请求 `price: 1990`，成功后表单重置并刷新列表。
- 同一商品快速连点上下架只产生 1 次 PATCH。
- 数量 2 下单请求体为 `{"productId":1,"quantity":2}`，新订单显示 `×2` 和 `¥398.00`。
- 快速连点购买只产生 1 次 POST。
- 支付时观察到 `processing` 后变为 `paid`，成功前不会提前结束轮询。
- 离开订单页后 5.2 秒内新增支付查询为 0。
- `/products/1` 可直接打开，`/products/9999` 显示“商品不存在”。
- 单独直接请求重复支付接口时返回 HTTP 409 和“订单已支付，请勿重复支付”；该接口保护不等于页面上仍可点击已支付订单的按钮。

浏览器验证脚本放在临时目录，不在仓库内；仓库保留的是 Vitest 单元测试、构建配置和截图。

## 截图

- [当前商品列表](screenshots/product-list-current.png)
- [当前订单列表](screenshots/order-list-current.png)
- [支付完成后的当前订单列表](screenshots/order-list-paid-current.png)
- [发布表单校验](screenshots/product-form-empty-errors.png)
- [发布成功](screenshots/product-form-success.png)
- [空筛选结果](screenshots/product-list-empty.png)
- [商品详情](screenshots/product-detail.png)
- [商品不存在](screenshots/product-detail-404.png)
- [异常标题清洗后的列表](screenshots/product-title-cleaned-list.png)
- [异常标题清洗后的详情](screenshots/product-title-cleaned-detail.png)

## 已知限制

1. 下单成功后跳转订单页；返回商品列表时会重新拉取商品，但不会在离开前的列表实例上原地扣减库存。
2. 当前自动化测试是 Vitest 单元测试，未提交组件级或完整 Playwright E2E 测试套件。
3. 搜索只过滤当前 `pageSize: 50` 的商品，没有分页控件、服务端搜索或输入防抖。
4. 没有登录鉴权、图片上传、富文本编辑和退款流程。
5. Mock 数据保存在内存中，重启服务或调用 `_reset` 会恢复初始数据。
6. 使用 `createWebHistory()`，生产静态服务器需要配置 SPA History fallback，否则刷新 `/orders` 或 `/products/1` 可能 404；本地 Vite 开发服务器已正确处理。
7. 标题清洗是展示层修复，不改动题目提供的 Mock 数据；生产环境仍应在服务端做输入校验，并继续使用 `{{ }}` 文本插值。
