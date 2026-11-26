# 代码接口说明

本文说明当前前端各部分代码的职责、可复用接口、组件输入输出和数据流，供后续维护、追加功能或评审时快速定位。

阅读前先看 [项目约束与实现边界](constraint.md)。稳定约定不在这里重新定义；本文重点回答“代码在哪里、如何调用、改动会影响谁”。

## 1. 总体结构

```text
index.html
  └─ src/main.js
      ├─ src/style.css
      ├─ src/router/index.js
      │   └─ src/router/routes.js
      └─ src/App.vue
          ├─ src/views/ProductListView.vue
          │   ├─ src/components/ProductGroupCard.vue
          │   └─ src/components/ProductCard.vue
          ├─ src/views/ProductCreateView.vue
          │   └─ src/components/ProductForm.vue
          ├─ src/views/ProductDetailView.vue
          ├─ src/views/OrderListView.vue
          │   └─ src/components/OrderCard.vue
          ├─ src/views/NotFoundView.vue
          ├─ src/api/index.js
          ├─ src/api/index.test.js
          ├─ src/router/index.test.js
          ├─ src/utils/money.js / product.js / quantity.js / productForm.js / permissions.js / productGroups.js
          └─ src/utils/money.test.js / product.test.js / quantity.test.js / productForm.test.js / permissions.test.js / productGroups.test.js
```

各层职责：

| 层 | 位置 | 职责 |
|---|---|---|
| 应用入口 | `src/main.js` | 创建 Vue 应用、注册路由、挂载 `#app` |
| 全局壳层 | `src/App.vue` | 顶部导航、全局下单锁、订单刷新信号、全局 toast |
| 路由 | `src/router/routes.js`、`src/router/index.js` | `routes.js` 维护 URL 与页面组件映射；`index.js` 创建路由实例、滚动复位、动态页面标题和 404 兜底 |
| 页面 | `src/views/` | 发起数据请求、维护页面状态、组合组件 |
| 业务组件 | `src/components/` | 展示和局部交互，通过 props/emits 与页面通信 |
| API 层 | `src/api/index.js` | 环境化基础地址、fetch、响应解析、导出接口函数；`index.test.js` 固化请求封装 |
| 工具层 | `src/utils/` | 金额换算、金额展示、标题清洗、数量校验、表单校验、角色与商品归属判断、商品展示聚合及对应单测 |
| Mock 服务 | `mock/server.mjs` | 只读模拟后端，提供业务接口和内存数据 |

## 2. 启动入口与路由接口

### 2.1 `src/main.js`

```js
createApp(App).use(router).mount('#app')
```

职责：

- 挂载根组件 `App.vue`。
- 注册 `vue-router`。
- 引入全局样式 `src/style.css`。

不在这里写业务请求、金额转换或页面状态。

### 2.2 `src/router/routes.js` 与 `src/router/index.js`

路由表单独放在 `src/router/routes.js`，便于在 `src/router/index.test.js` 中无浏览器地验证静态路径与动态参数的匹配优先级。`src/router/index.js` 只负责创建 `createWebHistory()` 路由实例、滚动复位和更新标题。

| 路径 | 路由名 | 组件 | 数据获取 |
|---|---|---|---|
| `/` | `products` | `ProductListView.vue` | `getProducts({ pageSize: 50 })` |
| `/orders` | `orders` | `OrderListView.vue` | `getOrders()` |
| `/products/new` | `product-create` | `ProductCreateView.vue` | 页面加载无请求；表单提交调用 `createProduct()` |
| `/products/:id` | `product-detail` | `ProductDetailView.vue` | `getProduct(route.params.id)` |
| `/:pathMatch(.*)*` | `not-found` | `NotFoundView.vue` | 无请求 |

静态路径 `/products/new` 必须注册在 `/products/:id` 之前，否则 `new` 会被当作商品 ID。`index.test.js` 已固化 `/products/new`、`/products/42`、`/products/new/extra` 和未知路径四条边界。

直接访问详情页时，详情组件会自己读取 `route.params.id` 并请求数据，不依赖列表页传参。每条路由通过 `meta.title` 设置标题，`router.afterEach()` 将 `document.title` 更新为 `${title} · 校园好物市集`；未知路径匹配 `not-found` 路由。

### 2.3 `src/App.vue`

`App.vue` 是全局协调层：

- 渲染顶部导航（商品列表 / 上架商品 / 我的订单）和 `RouterView`。
- 显示明确标注的“演示角色 / 演示身份”切换器；角色变化后通过 `createDemoSession()` 生成新的演示会话，该身份不是认证结果。
- 统一监听所有页面冒泡的 `buy` 事件。
- 调用 `createOrder({ productId, quantity })`。
- 下单成功后递增 `ordersRefreshKey`，跳转 `/orders`。
- 用 `creatingOrder` 和 `orderingProductId` 锁定所有购买按钮。
- 调用 `getQuantityError(quantity, product.stock)` 校验正整数、库存异常和库存上限，并拒绝 `status !== 'on'` 的下架商品。
- 显示全局下单结果提示，3 秒后自动消失。

传给路由页面的公共接口：

```vue
<component
  :is="Component"
  :ordering-product-id="orderingProductId"
  :refresh-key="ordersRefreshKey"
  :session="productSession"
  @buy="handleBuy"
/>
```

| 接口 | 类型 | 消费方 |
|---|---|---|
| `ordering-product-id` | `number \| string \| null` | 商品列表、商品详情，用于标出正在下单的商品 |
| `refresh-key` | `number` | 订单列表，用于下单成功后重新加载订单 |
| `session` | `object \| null` | 商品列表、商品详情；只有商品相关路由传演示会话，其他路由为 `null` |
| `buy` | 事件 | `App.vue`，事件载荷为 `{ product, quantity }` |

### 2.4 权限与演示会话

`src/utils/permissions.js` 定义角色常量、角色中文名、演示会话工厂和商品管理判定。`App.vue` 在顶部提供游客 / 买家 / 卖家 / 管理员切换器；选择卖家时可填写卖家编号，并通过 `createDemoSession()` 生成动态演示会话。只有商品列表和商品详情路由会收到该会话，用于展示和体验上下架权限，不用于登录或接口鉴权。

| 角色 | 目标权限范围 | 当前实现 |
|---|---|---|
| 游客 | 浏览商品 | 可切换到游客演示；商品管理显示“仅浏览” |
| 买家 | 浏览、下单、支付、查看本人订单 | 可切换到买家演示；下单/支付 UI 存在，订单归属未校验 |
| 卖家 | 管理本人商品 | 可输入演示卖家编号；`canManageProduct()` 按 `sellerId` 精确匹配，当前 Mock 商品没有该字段 |
| 管理员 | 审核、强制下架、处理举报、查看审计日志 | 默认管理员演示可切换商品状态；其他管理能力未实现 |

演示会话只控制前端按钮和提示。切换步骤：在顶部选择角色；选择卖家后输入卖家编号；观察页面级、组级和单件商品权限同步变化。游客 / 买家为 `none`，管理员为 `all`，卖家仅在存在匹配 `sellerId` 时为 `partial` 或 `all`。当前 Mock 商品没有 `sellerId`，因此卖家页面实际为 `none`，不能把演示输入的卖家编号当成真实卖家归属。

任何真实写操作都必须在服务端重新校验当前用户、角色和资源归属，不能信任客户端提交的 `role` 或 `sellerId`。

## 3. API 客户端接口

文件：`src/api/index.js`

### 3.1 基础地址

```js
export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL?.trim() || '/api'
).replace(/\/+$/, '')
```

默认值为同源 `/api`，由 `vite.config.js` 的 `server.proxy` / `preview.proxy` 转发到 `http://127.0.0.1:3001`。需要覆盖时使用 `.env` 中的 `VITE_API_BASE_URL`；生产部署必须配置反向代理，或提供完整 API 地址。组件不得自行拼接接口域名。

### 3.2 内部请求函数

```js
async function request(path, options = {})
```

内部流程：

1. 使用 `fetch(`${API_BASE_URL}${path}`)` 请求。
2. 有请求体时自动加入 `Content-Type: application/json`。
3. 尝试解析 JSON；空响应或非 JSON 不会先崩溃。
4. HTTP 非 2xx：抛出 `payload.message` 或 `请求失败（HTTP 状态码）`。
5. 业务响应 `code !== 0`：抛出 `payload.message` 或 `请求失败`。
6. 成功时只返回 `payload.data`，调用方不再处理外层响应包。

`request()` 是私有函数，不应从组件绕过它直接 `fetch`。

### 3.3 导出的 API 函数

| 函数 | HTTP 接口 | 请求内容 | 成功时 `data` | 主要调用方 |
|---|---|---|---|---|
| `getProducts({ page, pageSize } = {})` | `GET /products` | 可选 query：`page`、`pageSize` | `{ total, page, pageSize, items }` | `ProductListView` |
| `getProduct(id)` | `GET /products/:id` | 路径参数 `id` | `Product` | `ProductDetailView` |
| `createProduct(payload)` | `POST /products` | JSON 商品字段 | 新建的 `Product` | `ProductForm` |
| `updateProductStatus(id, status)` | `PATCH /products/:id/status` | `{ status: 'on' \| 'off' }` | `{ id, status }` | `ProductListView` |
| `createOrder(payload)` | `POST /orders` | `{ productId, quantity }` | 新建的 `Order` | `App.vue` |
| `getOrders()` | `GET /orders?pageSize=50` | 固定 `pageSize=50` | `{ total, page, pageSize, items }` | `OrderListView` |
| `createPayment({ orderId, method })` | `POST /payments` | `{ orderId, method }` | 支付单对象，状态通常为 `processing` | `OrderListView` |
| `getPayment(paymentId)` | `GET /payments/:paymentId` | 路径参数 | 支付单对象，状态为 `processing` 或 `paid` | `OrderListView` |

说明：

- `getProducts()` 会忽略值为 `undefined`、`null` 或空字符串的查询参数。
- 商品和支付 ID 会通过 `encodeURIComponent()` 编码。
- 当前 API 层没有 AbortController；卸载后在途请求由页面组件的取消标志、请求 ID 或轮询 token 拦截回写。

### 3.4 `createProduct()` 请求体

```js
{
  title: string,          // 1~50 字
  price: number,          // 整数分，必须由 yuanToFen() 转换
  stock: number,          // 0~999 的整数
  category: string,       // 数码 / 图书 / 服饰 / 食品 / 文具
  description: string     // 可为空；表单与 Mock 均限制最多 200 字
}
```

当前 `createProduct()` 不提交 `sellerId` 或 `sellerName`。Mock 会把新商品的 `sellerName` 固定写为“我”，且不生成 `sellerId`，所以前端无法认定卖家归属，卖家模式不能管理该商品；管理员仍可管理。真实后端应从会话推导卖家身份并写入服务端数据，不能接受客户端伪造卖家编号。

### 3.5 `createOrder()` 请求体

```js
{
  productId: number | string,
  quantity: number        // 大于 0 的整数
}
```

商品单价、库存判断、订单号和 `totalAmount` 都由 Mock 处理，前端不得自行计算并提交总额。

### 3.6 `createPayment()` 请求体

```js
{
  orderId: number | string,
  method: 'alipay' | 'wechat'
}
```

成功响应至少包含 `paymentId`。订单页依赖该值启动支付状态轮询。

### 3.7 错误处理约定

所有导出的 API 函数在失败时抛出 `Error`：

```js
try {
  const data = await getProducts({ pageSize: 50 })
  // 此处 data 是已经解包后的 { total, page, pageSize, items }
} catch (error) {
  const message = error instanceof Error
    ? error.message
    : '请求失败，请稍后重试'
}
```

- 错误分支（业务 code 非 0、HTTP 非 2xx、空响应/非 JSON、网络错误）已由 `src/api/index.test.js` 覆盖，修改请求封装时需同步更新。

页面必须把 `message` 展示给用户，不能让错误只停留在 Console。

## 4. 数据结构

### 4.1 `Product`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 商品唯一编号；同名商品靠它区分，不能把标题当唯一键 |
| `title` | string | 商品标题；只是搜索和展示字段，展示前经过 `normalizeProductTitle()` |
| `price` | number | 单价，整数分 |
| `stock` | number | 库存，0~999 |
| `category` | string | 五种分类之一 |
| `status` | `'on' \| 'off'` | 在售 / 已下架 |
| `spuId` | `number \| string \| undefined` | 可选标准商品编号；存在时仅用于识别明确同款。当前 Mock 数据没有该字段 |
| `sellerId` | `number \| string \| undefined` | 可选卖家用户 ID；卖家管理权限只按该字段与当前用户 ID 是否一致判断。当前 Mock 数据和新发布商品均没有该字段 |
| `sellerName` | string | 卖家展示名称；缺失时卡片和详情显示“匿名卖家”。该字段不是认证身份，也不能作为权限依据；没有有效 `sellerId` 时不能据此判定商品归属 |
| `description` | string | 商品描述 |

商品标题可以重复，`id` 才是商品和数据记录的唯一身份。多个卖家发布相似商品时，展示层只在能识别明确同款、标准商品类型或具体分类时归组；每件商品的编号、卖家名称、价格、库存、状态和权限必须独立保留，无法可靠识别的同名商品不得强行合并。

### 4.2 `ProductGroup` 聚合展示结构

`buildProductGroups()` 返回的组只存在于展示层，不是后端商品实体，也不能作为权限资源。

| 字段 | 类型 | 说明 |
|---|---|---|
| `key` | string | 展示分组键，形如 `spu:*`、`type:*`、`category:*` 或 `product:*`；不能用于权限判断 |
| `groupingMode` | `'spu' \| 'type' \| 'category' \| 'product'` | 分组依据；`product` 表示无法可靠识别购买意图时保留为单件商品组 |
| `title` | string | 稳定组标题：明确同款用标准类型或共同标题前缀；标准类型和具体分类用对应名称；无法识别时用该商品的清洗后标题 |
| `type` | `string \| null` | 识别出的标准商品类型，如“耳机”“电脑包”；未识别时为 `null` |
| `tags` | `string[]` | 多商品组中出现的商品特点标签，如“降噪”“蓝牙”“二手”；最多 6 个 |
| `products` | `Product[]` | 组内全部独立商品，保留原对象 |
| `productCount` | number | 组内商品件数 |
| `sellerCount` | number | 卖家信息数；有 `sellerId` 的按卖家编号去重，缺少编号的商品各自计数 |
| `identifiedSellerCount` | number | 带有效 `sellerId` 的不同卖家数；“有编号”不等于已认证 |
| `missingSellerIdCount` | number | 缺少有效 `sellerId` 的商品数 |
| `priceMin` / `priceMax` | `number \| null` | 有效价格区间，单位分；无有效价格时为 `null` |
| `totalStock` | number | 组内有效库存之和，异常库存按 0 计 |
| `onSaleCount` | number | `status === 'on'` 的商品数 |
| `availableCount` | number | 在售且库存大于 0 的商品数 |
| `offShelfCount` | number | 非在售商品数 |
| `soldOutCount` | number | 库存小于等于 0 的商品数 |
| `permissions` | object | 当前会话的组级权限摘要，结构见 5.6 |

组标题生成规则：

- 明确同款：优先使用标题识别出的标准类型；没有类型时使用组内共同标题前缀；仍无效时显示“同款商品”。
- 标准类型和具体分类：使用固定类型名或分类名，不以组内第一件商品、最长标题或出现次数最多的标题命名。
- 无法可靠识别：使用该商品清洗后的标题，并单独成组；卖家名称、卖价、库存和商品编号不参与组标题生成。

配件边界：配件与主商品分开归组，例如“耳机收纳盒”“键盘膜”“显示器支架”不并入耳机、键盘或显示器。只有当配件词与标题最前面的主商品词重叠时才排除该类型，因此“耳机含收纳盒”仍归入耳机。

### 4.3 `Order`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 订单 ID |
| `orderNo` | string | 展示用订单号 |
| `productId` | number | 下单商品 ID |
| `productTitle` | string | 下单时商品标题快照 |
| `quantity` | number | 购买数量 |
| `unitPrice` | number | 下单时单价，整数分 |
| `totalAmount` | number | 服务端计算总额，整数分 |
| `status` | `'created' \| 'paid'` | 待支付 / 已支付 |
| `createdAt` | string | 创建时间 |
| `paidAt` | `string \| null` | 支付时间 |

### 4.4 `Payment`

| 字段 | 类型 | 说明 |
|---|---|---|
| `paymentId` | string | 支付单号 |
| `orderId` | number | 关联订单 ID |
| `amount` | number | 支付金额，整数分 |
| `method` | `'alipay' \| 'wechat'` | 支付方式 |
| `status` | `'processing' \| 'paid'` | 支付状态 |
| `paidAt` | `string \| null` | 支付完成时间 |

### 4.5 分页对象

商品和订单列表接口当前都返回：

```js
{
  total: number,
  page: number,
  pageSize: number,
  items: object[]
}
```

页面必须从 `items` 读取列表，不能把外层分页对象当数组。

## 5. 工具函数接口

### 5.1 `src/utils/money.js`

| 函数 | 签名 | 行为 |
|---|---|---|
| `yuanToFen` | `yuanToFen(yuan) -> number` | 元字符串/数字转整数分；空值抛“金额不能为空”，非字符串/数字抛格式错误；只接受 `/^[0-9]+(?:\.[0-9]{1,2})?$/`，零/负数抛“金额必须大于 0”，超安全整数抛“金额超出可处理范围”；内部使用 `Math.round(Number(raw) * 100)` |
| `fenToYuan` | `fenToYuan(fen) -> string` | 分转固定两位小数的元字符串；非有限数或负数按 0 兜底 |
| `formatPrice` | `formatPrice(fen) -> string` | 返回 `¥${fenToYuan(fen)}` |

示例：

```js
yuanToFen('19.9')   // 1990
fenToYuan(199)      // '1.99'
formatPrice(0)      // '¥0.00'
formatPrice(9999999) // '¥99999.99'
```

### 5.2 `src/utils/product.js`

```js
normalizeProductTitle(value, fallback = '未命名商品') -> string
```

处理顺序：

1. 非字符串返回 `fallback`。
2. 移除 script/style 整块内容。
3. 移除其它 HTML 标签。
4. 移除控制字符和零宽字符。
5. 合并连续空白并去除首尾空白。
6. 清理后为空时返回 `fallback`。

该函数只用于展示，不修改接口返回的原始商品数据。模板仍必须使用 `{{ }}`。

### 5.3 `src/utils/quantity.js`

```js
getQuantityError(quantity, stock) -> string
```

- 返回空字符串表示校验通过；返回中文错误文案表示不可提交。
- `quantity` 只接受 `>=1` 的安全整数；字符串只接受 `/^[0-9]+$/`，拒绝 `'1e2'`、`'0x10'`、`'+1'`、`'1.0'`、`true`、数组和对象。
- `stock` 数字需有限且 `>=0` 后向下取整；字符串只接受十进制数字/小数并向下取整，拒绝 `null`、空串、负数、`NaN` 和 `Infinity`。
- 数量不能超过向下取整后的库存；库存为 0 时，任何正数都返回超库存错误。
- 错误文案保持 `购买数量必须是正整数` / `商品库存信息异常` / `购买数量不能超过库存（N 件）`。
- `App.vue` 在全局下单入口调用它；`quantity.test.js` 固化脏类型、科学计数法、安全整数、字符串库存、超库存、零库存和异常库存规则。

### 5.4 `src/utils/productForm.js`

```js
PRODUCT_CATEGORIES -> string[]
countCharacters(value) -> number
validateProductForm(form) -> { valid: boolean, errors: Record<string, string> }
```

- `PRODUCT_CATEGORIES` 固定为 5 项：数码、图书、服饰、食品、文具。
- `countCharacters` 按 Unicode 码点计数，避免 emoji 等代理对字符被拆成两个长度。
- `validateProductForm` 集中校验标题（1~50 字）、价格（>0 且最多两位小数）、库存（0~999 整数）、分类和描述（最多 200 字），返回 `valid` 与各字段错误文案。
- `ProductForm.vue` 引用该模块；`productForm.test.js` 固化 emoji 码点、标题/价格/库存/描述边界。

### 5.5 `src/utils/permissions.js`

```js
USER_ROLES // { visitor, buyer, seller, admin }
ROLE_LABELS // 角色中文名
createDemoSession(role = USER_ROLES.admin, sellerId = '') -> Readonly<Session>
DEMO_SESSION // 默认管理员演示会话：{ id: 'demo-admin', name: '演示管理员', role: 'admin', isDemo: true }
canManageProduct(product, session) -> boolean
```

`canManageProduct()` 规则：

- 无会话、会话缺少有效用户 ID、游客或买家：拒绝。
- 管理员：可管理任意商品。
- 卖家：仅当商品存在有效 `sellerId`，且与 `session.id` 完全一致时允许；数字 `42` 和字符串 `'42'` 视为同一 ID。
- `sellerId` 缺失、空白，或只有同名 `sellerName`：拒绝。
- 函数只用于前端体验控制；服务端必须重新执行同样的归属校验。

`createDemoSession()` 会校验角色；未知角色降级为游客。卖家编号会去除首尾空白，未填写时保持空 ID，从而由 `canManageProduct()` 按失败关闭处理。`permissions.test.js` 固化无会话、游客/买家、卖家本人/他人、数字字符串 ID、同名不同人、空 `sellerId`、管理员、演示会话、默认管理员、卖家编号清洗、空编号和未知角色降级等边界。

### 5.6 `src/utils/productGroups.js`

```js
buildProductGroups(products, session = null) -> ProductGroup[]
summarizeGroupPermissions(products = [], session = null) -> {
  role, roleLabel, manageableCount, totalCount,
  scope: "all" | "partial" | "none",
  scopeLabel, canManageAll, permissionLabel
}
```

- `buildProductGroups()` 仅接受数组；非数组、`null` 和 `undefined` 返回空数组。
- 分组按购买意图而不是标题完全相同来判断：先使用 `spuId` 识别明确同款；没有时从清洗后的标题匹配固定标准商品类型，再退到非宽泛的具体分类。标准词按“最长、最具体”优先，例如“笔记本内胆包”先命中“电脑包”而不是“笔记本”。
- 商品身份始终是 `id`；重复 `id` 只保留第一条；没有可靠类型或具体分类时，每件商品独立成组，不按同名强行聚合。
- 聚合只计算展示摘要，不合并商品身份、卖家信息、库存所有权或权限。
- 配件与主商品分开；主商品标题提到随附配件时仍归入主商品，例如“英语听力耳机含收纳盒”不会因为出现“收纳盒”而被排除。
- `summarizeGroupPermissions()` 逐条调用 `canManageProduct()`，再根据覆盖比例返回 `all / partial / none`。
- 组标题由稳定规则决定，不取第一件商品标题、最长标题或出现次数最多的标题；卖家、价格、库存和商品编号不参与标题生成。
- `permissions.test.js` 之外，`productGroups.test.js` 固化明确同款优先、不同标题耳机归组、笔记本与内胆包分离、配件排除、随附配件保留、未知商品独立、具体分类回退、卖家信息计数、异常价格库存和权限三态。

## 6. 业务组件接口

### 6.1 `ProductForm.vue`

职责：商品发布表单、字段校验、元转分、提交锁和组件内成功提示。页面级成功反馈由父组件 `ProductCreateView.vue` 负责。

**Props**：无。

**Emits**：

| 事件 | 载荷 | 触发时机 |
|---|---|---|
| `created` | 新建的 `Product` | `createProduct()` 成功且表单已重置后 |

内部关键状态：

| 状态 | 作用 |
|---|---|
| `form` | 标题、价格、库存、分类、描述 |
| `errors` | 标题、价格、库存、分类、描述五个字段各自的错误文案 |
| `submitting` | 提交锁，防止重复 POST |
| `submitError` | 后端或网络错误提示 |
| `toastMessage` | 组件内“商品发布成功” toast；不负责父页面刷新 |

校验规则：

- 标题：去空白后 1~50 个 Unicode 字符。
- 价格：必填、匹配最多两位小数、数值大于 0。
- 库存：必填、非负整数、最大 999。
- 分类：必须属于固定五项。
- 描述：选填，去除首尾空白后最多 200 个 Unicode 字符，并实时显示 `x/200`。
- 校验失败时不发送请求。
- 校验规则由 `src/utils/productForm.js` 的 `validateProductForm()` 集中提供，`ProductForm.vue` 只负责绑定表单与展示错误。

父组件接入方式：

```vue
<ProductForm @created="handleCreated" />
```

### 6.2 `ProductCard.vue`

职责：展示单个商品、输入购买数量、触发购买和上下架事件。

**Props**：

| Prop | 类型 | 默认值 | 作用 |
|---|---|---|---|
| `product` | Object | 必填 | 商品数据 |
| `ordering` | Boolean | `false` | 当前商品是否正在下单 |
| `orderLocked` | Boolean | `false` | 是否有其它下单请求正在进行 |
| `statusUpdating` | Boolean | `false` | 当前商品上下架请求是否进行中 |
| `canManage` | Boolean | `false` | 当前会话是否有权管理该商品；无权限时上下架按钮禁用 |

**Emits**：

| 事件 | 载荷 | 触发时机 |
|---|---|---|
| `buy` | `{ product, quantity }` | 点击购买且库存、锁状态允许 |
| `toggle-status` | `product` | 点击上架/下架；仅在 `canManage === true` 且不在请求中时触发 |

内部行为：

- `quantity` 默认 1，并随库存变化自动限制在有效范围。
- `canBuy = status === 'on' && !soldOut`；下架或缺货时禁用数量输入和购买按钮，下架文案为“已下架”。
- 下架商品的标题仍可进入详情页，详情页继续执行自身的禁购判断。
- 零库存时显示“缺货”、卡片灰化并禁用购买。
- 标题经 `normalizeProductTitle()` 清洗，CSS 负责单行省略。
- 金额仅通过 `formatPrice(product.price)` 展示。
- 元信息展示商品编号、库存、分类和卖家；商品编号来自 `id`，同名商品靠它区分。
- 卖家取 `sellerName`，缺失或空白时显示“匿名卖家”。没有有效 `sellerId` 时界面不猜测归属，管理员权限仍按角色单独判断。
- 没有管理权限时按钮文案为“无管理权限”并禁用；点击处理函数内部也会再次检查 `canManage`，不能只依赖按钮的 disabled 状态。

### 6.3 `ProductGroupCard.vue`

职责：展示一个聚合组、默认折叠策略、组头摘要和组级权限，并把每件商品交给 `ProductCard`。

**Props**：

| Prop | 类型 | 默认值 | 作用 |
|---|---|---|---|
| `group` | Object | 必填 | `buildProductGroups()` 生成的聚合组 |
| `session` | `object \| null` | `null` | 当前会话；用于逐件判断商品的展示权限 |
| `orderingProductId` | `number \| string \| null` | `null` | 当前正在下单的商品 |
| `statusUpdatingIds` | Object | `new Set()` | 正在更新状态的商品 ID 集合 |

**Emits**：

| 事件 | 载荷 | 触发时机 |
|---|---|---|
| `buy` | `{ product, quantity }` | 组内某件商品发起购买 |
| `toggle-status` | `product` | 组内某件商品发起上下架 |

内部行为：

- 单商品组默认展开，多商品组默认折叠。
- 组头展示商品件数、卖家信息数、价格区间、库存合计和状态摘要；多商品组展示标准组名和组内出现的特点标签。
- 组级权限只展示“全部可管理 / 部分可管理 / 仅浏览”摘要；展开后 `ProductCard` 仍按每件商品的 `canManageProduct()` 结果控制按钮。
- 组内说明明确写出“以下商品单独出售，价格、库存和操作互不影响”。

### 6.4 `OrderCard.vue`

职责：展示单个订单、选择支付方式、触发支付事件。

**Props**：

| Prop | 类型 | 默认值 | 作用 |
|---|---|---|---|
| `order` | Object | 必填 | 订单数据 |
| `paying` | Boolean | `false` | 当前订单是否正在支付 |
| `paymentLocked` | Boolean | `false` | 是否已有其它支付流程进行中 |

**Emits**：

| 事件 | 载荷 | 触发时机 |
|---|---|---|
| `pay` | `{ orderId, method }` | 待支付订单点击“去支付” |

内部行为：

- `method` 默认 `alipay`，可切换 `wechat`。
- `created` 显示“待支付”，`paid` 显示“已支付”，未知状态显示“状态未知”。
- 已支付、支付中、其它支付锁定或未知状态均禁用按钮。
- 订单标题同样经过展示清洗，金额使用 `formatPrice(totalAmount)`。

## 7. 页面组件接口

### 7.1 `ProductListView.vue`

**Props**：

| Prop | 类型 | 作用 |
|---|---|---|
| `orderingProductId` | `number \| string \| null` | 标识正在下单的商品 |
| `session` | `object \| null` | 当前前端会话；用于计算每个商品的管理权限 |

**Emits**：

| 事件 | 载荷 | 转发目标 |
|---|---|---|
| `buy` | `{ product, quantity }` | 原样转发给 `App.vue` |

职责：

- 挂载时调用 `getProducts({ pageSize: 50 })`。
- 从 `data.items` 读取列表。
- 按标题做前端筛选。
- 维护互斥的 loading/error/empty/ready 状态。
- 每次加载递增 `loadRequestId`，只允许最新请求回写；卸载时递增 ID，使在途请求失效。
- 请求前调用 `canManageProduct(product, props.session)`，再用 `statusUpdatingIds` 做商品级锁；无权限时提示“仅商品所有者或管理员可以变更商品状态”。
- 将 `filteredProducts` 交给 `buildProductGroups(filteredProducts, session)`，再渲染 `ProductGroupCard`；组内仍逐条渲染 `ProductCard`。
- 向 `ProductCard` 传递 `can-manage`；组头用“同类商品”概括相近购买意图，具体商品仍逐件展示。
- 页面级权限摘要由 `summarizeGroupPermissions()` 汇总当前可见商品，并通过 `aria-live="polite"` 持续播报变化。
- 标题区提供进入 `/products/new` 的“上架商品”入口；列表页不再嵌入发布表单。
- 组件卸载时设置 `cancelled`，阻止旧请求回写。

### 7.2 `ProductCreateView.vue`

**Props**：无。

**Emits**：无；仅接收 `ProductForm` 的 `created` 事件。

职责：

- 承载独立 `/products/new` 页面，渲染 `ProductForm`。
- 收到新商品后保存到 `createdProduct`，展示清洗后的标题、商品 ID、“查看详情”和“继续上架”。
- “查看详情”跳转 `product-detail` 路由；“继续上架”只收起成功提示，表单已由 `ProductForm` 自行重置。
- 不通知 `ProductListView` 刷新，也不直接调用商品列表接口；之后进入列表页时由列表页重新请求。

### 7.3 `ProductDetailView.vue`

**Props**：

| Prop | 类型 | 作用 |
|---|---|---|
| `orderingProductId` | `number \| string \| null` | 标识当前详情商品是否正在下单 |
| `session` | `object \| null` | 当前前端会话；用于生成管理权限提示 |

**Emits**：

| 事件 | 载荷 | 转发目标 |
|---|---|---|
| `buy` | `{ product, quantity }` | 原样转发给 `App.vue` |

职责：

- 监听 `route.params.id`，每次变化都通过 `getProduct()` 独立请求。
- 使用递增 `requestId` 丢弃旧请求响应。
- 不存在或请求失败时显示“商品不存在”或后端 message。
- 下架或缺货时禁用购买。
- 元信息显示商品编号和卖家名称；同名或同类商品仍以商品编号区分，卖家名称不参与权限判断。
- `sellerName` 不参与权限判断；权限提示由 `canManageProduct(product, session)` 得出，并明确真实权限仍须服务端校验。
- 组件卸载时递增 `requestId`，使在途请求失效。

### 7.4 `OrderListView.vue`

**Props**：

| Prop | 类型 | 作用 |
|---|---|---|
| `refreshKey` | Number | 父组件递增后触发订单重新加载 |

**Emits**：无；支付流程由页面内部完成。

职责：

- 挂载时调用 `getOrders()`，实际请求 `/orders?pageSize=50`，兼容 `data.items` 和直接数组。
- 每次订单加载递增 `loadRequestId`，旧响应不能覆盖新结果；卸载时使在途请求失效。
- 处理支付宝/微信支付创建与状态轮询。
- 管理 `pollTimer`、`pollToken`、`cancelled`、`loadRequestId` 和 `requestInFlight`。
- 支付成功后原地将订单状态改为 `paid`。
- 组件卸载时设置 `cancelled = true`，并调用 `stopPolling()`。

## 8. 关键数据流

### 8.1 商品列表加载

```text
ProductListView onMounted
  -> getProducts({ pageSize: 50 })
  -> 记录 loadRequestId
  -> request() 解包 data
  -> 仅最新请求可写回 data.items
  -> filteredProducts
  -> buildProductGroups(filteredProducts, session)
  -> ProductGroupCard
  -> ProductCard
```

### 8.2 商品发布

```text
ProductForm submit
  -> 字段校验
  -> yuanToFen(price)
  -> createProduct(payload)
  -> 成功后 resetForm()
  -> emit('created', product)
  -> ProductCreateView.handleCreated(product)
  -> 展示新商品标题、ID、详情入口和继续上架
```

### 8.3 商品上下架与前端权限判断

```text
ProductCard 点击上架/下架
  -> 检查 canManage
  -> ProductListView.handleToggleStatus()
  -> canManageProduct(product, session)
  -> 无权限：显示错误提示，不发请求
  -> 有权限：statusUpdatingIds 加锁
  -> updateProductStatus(id, nextStatus)
  -> 成功后只更新对应商品状态
```

默认演示管理员因此可以操作全部商品；顶部切换到游客、买家或卖家后会立即重算权限。卖家分支已有 `createDemoSession()` 和 `canManageProduct()` 单测，但没有真实登录、服务端会话和 Mock `sellerId`，尚未构成真实权限隔离。

### 8.4 选择数量并下单

```text
ProductCard / ProductDetailView
  -> emit('buy', { product, quantity })
  -> ProductListView / ProductDetailView 转发
  -> App.handleBuy()
  -> getQuantityError(quantity, product.stock)
  -> 拒绝下架商品并锁定 creatingOrder
  -> createOrder({ productId, quantity })
  -> ordersRefreshKey += 1
  -> router.push('/orders')
```

### 8.5 支付轮询

```text
OrderCard emit('pay', { orderId, method })
  -> OrderListView.handlePay()
  -> createPayment()
  -> paymentId
  -> setInterval(1000ms)
  -> getPayment(paymentId)
  -> processing：继续等待
  -> paid：stopPolling + 更新订单为 paid
  -> 10 次仍未确认：停止并提示
```

### 8.6 订单列表加载

```text
OrderListView onMounted / refreshKey 变化
  -> 递增 loadRequestId
  -> getOrders() = GET /orders?pageSize=50
  -> request() 解包 data
  -> 仅最新请求可写回 data.items
  -> 渲染 OrderCard
```

## 9. Mock 接口索引

> `mock/` 只读。下表用于核对前端调用，不应通过修改 Mock 来适配前端错误。

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/products` | 商品列表；支持 `keyword`、`status`、`category`、`page`、`pageSize` |
| `GET` | `/api/products/:id` | 商品详情；不存在返回 404 |
| `POST` | `/api/products` | 创建商品；金额单位必须为整数分 |
| `PATCH` | `/api/products/:id/status` | 上架/下架 |
| `POST` | `/api/orders` | 创建订单并扣减库存 |
| `GET` | `/api/orders` | 订单分页列表，按新到旧排序 |
| `GET` | `/api/orders/:id` | 订单详情 |
| `POST` | `/api/payments` | 创建支付单，返回 `processing` |
| `GET` | `/api/payments/:paymentId` | 查询支付状态，Mock 约 2.5 秒后转为 `paid` |
| `GET` | `/api/categories` | 分类列表 |
| `POST` | `/api/_reset` | 调试用，恢复初始数据 |

所有业务响应仍遵循 `{ code, message, data }`。

## 10. 后续开发扩展方式

### 10.1 新增 API

1. 在 `src/api/index.js` 中复用 `request()` 增加导出函数。
2. 不要绕过统一错误解析直接写 `fetch`。
3. 在本文“导出的 API 函数”表补充签名、请求体和返回值。
4. 在页面调用处处理 loading、error、请求锁和卸载状态。
5. 补充单元测试或浏览器验证，并同步 `SUBMISSION.md`。

### 10.2 新增组件接口

1. Props 使用明确类型和默认值。
2. 子组件不直接调用需要页面统一协调的接口时，用 emits 把动作交给父组件。
3. 事件载荷保持可序列化对象，例如 `{ product, quantity }`。
4. 在本文“业务组件接口”中记录 props、emits、载荷和触发条件。

### 10.3 新增页面路由

1. 在 `src/router/routes.js` 增加路径和命名路由；`src/router/index.js` 只保留路由实例和全局钩子。
2. 页面自行请求所需数据，支持直接打开 URL；静态路径必须排在可能吞掉它的动态参数路由之前。
3. 在 `src/App.vue` 判断导航激活状态。
4. 在 `src/router/index.test.js` 增加静态/动态路径优先级与 404 用例。
5. 在本文路由表和 README 页面表中同步说明，并设置 `meta.title`。
6. 验证刷新、直接访问、未知路径 404 和浏览器标题。

### 10.4 修改金额或订单字段

1. 先确认接口单位是否仍为整数分。
2. 修改 `money.js` 时同步更新 `money.test.js`。
3. 检查所有调用方，禁止局部出现 `price / 100` 或裸 `toFixed`。
4. 修改订单字段时同步 `OrderCard.vue`、订单页、Mock 字段核对和本文数据结构。
5. 执行 `npm run check` 和关键浏览器流程。

### 10.5 修改支付状态机

1. 先明确新增状态的所有可能取值。
2. 更新 `OrderCard` 的状态文案、按钮禁用条件和未知状态兜底。
3. 更新 `OrderListView` 的轮询结束条件。
4. 确认卸载时 `cancelled`、`pollToken`、`pollTimer` 和 `requestInFlight` 的行为。
5. 在离开页面后观察至少 5 秒，确认没有新增支付查询请求。

## 11. 修改影响矩阵

| 修改位置 | 必须连带检查 |
|---|---|
| `src/api/index.js` | 调用页面、错误提示、`index.test.js`、本文 API 表、Mock 字段 |
| `src/utils/money.js` | `money.test.js`、表单、卡片、详情、订单卡片 |
| `src/utils/product.js` | `product.test.js`、列表、详情、订单卡片 |
| `src/utils/quantity.js` | `quantity.test.js`、`App.vue`、商品卡片/详情页禁购与数量边界 |
| `src/utils/productForm.js` | `productForm.test.js`、`ProductForm.vue` 的字段校验与字数展示 |
| `src/utils/permissions.js` | `permissions.test.js`、`App.vue` 的演示角色切换与 `createDemoSession()`、`ProductListView` / `ProductDetailView` 的权限提示、`productGroups.js` 的逐条权限判定 |
| `src/utils/productGroups.js` | `productGroups.test.js`、`ProductListView` 的聚合数据流、`ProductGroupCard` 摘要字段、卖家计数与 `all/partial/none` 口径 |
| `ProductGroupCard` props/emits | `ProductListView` 的传参、`ProductCard` 的逐条事件转发、本文组件接口 |
| `Product` 新增 `spuId` / `sellerId` | `buildProductGroups()`、卖家展示、标准类型与具体分类兜底、Mock 字段核对和权限测试 |
| `ProductCard` props/emits、商品编号与卖家展示 | `ProductListView` 的 `can-manage`、本文组件接口、匿名卖家兜底和 `permissions.test.js` |
| `ProductForm` emits | `ProductCreateView` 成功反馈、本文组件接口 |
| `OrderCard` props/emits | `OrderListView` 模板、本文组件接口 |
| `ProductListView` / `ProductCreateView` props 与职责 | `App.vue`、本文页面接口、独立上架入口 |
| `OrderListView` props | `App.vue` 的 `refreshKey`、本文页面接口 |
| 路由 / `meta.title` | `routes.js`、`index.js`、`router/index.test.js`、`App.vue` 导航、README、本文路由表、404 页面 |
| 轮询状态 | `OrderListView`、约束文档、测试/浏览器验证记录 |
| ESLint / Prettier / CI | `package.json`、配置文件、`.github/workflows/frontend-ci.yml`、README/SUBMISSION 验证记录 |

## 12. 开发完成检查

- [ ] API 仍然只通过 `src/api/index.js` 访问。
- [ ] 金额仍以整数分提交、以 `¥xx.xx` 展示。
- [ ] 用户文本仍通过 `{{ }}` 输出，没有 `v-html`。
- [ ] props/emits 变更已同步到本文和调用方。
- [ ] 页面状态仍然互斥，错误有可见提示。
- [ ] 异步请求有失败恢复和卸载保护。
- [ ] 所有前端管理权限判断都使用 `canManageProduct()`，没有按 `sellerName`、标题或聚合组 key 判定归属。
- [ ] 聚合只存在于展示层，没有合并商品、卖家、库存或权限。
- [ ] 真实写操作已在服务端规划归属校验；没有把演示角色切换描述成真实登录。
- [ ] `npm run check`（Lint、格式、测试、构建）通过。
- [ ] `mock/` 未被修改。