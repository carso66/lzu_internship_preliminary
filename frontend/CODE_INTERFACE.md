# 代码接口说明

本文说明当前前端各部分代码的职责、可复用接口、组件输入输出和数据流，供后续维护、追加功能或评审时快速定位。

阅读本文前先看 [项目约束与实现边界](constraint.md)。稳定约定不在这里重新定义；本文重点回答“代码在哪里、如何调用、改动会影响谁”。

## 1. 总体结构

```text
index.html
  └─ src/main.js
      ├─ src/style.css
      ├─ src/router/index.js
      └─ src/App.vue
          ├─ src/views/ProductListView.vue
          │   ├─ src/components/ProductForm.vue
          │   └─ src/components/ProductCard.vue
          ├─ src/views/ProductDetailView.vue
          ├─ src/views/OrderListView.vue
          │   └─ src/components/OrderCard.vue
          ├─ src/api/index.js
          └─ src/utils/money.js / product.js
```

各层职责：

| 层 | 位置 | 职责 |
|---|---|---|
| 应用入口 | `src/main.js` | 创建 Vue 应用、注册路由、挂载 `#app` |
| 全局壳层 | `src/App.vue` | 顶部导航、全局下单锁、订单刷新信号、全局 toast |
| 路由 | `src/router/index.js` | URL 与页面组件映射 |
| 页面 | `src/views/` | 发起数据请求、维护页面状态、组合组件 |
| 业务组件 | `src/components/` | 展示和局部交互，通过 props/emits 与页面通信 |
| API 层 | `src/api/index.js` | 基础地址、fetch、响应解析、导出接口函数 |
| 工具层 | `src/utils/` | 金额换算、金额展示、标题清洗及单测 |
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

### 2.2 `src/router/index.js`

| 路径 | 路由名 | 组件 | 数据来源 |
|---|---|---|---|
| `/` | `products` | `ProductListView.vue` | `getProducts({ pageSize: 50 })` |
| `/orders` | `orders` | `OrderListView.vue` | `getOrders()` |
| `/products/:id` | `product-detail` | `ProductDetailView.vue` | `getProduct(route.params.id)` |

路由使用 `createWebHistory()`。直接访问详情页时，详情组件会自己读取 `route.params.id` 并请求数据，不依赖列表页传参。

### 2.3 `src/App.vue`

`App.vue` 是全局协调层：

- 渲染顶部导航和 `RouterView`。
- 统一监听所有页面冒泡的 `buy` 事件。
- 调用 `createOrder({ productId, quantity })`。
- 下单成功后递增 `ordersRefreshKey`，跳转 `/orders`。
- 用 `creatingOrder` 和 `orderingProductId` 锁定所有购买按钮。
- 显示全局下单结果提示，3 秒后自动消失。

传给路由页面的公共接口：

```vue
<component
  :is="Component"
  :ordering-product-id="orderingProductId"
  :refresh-key="ordersRefreshKey"
  @buy="handleBuy"
/>
```

| 接口 | 类型 | 消费方 |
|---|---|---|
| `ordering-product-id` | `number \| string \| null` | 商品列表、商品详情，用于标出正在下单的商品 |
| `refresh-key` | `number` | 订单列表，用于下单成功后重新加载订单 |
| `buy` | 事件 | `App.vue`，事件载荷为 `{ product, quantity }` |

## 3. API 客户端接口

文件：`src/api/index.js`

### 3.1 基础地址

```js
export const API_BASE_URL = 'http://127.0.0.1:3001/api'
```

Mock 端口变化时只改这一处。组件不得自行拼接接口域名。

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
| `getOrders()` | `GET /orders` | 无 | `{ total, page, pageSize, items }` | `OrderListView` |
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
  description: string     // 可为空，Mock 限制最多 200 字
}
```

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

页面必须把 `message` 展示给用户，不能让错误只停留在 Console。

## 4. 数据结构

### 4.1 `Product`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 商品 ID |
| `title` | string | 商品标题；展示前经过 `normalizeProductTitle()` |
| `price` | number | 单价，整数分 |
| `stock` | number | 库存，0~999 |
| `category` | string | 五种分类之一 |
| `status` | `'on' \| 'off'` | 在售 / 已下架 |
| `sellerName` | string | 卖家名称 |
| `description` | string | 商品描述 |

### 4.2 `Order`

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

### 4.3 `Payment`

| 字段 | 类型 | 说明 |
|---|---|---|
| `paymentId` | string | 支付单号 |
| `orderId` | number | 关联订单 ID |
| `amount` | number | 支付金额，整数分 |
| `method` | `'alipay' \| 'wechat'` | 支付方式 |
| `status` | `'processing' \| 'paid'` | 支付状态 |
| `paidAt` | `string \| null` | 支付完成时间 |

### 4.4 分页对象

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
| `yuanToFen` | `yuanToFen(yuan) -> number` | 元字符串/数字转整数分；空值和非法数字抛错；内部使用 `Math.round(Number(raw) * 100)` |
| `fenToYuan` | `fenToYuan(fen) -> string` | 分转固定两位小数的元字符串；非有限数字按 0 处理 |
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

## 6. 业务组件接口

### 6.1 `ProductForm.vue`

职责：商品发布表单、字段校验、元转分、提交锁和成功反馈。

**Props**：无。

**Emits**：

| 事件 | 载荷 | 触发时机 |
|---|---|---|
| `created` | 新建的 `Product` | `createProduct()` 成功且表单已重置后 |

内部关键状态：

| 状态 | 作用 |
|---|---|
| `form` | 标题、价格、库存、分类、描述 |
| `errors` | 四个字段各自的错误文案 |
| `submitting` | 提交锁，防止重复 POST |
| `submitError` | 后端或网络错误提示 |
| `toastMessage` | 成功 toast |

校验规则：

- 标题：去空白后 1~50 个 Unicode 字符。
- 价格：必填、匹配最多两位小数、数值大于 0。
- 库存：必填、非负整数、最大 999。
- 分类：必须属于固定五项。
- 校验失败时不发送请求。

父组件接入方式：

```vue
<ProductForm @created="handleProductCreated" />
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

**Emits**：

| 事件 | 载荷 | 触发时机 |
|---|---|---|
| `buy` | `{ product, quantity }` | 点击购买且库存、锁状态允许 |
| `toggle-status` | `product` | 点击上架/下架 |

内部行为：

- `quantity` 默认 1，并随库存变化自动限制在有效范围。
- 零库存时显示“缺货”、卡片灰化并禁用购买。
- 标题经 `normalizeProductTitle()` 清洗，CSS 负责单行省略。
- 金额仅通过 `formatPrice(product.price)` 展示。

### 6.3 `OrderCard.vue`

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

**Emits**：

| 事件 | 载荷 | 转发目标 |
|---|---|---|
| `buy` | `{ product, quantity }` | 原样转发给 `App.vue` |

职责：

- 挂载时调用 `getProducts({ pageSize: 50 })`。
- 从 `data.items` 读取列表。
- 按标题做前端筛选。
- 维护互斥的 loading/error/empty/ready 状态。
- 调用 `updateProductStatus()`，并用 `statusUpdatingIds` 做商品级锁。
- 收到 `ProductForm` 的 `created` 后重新加载商品。
- 组件卸载时设置 `cancelled`，阻止旧请求回写。

### 7.2 `ProductDetailView.vue`

**Props**：

| Prop | 类型 | 作用 |
|---|---|---|
| `orderingProductId` | `number \| string \| null` | 标识当前详情商品是否正在下单 |

**Emits**：

| 事件 | 载荷 | 转发目标 |
|---|---|---|
| `buy` | `{ product, quantity }` | 原样转发给 `App.vue` |

职责：

- 监听 `route.params.id`，每次变化都通过 `getProduct()` 独立请求。
- 使用递增 `requestId` 丢弃旧请求响应。
- 不存在或请求失败时显示“商品不存在”或后端 message。
- 下架或缺货时禁用购买。
- 组件卸载时递增 `requestId`，使在途请求失效。

### 7.3 `OrderListView.vue`

**Props**：

| Prop | 类型 | 作用 |
|---|---|---|
| `refreshKey` | Number | 父组件递增后触发订单重新加载 |

**Emits**：无；支付流程由页面内部完成。

职责：

- 挂载时调用 `getOrders()`，兼容 `data.items` 和直接数组。
- 处理支付宝/微信支付创建与状态轮询。
- 管理 `pollTimer`、`pollToken`、`cancelled` 和 `requestInFlight`。
- 支付成功后原地将订单状态改为 `paid`。
- 组件卸载时设置 `cancelled = true`，并调用 `stopPolling()`。

## 8. 关键数据流

### 8.1 商品列表加载

```text
ProductListView onMounted
  -> getProducts({ pageSize: 50 })
  -> request() 解包 data
  -> data.items
  -> filteredProducts
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
  -> ProductListView.handleProductCreated()
  -> loadProducts()
```

### 8.3 选择数量并下单

```text
ProductCard / ProductDetailView
  -> emit('buy', { product, quantity })
  -> ProductListView / ProductDetailView 转发
  -> App.handleBuy()
  -> 校验并锁定 creatingOrder
  -> createOrder({ productId, quantity })
  -> ordersRefreshKey += 1
  -> router.push('/orders')
```

### 8.4 支付轮询

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

1. 在 `src/router/index.js` 增加路径和命名路由。
2. 页面自行请求所需数据，支持直接打开 URL。
3. 在 `src/App.vue` 判断导航激活状态。
4. 在本文路由表和 README 页面表中同步说明。
5. 验证刷新、直接访问和不存在数据三种情况。

### 10.4 修改金额或订单字段

1. 先确认接口单位是否仍为整数分。
2. 修改 `money.js` 时同步更新 `money.test.js`。
3. 检查所有调用方，禁止局部出现 `price / 100` 或裸 `toFixed`。
4. 修改订单字段时同步 `OrderCard.vue`、订单页、Mock 字段核对和本文数据结构。
5. 执行 `npm test`、`npm run build` 和关键浏览器流程。

### 10.5 修改支付状态机

1. 先明确新增状态的所有可能取值。
2. 更新 `OrderCard` 的状态文案、按钮禁用条件和未知状态兜底。
3. 更新 `OrderListView` 的轮询结束条件。
4. 确认卸载时 `cancelled`、`pollToken`、`pollTimer` 和 `requestInFlight` 的行为。
5. 在离开页面后观察至少 5 秒，确认没有新增支付查询请求。

## 11. 修改影响矩阵

| 修改位置 | 必须连带检查 |
|---|---|
| `src/api/index.js` | 调用页面、错误提示、本文 API 表、Mock 字段 |
| `src/utils/money.js` | `money.test.js`、表单、卡片、详情、订单卡片 |
| `src/utils/product.js` | `product.test.js`、列表、详情、订单卡片 |
| `ProductCard` props/emits | `ProductListView` 模板、本文组件接口 |
| `ProductForm` emits | `ProductListView` 刷新逻辑、本文组件接口 |
| `OrderCard` props/emits | `OrderListView` 模板、本文组件接口 |
| `ProductListView` props | `App.vue`、本文页面接口 |
| `OrderListView` props | `App.vue` 的 `refreshKey`、本文页面接口 |
| 路由 | `App.vue` 导航、README、本文路由表 |
| 轮询状态 | `OrderListView`、约束文档、测试/浏览器验证记录 |

## 12. 开发完成检查

- [ ] API 仍然只通过 `src/api/index.js` 访问。
- [ ] 金额仍以整数分提交、以 `¥xx.xx` 展示。
- [ ] 用户文本仍通过 `{{ }}` 输出，没有 `v-html`。
- [ ] props/emits 变更已同步到本文和调用方。
- [ ] 页面状态仍然互斥，错误有可见提示。
- [ ] 异步请求有失败恢复和卸载保护。
- [ ] `npm test` 与 `npm run build` 通过。
- [ ] `mock/` 未被修改。
