# 校园好物市集 · 前端

这是一个基于 Vue 3 + Vite 的校园商品交易前端，对接 `mock/server.mjs` 提供的模拟接口。当前已经打通商品浏览与筛选、独立页面发布商品、商品上下架、数量下单、订单列表、支付轮询和商品详情查看等链路；商品列表优先识别明确同款；无法识别同款时，再按稳定商品类型或具体分类把相近购买意图归为一类，组内仍以商品编号区分每件商品，并显示卖家名称。

## 项目文档

文档阅读顺序如下，建议先确认约束，再查接口和设计取舍：

| 顺序 | 文档 | 用途 |
|---|---|---|
| 1 | [README](README.md) | 启动方式、页面路由、验收对应关系和已知限制 |
| 2 | [项目约束与实现边界](constraint.md) | 核心约定、文档与代码阅读顺序、改动前检查表 |
| 3 | [代码接口说明](CODE_INTERFACE.md) | 各层代码职责、API 签名、数据结构、组件 props/emits 和数据流 |
| 4 | [设计说明](DESIGN.md) | 模块拆分、关键取舍和异步流程 |
| 5 | [提交说明](SUBMISSION.md) | 完成情况、验证证据和提交清单 |
| 6 | [AI 使用说明](AI_USAGE.md) | AI 参与过程、验证方式与未采用建议 |

## 技术栈

| 项 | 使用方案 |
|---|---|
| 运行时 | Node.js >= 22.12（`frontend/.nvmrc` 固定为 22.12.0） |
| 框架 | Vue 3.5 |
| 构建工具 | Vite 8 |
| 路由 | vue-router 4（History 模式） |
| 语言 | JavaScript |
| 样式 | 原生 CSS，组件内 scoped style |
| 单元测试 | Vitest 5 |
| 代码质量 | ESLint 10 + Prettier 3 |
| 持续集成 | GitHub Actions 执行 `npm ci` 与 `npm run check` |
| 数据服务 | `mock/server.mjs`，零额外依赖 |

## 快速启动

环境要求：Node.js >= 22.12（推荐使用 `frontend/.nvmrc` 中的 22.12.0），npm >= 10。

在仓库根目录执行一次依赖安装：

```powershell
cd frontend
npm ci
```

需要调整依赖时使用 `npm install` 更新 `package-lock.json`。

先启动 Mock API（终端 A）：

```powershell
cd frontend
node mock/server.mjs
```

再启动前端（终端 B）：

```powershell
cd frontend
npm run dev
```

默认地址：

- 前端：<http://localhost:5173>
- Mock API：<http://127.0.0.1:3001/api>

前端默认使用同源 `/api` 作为 API 基础地址。Vite 的 `server.proxy` 和 `preview.proxy` 会把 `/api` 转发到 `http://127.0.0.1:3001`。需要覆盖时，复制 `.env.example` 为 `.env` 并设置 `VITE_API_BASE_URL`；前端与 API 不同域时可填写完整地址。

如果需要恢复 Mock 初始数据，可在服务运行时执行：

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:3001/api/_reset
```

Mock 数据保存在内存中，重启 `mock/server.mjs` 会恢复初始状态。不要修改 `mock/` 目录。

## 页面与路由

| 路径 | 页面 | 说明 |
|---|---|---|
| `/` | `ProductListView` | 商品列表、标题搜索、上下架和购买入口 |
| `/products/new` | `ProductCreateView` | 独立的上架商品页面；提交表单并在成功后查看新商品 |
| `/orders` | `OrderListView` | 订单列表、支付宝/微信支付、支付状态轮询 |
| `/products/:id` | `ProductDetailView` | 商品详情；直接打开 URL 也会自行请求数据；不存在时显示“商品不存在” |
| `/:pathMatch(.*)*` | `NotFoundView` | 未匹配路径的 404 页面，提供返回商品列表入口 |

`App.vue` 顶部提供“商品列表 / 上架商品 / 我的订单”三个入口。“上架商品”有独立高亮状态；商品详情仍归在“商品列表”入口下。每条路由通过 `meta.title` 设置浏览器标题，格式为 `${title} · 校园好物市集`。静态路径 `/products/new` 必须先于 `/products/:id` 注册，且已有路由单测锁定这一优先级。

## 目录结构

```text
frontend/
├── .env.example                # API 地址配置示例
├── .nvmrc                      # Node.js 版本
├── .prettierignore
├── .prettierrc.json
├── eslint.config.js
├── mock/                       # 题目提供的 Mock 服务，未修改
├── screenshots/                # 验收截图
├── src/
│   ├── api/index.js            # API 基础地址与统一请求封装
│   ├── api/index.test.js       # API 请求封装单元测试
│   ├── components/
│   │   ├── ProductGroupCard.vue # 聚合组摘要、折叠列表和组级权限
│   │   ├── ProductCard.vue     # 单件商品展示、数量输入、购买和上下架事件
│   │   ├── ProductForm.vue     # 商品发布表单与字段校验
│   │   └── OrderCard.vue       # 订单展示与支付入口
│   ├── router/
│   │   ├── routes.js           # 路由表，便于单独验证匹配优先级
│   │   ├── index.js            # 路由实例、页面标题与 404
│   │   └── index.test.js       # /products/new 与详情/404 匹配测试
│   ├── utils/
│   │   ├── money.js            # 元/分转换与金额展示
│   │   ├── money.test.js       # 金额单元测试
│   │   ├── product.js          # 商品标题清洗
│   │   ├── product.test.js     # 标题清洗单元测试
│   │   ├── quantity.js         # 购买数量与库存校验
│   │   ├── quantity.test.js    # 数量校验单元测试
│   │   ├── productForm.js      # 商品表单校验与码点计数
│   │   ├── productForm.test.js # 表单校验单元测试
│   │   ├── permissions.js      # 角色常量、演示会话与商品管理权限判断
│   │   ├── permissions.test.js # 商品归属与角色边界测试
│   │   ├── productGroups.js    # 相近购买意图分组与组级权限摘要
│   │   └── productGroups.test.js # 相似商品分组、卖家计数与权限三态测试
│   ├── views/
│   │   ├── ProductListView.vue   # 商品浏览、搜索、聚合、上下架和购买
│   │   ├── ProductCreateView.vue # 独立上架页与发布成功反馈
│   │   ├── ProductDetailView.vue
│   │   ├── OrderListView.vue
│   │   └── NotFoundView.vue
│   ├── App.vue                   # 导航、权限演示切换、全局下单协调
│   ├── main.js
│   └── style.css
├── package-lock.json
├── package.json
└── vite.config.js
```

仓库根目录的 `.github/workflows/frontend-ci.yml` 负责 Lint、格式检查、单测和构建。

## 已实现功能

### 商品列表

- 进入页面调用 `getProducts({ pageSize: 50 })`，使用返回的 `data.items` 渲染，不硬编码商品。
- 商品列表按四层规则分组：先按 `spuId` 识别明确同款；没有同款编号时，按标题中的标准商品类型归类；再退到具体分类；仍无法可靠识别时，每件商品独立成组。多商品组默认折叠，单商品组默认展开。
- 组标题不取第一件商品、最长标题或出现次数最多的标题：标准类型和具体分类使用固定名称；明确同款优先使用标准类型，否则使用共同标题前缀；无法识别时使用该商品清洗后的标题。
- 配件词只有与主商品词重叠时才排除，例如“耳机收纳盒”不会并入“耳机”，但“耳机含收纳盒”仍属于耳机商品。
- 组头展示商品件数、卖家信息数、价格区间、库存合计和在售/可购买/下架状态；多商品组额外展示组内出现的特点标签，展开后每件商品仍独立展示。
- 卡片显示标题、`formatPrice(price)`、商品编号、库存、分类、卖家以及“在售 / 已下架”徽标；卖家取 `sellerName`，缺失时显示“匿名卖家”。
- 库存为 0 的卡片整体灰化、显示“缺货”、购买按钮禁用。
- 已下架商品保留在列表中用于查看状态和进入详情，但购买按钮与数量输入禁用，按钮文案为“已下架”。
- 过长标题使用单行省略，不撑破卡片布局。
- 支持按标题前端过滤，并提供“清空筛选”按钮。
- 页面使用 `loading / error / empty / ready` 互斥状态，分别显示加载、错误、空结果和列表内容。
- 每次加载分配请求 ID，旧响应不会覆盖新结果；组件卸载时使在途请求失效。
- 商品标题只通过 Vue 的 `{{ }}` 渲染，没有使用 `v-html`。

### 上架商品（独立页面）

- 页面路由为 `/products/new`，顶部导航和商品列表页的“上架商品”按钮都可进入；商品列表不再嵌入发布表单。
- 字段包括标题、价格、库存、分类和描述。
- 分类固定为 5 项：数码、图书、服饰、食品、文具。
- 标题校验 1~50 字；价格必填、大于 0、最多两位小数；库存为 0~999 的整数；描述选填且最多 200 字。
- 五个字段的错误分别显示在对应字段下方，不使用 `alert`；描述输入区实时显示 `x/200` 字数。
- 提交前通过 `yuanToFen()` 将元金额转换为整数分。
- 提交中按钮禁用并显示“提交中…”，避免重复提交。
- 成功后显示 toast、重置 5 个字段，并在页面内展示新商品标题、商品 ID、“查看详情”和“继续上架”入口；再次进入商品列表时会重新请求，因此新商品可见。
- 失败时展示后端返回的 `message`，按钮恢复可点。

### 商品上下架

- 在售商品显示“下架”，已下架商品显示“上架”，请求中显示“处理中…”并禁用按钮。
- 调用 `updateProductStatus(id, status)`；成功后只更新对应商品的徽标和按钮状态。
- `ProductListView` 使用 `statusUpdatingIds` 按商品 ID 加锁，同一商品快速连点只会产生一次 PATCH。
- 请求前调用 `canManageProduct(product, session)`：卖家只能管理 `sellerId` 与自身用户 ID 一致的商品，管理员可管理全部商品；无权限时卡片显示“无管理权限”并禁用按钮。
- 已下架商品会立即禁用购买按钮和数量输入，按钮文案为“已下架”；仍可通过标题进入详情查看，但详情页同样不可购买。

### 相似商品、卖家与权限演示

- 商品唯一身份是 `id`，标题只是搜索和展示字段。只有能识别为相近购买意图的商品才进入同一展示组；无法可靠识别的同名商品仍各自成组。展示分组不得合并商品、卖家、库存或权限。
- 页面级和组级均显示权限摘要，界面使用“全部可管理 / 部分可管理 / 仅浏览”，内部再区分全部、部分和无权限；会话或商品归属变化后由 computed 重新计算。
- 权限摘要只改善体验，操作前仍逐条调用 `canManageProduct()`，最终由服务端重新鉴权；不能按聚合组 key 或标题授权。
- 商品卡片和详情页都显示“商品编号”；卖家优先显示 `sellerName`，缺失时显示“匿名卖家”。没有有效 `sellerId` 时不能据此判定卖家归属，管理员仍可管理全部商品。
- `sellerName` 只是展示名称，不能用于判定商品归属。`canManageProduct()` 只接受有效会话；卖家分支要求商品的 `sellerId` 与当前用户 ID 完全一致，管理员分支可管理全部商品。
- 顶部提供“演示角色”切换器，可在游客、买家、卖家和管理员之间切换；选择卖家时显示“卖家编号”输入框。切换后，页面级、组级和单件商品权限由 computed 自动重算。
- 游客和买家显示“仅浏览”；管理员显示“全部可管理”；卖家只在商品 `sellerId` 与会话 ID 精确匹配时显示可管理。卖家未填写编号时会话 ID 为空，按失败关闭处理。
- 当前 Mock 商品没有 `sellerId`，因此卖家模式即使填写编号也只会显示“仅浏览”。“部分可管理”由 `permissions.test.js` / `productGroups.test.js` 的合成 `sellerId` 数据覆盖，不在页面中伪造卖家编号。
- 所有演示会话都由 `createDemoSession()` 生成并标记 `isDemo: true`，默认角色为管理员。该切换器只影响商品管理提示和上下架入口，不是登录认证结果，也不改变服务端授权。
- 上架页目前不提交 `sellerId`，Mock 新建商品也没有卖家编号，因此卖家模式无法管理新商品，管理员仍可管理全部商品。真实后端必须从 token / session 推导卖家身份，不能信任客户端提交的 `sellerId` 或角色。
- 前端按钮禁用只改善体验，不是安全边界。即使前端判定通过，服务端仍必须对发布、上下架、下单和支付重新鉴权。

| 角色 | 目标权限 | 当前前端状态 |
|---|---|---|
| 游客 | 浏览商品 | 顶部可切到游客演示，商品页面显示“仅浏览”；尚未实现真实游客会话 |
| 买家 | 浏览、下单、支付、查看自己的订单 | 下单与支付界面已实现，切换买家后商品管理显示“仅浏览”；订单归属和真实登录未实现 |
| 卖家 | 仅管理本人商品 | 可填写演示卖家编号并按 `sellerId` 精确匹配；当前 Mock 商品没有 `sellerId`，实际页面通常显示“仅浏览” |
| 管理员 | 审核、强制下架、处理举报、查看审计日志 | 默认演示管理员可切换任意商品状态；审核、举报和审计能力未实现 |

### 数量选择与下单

- 商品卡片和详情页提供购买数量输入，范围为 1 到当前库存。
- 购买事件传递 `{ product, quantity }`，`App.vue` 通过 `getQuantityError(quantity, stock)` 再次校验正整数、库存异常和数量上限，并拒绝已下架商品。
- 请求体为 `{ productId, quantity }`，例如购买 2 件时提交 `{"productId":1,"quantity":2}`。
- 创建订单期间所有购买按钮锁定，避免快速连点产生重复订单。
- 下单成功后递增 `ordersRefreshKey`、跳转 `/orders` 并提示订单号；订单页挂载或收到刷新信号时会重新调用 `getOrders()`。

### 订单与支付

- 订单页调用 `getOrders()`，实际请求 `/orders?pageSize=50`，兼容 `data.items` 和直接返回数组两种情况。
- 订单列表同样使用请求 ID 防止旧响应覆盖新结果，卸载时使在途请求失效。
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
export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL?.trim() || '/api'
).replace(/\/+$/, '')
```

默认使用同源 `/api`，由 Vite 开发/预览代理转发到 Mock。需要直连其它环境时，在本地 `.env` 中设置 `VITE_API_BASE_URL`；生产部署需配置反向代理，或在构建时提供完整 API 地址。统一请求函数会解析 `{ code, message, data }`：

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
| L0-1 商品列表 | 已实现 | 接口渲染、金额格式、缺货/下架禁购、状态徽标、长标题省略、请求竞态保护 |
| L0-2 发布表单 | 已实现 | 独立 `/products/new` 页面、元输入、分类 5 项、描述 200 字限制、整数分提交 |
| L0-3 发布反馈 | 已实现 | toast、字段重置、新商品 ID、详情入口和“继续上架” |
| L0-4 上下架 | 已实现 | 卡片操作、PATCH 请求锁、成功同步状态、失败显示 message |
| L0-5 加载/空态 | 已实现 | 四种状态互斥，空态可清空筛选 |
| L1-6 下单 | 已实现 | 数量选择、全局正整数/库存上限校验、下架禁购、请求体使用真实 quantity |
| L1-7 订单列表 | 已实现 | 订单五项字段、中文状态、金额格式、新订单可见 |
| L1-8 金额处理 | 已实现 | 元/分转换、两位小数展示、多件订单金额由后端整数分计算 |
| L1-9 表单校验 | 已实现 | 标题、价格、库存和描述长度均在前端拦截 |
| L2-10 支付轮询 | 已实现 | 1 秒轮询、10 次上限、processing 不提前成功、卸载清理 |
| L2-11 防重复提交 | 已实现 | 发布锁、单商品上下架锁、全局下单锁 |
| L2-12 自动化测试 | 已实现（单测） | Vitest 覆盖金额、标题清洗、数量校验、表单校验、商品归属与角色边界、展示聚合与组级权限摘要、API 错误处理和路由优先级，共 8 个文件 89 个用例 |
| L2-13B 路由详情 | 已实现 | `/products/:id`、直接打开、返回、商品不存在提示、全局 404 与动态标题 |

## 自检与验证

推荐一键执行 Lint、格式检查、单测和生产构建：

```powershell
npm run check
```

该命令依次运行 `eslint .`、`prettier --check .`、`vitest run` 和 `vite build`。本次全部通过。

单独运行单元测试：

```powershell
npm test
```

本次结果：

```text
 RUN  v5.1.1 D:/Projects/personal/lzu_internship_preliminary/frontend

 Test Files  8 passed (8)
      Tests  89 passed (89)
   Duration  916ms (import 46%, transform 31%, tests 14%, worker 9%)
```

单独运行生产构建：

```powershell
npm run build
```

本次结果：

```text
vite v8.3.8 building client environment for production...
transforming...
✓ 55 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                   0.47 kB │ gzip:  0.33 kB
dist/assets/index-G3ZytthF.css   32.64 kB │ gzip:  5.45 kB
dist/assets/index-XAAi25Fa.js   144.04 kB │ gzip: 52.37 kB

✓ built in 414ms
```

> 注：`Duration` 与 `built in` 毫秒值会随机器负载波动；验收以 `8 passed (8)`、`89 passed (89)` 和 `55 modules transformed` 为准。

此前使用临时 Playwright 脚本做过浏览器全链路验证，主要结果：

- 商品列表加载 24 条数据，页面无 `NaN` / `undefined`。
- 长标题保持单行省略；异常标题清洗后显示“二手教材”，不执行脚本。
- 51 字标题校验、`19.999` 价格和 `1.5` 库存均在字段下方显示错误。
- 此前在列表内嵌表单阶段验证过：`19.9` 元发布时实际请求 `price: 1990`，成功后表单重置。独立上架页拆分发生在该次浏览器验证之后，当前版本未重新执行浏览器回归。
- 同一商品快速连点上下架只产生 1 次 PATCH。
- 数量 2 下单请求体为 `{"productId":1,"quantity":2}`，新订单显示 `×2` 和 `¥398.00`。
- 快速连点购买只产生 1 次 POST。
- 支付时观察到 `processing` 后变为 `paid`，成功前不会提前结束轮询。
- 离开订单页后 5.2 秒内新增支付查询为 0。
- `/products/1` 可直接打开，`/products/9999` 显示“商品不存在”。
- 单独直接请求重复支付接口时返回 HTTP 409 和“订单已支付，请勿重复支付”；该接口保护不等于页面上仍可点击已支付订单的按钮。

浏览器验证脚本放在临时目录，不在仓库内；仓库保留的是 Vitest 单元测试、构建配置、CI 配置和截图。当前版本的独立上架页、相似商品分组列表、组级权限摘要、商品编号、卖家字段、前端归属判断、路由优先级测试、全局 404、动态页面标题、下架禁购、描述字数限制和边界/错误单测已完成代码与自动化检查，但没有重新执行浏览器回归。

## 截图

以下截图来自历史浏览器验证；其中发布截图属于“商品列表内嵌发布表单”阶段，不含拆分后的独立上架页。

- [此前商品列表](screenshots/product-list-current.png)
- [此前订单列表](screenshots/order-list-current.png)
- [此前支付完成后的订单列表](screenshots/order-list-paid-current.png)
- [拆分前的发布表单校验](screenshots/product-form-empty-errors.png)
- [拆分前的发布成功反馈](screenshots/product-form-success.png)
- [空筛选结果](screenshots/product-list-empty.png)
- [商品详情](screenshots/product-detail.png)
- [商品不存在](screenshots/product-detail-404.png)
- [异常标题清洗后的列表](screenshots/product-title-cleaned-list.png)
- [异常标题清洗后的详情](screenshots/product-title-cleaned-detail.png)

## 已知限制

1. 下单成功后跳转订单页；返回商品列表时会重新拉取商品，但不会在离开前的列表实例上原地扣减库存。
2. 当前自动化测试是 Vitest 单元测试（工具函数、表单校验、商品归属、展示聚合与组级权限摘要、API 请求封装和路由匹配，共 8 个文件 89 个用例），未提交组件级或完整 Playwright E2E 测试套件。
3. 搜索只过滤当前 `pageSize: 50` 的商品，没有分页控件、服务端搜索或输入防抖。
4. 没有真实登录鉴权、图片上传、富文本编辑和退款流程；当前只有顶部可切换的 `isDemo: true` 演示会话和前端体验层权限判断。
5. Mock 数据保存在内存中，重启服务或调用 `_reset` 会恢复初始数据。
6. 使用 `createWebHistory()`，生产静态服务器需要配置 SPA History fallback，否则刷新 `/orders` 或 `/products/1` 可能 404；本地 Vite 开发服务器已正确处理。
7. 标题清洗是展示层修复，不改动题目提供的 Mock 数据；生产环境仍应在服务端做输入校验，并继续使用 `{{ }}` 文本插值。
8. 商品卡片中的卖家名称只是 `sellerName` 展示字段，不是经过认证的卖家身份；缺少 `sellerId` 时不能据此判定商品归属，页面也不会用名称代替编号授权。前端已用 `canManageProduct()` 控制管理按钮，但没有真实登录和服务端授权，不能把演示角色切换当成权限隔离。
9. 当前 Mock 商品没有 `spuId`，页面主要按标准商品类型或具体分类保守归组；24 件 Mock 商品实际显示为 20 组，其中“耳机”2 件、“备考资料”3 件、“教材”2 件，其余多为单件组。固定词表无法覆盖的商品会保持独立，这不是后端分类结果，也不能据此认定商品本质相同或合并卖家、库存、权限。
10. 当前 Mock 商品也没有 `sellerId`，所以卖家模式无法在真实页面命中“部分可管理”；`partial` 仅由测试夹具证明。接入真实会话和 `sellerId` 后才能完整演示卖家只管理本人商品的场景。
