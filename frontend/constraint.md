# 项目约束与实现边界

本文定义当前前端实现的稳定约定、不可破坏的接口契约和已知边界。后续开发应把它当作“改动前检查表”，而不是对题目要求的替代。

原始任务要求与提交流程仍以仓库根目录的 `README.md` 和题目说明为准。本文与代码不一致时，应先修正代码或文档，不允许在验收材料里声称未实现的能力。

## 1. 文档与代码阅读顺序

### 1.1 推荐阅读文档

按下面顺序阅读，可以从前到后建立项目全貌：

| 顺序 | 文档 | 阅读目的 |
|---|---|---|
| 1 | [仓库根目录 README](../README.md) | 确认题目、截止时间、分支和 PR 提交流程 |
| 2 | [项目 README](README.md) | 启动项目，了解页面、路由、功能与验收对应关系 |
| 3 | 本文 `constraint.md` | 先看稳定约定、禁止事项和当前边界 |
| 4 | [代码接口说明](CODE_INTERFACE.md) | 查 API、工具函数、组件 props/emits 和页面数据流 |
| 5 | [设计说明](DESIGN.md) | 理解拆分方式、关键取舍和异步流程 |
| 6 | [提交说明](SUBMISSION.md) | 核对实际完成情况、测试证据和已知风险 |
| 7 | [AI 使用说明](AI_USAGE.md) | 查看 AI 参与过程、验证方式和未采用建议 |

### 1.2 推荐阅读代码

按“启动 → 路由 → 全局协调 → 基础能力 → 界面 → 验证”的顺序阅读：

| 顺序 | 路径 | 关注点 |
|---|---|---|
| 1 | `package.json`、`vite.config.js`、`index.html` | 依赖、脚本、构建入口和页面容器 |
| 2 | `src/main.js` | Vue 应用如何挂载并注册路由 |
| 3 | `src/router/routes.js` | `/`、`/orders`、`/products/new`、`/products/:id` 和 404 的路由表 |
| 4 | `src/router/index.js` | 路由实例、滚动复位、`meta.title` 和浏览器标题 |
| 5 | `src/App.vue` | 顶部导航、全局下单锁、订单刷新 key、全局提示 |
| 6 | `src/api/index.js` | 请求封装、基础地址、响应解析和导出函数 |
| 7 | `src/utils/money.js`、`src/utils/product.js`、`src/utils/quantity.js`、`src/utils/productForm.js`、`src/utils/permissions.js`、`src/utils/productGroups.js` | 金额口径、标题展示清洗、购买数量校验、表单校验、商品管理权限判断和展示聚合 |
| 8 | `src/components/*.vue` | 商品聚合组、商品卡片、发布表单、订单卡片的输入与事件 |
| 9 | `src/views/*.vue` | 商品列表、独立上架页、详情、订单和 404 页面状态 |
| 10 | `src/utils/*.test.js`、`src/api/index.test.js`、`src/router/index.test.js` | 约束如何通过自动测试固化，含商品归属、角色边界和聚合权限三态 |
| 11 | `mock/server.mjs` | 只读核对接口字段、状态和错误分支；禁止修改 |

### 1.3 按任务快速定位

- 改金额或价格展示：先看 `src/utils/money.js` 和 `src/utils/money.test.js`，再看使用它的卡片、表单和详情页。
- 改购买数量或库存边界：先看 `src/utils/quantity.js` 和 `src/utils/quantity.test.js`，再检查 `App.vue`、商品卡片和详情页的禁购状态。
- 改商品表单校验或字数/分类边界：先看 `src/utils/productForm.js` 和 `src/utils/productForm.test.js`，再看 `ProductForm.vue`。
- 改请求地址或接口：先看 `src/api/index.js`，再同步 [代码接口说明](CODE_INTERFACE.md) 中的接口表和调用方。
- 改商品浏览、上下架或购买：从 `ProductListView.vue` → `ProductGroupCard.vue` → `ProductCard.vue` → `src/api/index.js` 顺着调用链阅读。
- 改商品发布：从 `ProductCreateView.vue` → `ProductForm.vue` → `src/api/index.js` 顺着调用链阅读。
- 改商品聚合、来源摘要或组级权限提示：先看 `src/utils/productGroups.js` 和 `productGroups.test.js`，再检查 `ProductListView.vue` 的页面级汇总与 `ProductGroupCard.vue` 的组头。
- 改商品归属、角色或上下架权限：先看 `src/utils/permissions.js` 和 `permissions.test.js`，再检查 `App.vue` 的会话传参、`ProductListView.vue` 的请求前校验和 `ProductGroupCard.vue` / `ProductCard.vue` / `ProductDetailView.vue` 的权限提示。
- 改订单或支付：从 `OrderListView.vue` → `OrderCard.vue` → `createPayment()` / `getPayment()` 阅读，并重点检查卸载清理。
- 新增页面：先看 `src/router/routes.js`、`src/router/index.js`、`src/router/index.test.js` 和 `App.vue`，页面内部自行请求数据，不依赖列表传参。

## 2. 核心约定速查

以下约定是当前实现的稳定契约，后续改动必须同时满足：

| 编号 | 约定 |
|---|---|
| C1 | `mock/` 是题目提供的只读服务，不修改文件、数据格式或业务规则 |
| C2 | API 基础地址由 `VITE_API_BASE_URL` 决定，默认 `/api`，并只集中在 `src/api/index.js` 的 `API_BASE_URL` |
| C3 | 接口统一返回 `{ code, message, data }`，调用方拿到的是成功时的 `data` |
| C4 | 所有接口金额字段都使用整数分，不能把分直接当元展示 |
| C5 | 元转分使用 `Math.round(Number(yuan) * 100)`，不能使用 `parseInt` 或裸乘 100 |
| C6 | 金额展示只走 `fenToYuan()` / `formatPrice()`，`0` 必须显示为 `¥0.00` |
| C7 | 用户可见文本使用 `{{ }}`，禁止 `v-html`；标题清洗只发生在展示层 |
| C8 | loading、error、empty、ready 四种页面状态必须互斥 |
| C9 | 发布、上下架、下单、支付都必须有请求锁或禁用态，防止快速连点 |
| C10 | 离开订单页必须停止轮询，并阻止在途响应继续更新状态 |
| C11 | 详情页必须能直接打开，不依赖列表页传入的商品对象 |
| C12 | 修改功能、接口、校验、测试或工程化配置时，同步 `README.md`、`CODE_INTERFACE.md`、`DESIGN.md`、`SUBMISSION.md` 和 `AI_USAGE.md` 中的相关事实与边界 |
| C13 | 商品唯一身份是 `id`；同名 listing 可做展示聚合，但不得在数据层合并、静默去重或共享权限 |
| C14 | 卖家归属只按当前会话用户 ID 与商品 `sellerId` 精确匹配；`sellerName` 只能展示，绝不能作为权限依据 |
| C15 | 前端权限判断只改善体验；真实发布、上下架、订单和管理员操作必须由服务端重新鉴权，演示角色切换不等于真实登录 |
| C16 | 聚合组只能按 `spuId` 或规范化展示标题生成，不能作为权限资源；每条 listing 仍按 `id` 独立管理 |

## 3. 范围约束

- 本目录只实现前端，后端能力由 `frontend/mock/server.mjs` 提供。
- 使用 Vue 3 + Vite + vue-router，不引入整站后台模板。
- 页面文案和交互面向中文用户；金额展示使用人民币符号 `¥`。
- 不把构建产物、依赖目录或本地临时文件提交到仓库。
- 自动化测试使用 Vitest，测试文件跟随被测试模块放置（`src/api/index.test.js`、`src/utils/*.test.js`），不启动浏览器即可运行；临时浏览器脚本不进入仓库。
- 脚手架示例组件和资源已清理；`index.html` 仍引用 `public/favicon.svg`，不要误删当前站点使用的公共资源。

## 4. 数据与接口约束

- API 基础地址由环境变量和同源默认值共同决定，并集中定义在 `src/api/index.js`：

```js
export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL?.trim() || '/api'
).replace(/\/+$/, '')
```

- 默认请求同源 `/api`，由 `vite.config.js` 的 `server.proxy` / `preview.proxy` 转发到 Mock 服务。
- 修改 API 地址时，复制 `.env.example` 为本地 `.env` 并设置 `VITE_API_BASE_URL`；跨域部署可填写完整地址，不要在组件中散落硬编码地址。
- 服务端统一响应格式为 `{ code, message, data }`。
- `code === 0` 时调用方拿到 `data`；否则抛出带 `message` 的 `Error`。
- HTTP 非 2xx 时优先读取响应体的 `message`，没有 message 才使用 HTTP 状态码兜底。
- `GET /api/products` 返回 `{ total, page, pageSize, items }`，列表页从 `data.items` 读取。
- `GET /api/orders?pageSize=50` 返回 `{ total, page, pageSize, items }`；订单页同时兼容接口直接返回数组的未来变化。
- 商品上下架使用 `PATCH /api/products/:id/status`，请求体为 `{ status: 'on' | 'off' }`。
- 下单请求体必须使用 `{ productId, quantity }`，其中 `quantity` 是大于 0 的整数。
- 支付请求体使用 `{ orderId, method }`，`method` 只能是 `alipay` 或 `wechat`。
- Mock 数据只存在内存中，重启或 `POST /api/_reset` 会恢复初始状态。

### 4.1 商品身份与来源

- 商品唯一身份是接口返回的 `id`；标题只是搜索和展示字段，相同标题不代表同一商品。
- 展示层可优先按 `spuId` 聚合；缺少 `spuId` 时，可按清洗后、忽略大小写和空白的标题做展示聚合。聚合只减少页面重复，不改变数据身份。
- 聚合组不是商品实体，也不是库存或权限容器。组内每条 listing 仍分别保留商品编号、卖家来源、价格、库存、状态、成色和规格，不能因标题或 `spuId` 相同而共享卖家归属。
- `sellerId` 是卖家归属字段。卖家管理商品时，服务端必须用当前会话用户 ID 与商品 `sellerId` 比对。
- `sellerName` 只是展示名称，缺失或空白时显示“匿名卖家”；它不能用于判定商品归属，两名卖家同名也不构成任何共享权限。
- 没有有效 `sellerId` 的商品显示“身份未核验”。当前 Mock 商品没有真实卖家编号，新建商品请求也不提交 `sellerId`，因此不能声称来源已认证。
- 真实后端应从会话推导并写入卖家身份，提供卖家认证与信用信息；成熟商品模型可继续增加 `spuId`、`skuId` 和 `auditStatus`。

### 4.2 角色与操作权限契约

| 角色 | 目标权限 |
|---|---|
| 游客 | 浏览商品和公开详情，不发布商品，不管理商品 |
| 买家 | 浏览、下单、支付，并且只能查看自己的订单 |
| 卖家 | 发布商品，并且只能管理 `sellerId` 与自身用户 ID 一致的商品 |
| 管理员 | 审核商品、强制下架、处理举报、查看审计日志 |

- 上架页的目标访问者是已登录卖家或管理员；游客和买家应被引导登录或完成卖家资质，而不是直接获得发布权限。
- 前端 `canManageProduct()` 只控制按钮和提示，不能阻止有人直接调用接口；服务端必须在每次写操作时重新校验角色、会话和资源归属。
- 角色能力可以叠加，例如卖家同时作为买家下单时，两类权限应分别校验；不能因为一个角色通过就放宽另一资源的归属检查。
- 管理员操作必须记录操作者、目标商品、动作、时间和结果，禁止只在前端隐藏按钮。
- 顶部可切换游客、买家、卖家和管理员演示角色；会话由 `createDemoSession()` 动态生成并标记 `isDemo: true`，仅用于本地 UI 演示，不是认证结果，也没有权限隔离效果。

#### 4.2.1 权限变化的展示契约

- 页面级：`ProductListView` 对当前筛选结果调用 `summarizeGroupPermissions(visibleListings, session)`，显示角色、组数、listing 总数和 `all / partial / none` 范围，并用 `aria-live="polite"` 通知变化。
- 组级：`ProductGroupCard` 显示“可管理全部 N 条”“可管理 x/N 条”或“仅浏览，无管理权限”。这里的 `all / partial / none` 只是摘要，不是授权结果。
- listing 级：每个商品仍显示自己的商品编号、卖家来源和状态；上下架等按钮逐条调用 `canManageProduct(product, session)`，不能读取组级 `canManageAll` 代替。
- 重新计算：`session.role`、`session.id`、商品 `sellerId` 或当前筛选结果变化时，页面级和组级权限摘要必须随渲染重新计算；上下架状态变化会改变按钮文案、购买可用性和状态摘要，但不得改变所有权或授予新权限。
- 最终拒绝：前端隐藏或禁用只改善体验。写操作若被服务端以 403 拒绝，页面必须展示服务端错误并保持原状态，不能因为组级摘要显示“全部可管理”就假定成功。

完整函数签名、返回形状和组件调用方见 [代码接口说明](CODE_INTERFACE.md)。

## 5. 金额约束

- 所有接口中的金额字段统一使用整数分，前端不得直接把分当元展示。
- 元输入转分必须使用：`Math.round(Number(yuan) * 100)`。
- 不使用 `parseInt` 解析金额，因为它会丢掉小数部分。
- 不提交未取整的 `yuan * 100` 结果，避免 JavaScript 浮点误差产生非整数分。
- 金额展示统一走 `fenToYuan()` / `formatPrice()`，保留两位小数。
- `0` 必须显示为 `¥0.00`；脏数据或非有限数字不能导致页面出现 `NaN`。
- 价格输入校验要求必填、大于 0、最多两位小数；库存要求 0~999 的整数。
- 多件订单的 `totalAmount` 由 Mock 按整数分计算，前端只负责格式化展示。

## 6. 渲染与安全约束

- 商品标题、描述、订单中的商品名等用户可见文本必须通过 Vue 的 `{{ }}` 输出。
- 禁止使用 `v-html` 渲染接口文本。
- 不修改 Mock 数据来“修掉”异常标题；`normalizeProductTitle()` 只在展示层清理 script/style 内容、HTML 标签、控制字符和零宽字符。
- 标题清理不能删除正常长标题；长标题应由 CSS 单行省略处理。
- 商品列表、详情页和订单卡片都应使用统一的标题清洗函数，避免同一数据在不同页面表现不一致。
- 前端校验只能用于提升体验，不能代替服务端校验；直接请求接口仍可能返回 400/409。
- 错误 message 应展示在页面上，不能只写 Console。

## 7. 页面状态约束

- 商品列表状态必须互斥：`loading`、`error`、`empty`、`ready` 同一时间只能出现一个。
- 加载中显示“加载中…”；无筛选结果时显示“没找到相关商品”和“清空筛选”；错误时显示错误 message 和重试按钮。
- 搜索是前端按标题过滤，页面不应因为输入关键词而偷偷改变接口返回结构。
- 库存为 0 的商品必须有肉眼可见区别：灰化、显示“缺货”、禁用购买按钮三者同时存在。
- 商品数量输入范围为 1 到库存；库存为 0 时不能下单，提交时必须通过 `getQuantityError()` 复核。
- 商品描述选填，最多 200 字；字段下方显示校验错误和实时 x/200 字数。
- 发布成功后必须重置 5 个字段和字段错误；`ProductCreateView` 展示新商品 ID、详情入口和“继续上架”，不通知列表页刷新。
- 独立上架页用于分离信息架构和导航职责，不等同于登录、卖家归属或权限隔离。
- 发布中、上下架处理中和支付中都需要按钮禁用与进行中文案，防止快速连点。
- 商品被下架时，列表卡片和详情页都不能购买；标题、详情链接仍可访问，以便查看下架状态。

## 8. 防重复请求约束

- 发布商品：`ProductForm.submitting` 为真时不得再次调用 `createProduct()`。
- 上下架：同一商品 ID 在 `statusUpdatingIds` 中时不得再次发送 PATCH；不同商品可以独立操作。
- 下单：`App.creatingOrder` 为真时所有购买按钮都必须禁用，防止重复订单。
- 数量必须由 `App.vue` 调用 `getQuantityError()` 再次校验为正整数、库存数据有效且不超过库存，不能只依赖输入框的 `min/max`。
- 已支付订单的支付按钮必须永久禁用；后端仍需保留重复支付 409 保护。
- 防重复锁必须在 `finally` 中释放，失败后按钮要恢复可点。
- 上下架集合更新必须创建新的 `Set`，不要直接修改旧集合，避免 Vue 响应式依赖漏更新。

## 9. 路由约束

| 路径 | 组件 | 约束 |
|---|---|---|
| `/` | `ProductListView` | 商品列表入口、搜索、上下架和购买，不再内嵌发布表单 |
| `/products/new` | `ProductCreateView` | 独立上架页；必须注册在 `/products/:id` 之前 |
| `/orders` | `OrderListView` | 订单列表与支付轮询 |
| `/products/:id` | `ProductDetailView` | 必须能直接打开，不依赖列表传入状态 |
| `/:pathMatch(.*)*` | `NotFoundView` | 未匹配路径统一显示 404，并提供返回入口 |

- 路由表集中在 `src/router/routes.js`，`src/router/index.js` 只保留路由实例和全局钩子。
- `/products/new` 必须先于 `/products/:id`，否则 `new` 会被当成商品 ID；`src/router/index.test.js` 必须保留该优先级用例。
- 使用 `createWebHistory()` 后，生产静态服务器必须为未知前端路径回退到 `index.html`。
- 每条路由通过 `meta.title` 声明标题，`router.afterEach()` 将 `document.title` 更新为 `${title} · 校园好物市集`。
- 详情页不存在或请求 404 时显示“商品不存在”或后端 message，不能白屏。
- 详情页的“返回”应回到商品列表，不假设用户从列表进入。
- 详情页监听 `route.params.id` 变化，并让旧请求失效，避免快速切换 ID 时显示错商品。

## 10. 异步生命周期约束

- 支付轮询使用 `setInterval`，间隔 1000ms，最多 10 次。
- `processing` 时只能保持“支付中…”，不能提前显示成功。
- `paid` 时停止轮询、更新订单状态并永久禁用支付按钮。
- 达到次数上限或接口报错时停止轮询并显示明确提示。
- `onUnmounted` 中必须先标记 `cancelled`，再自增 `pollToken` 并 `clearInterval`。
- 已经在途的请求返回后必须检查 `cancelled` 和 token，不能再 setState 或继续创建轮询。
- 同一时间只允许一个活动轮询定时器；离开订单页后 5 秒内不应出现新的 `GET /api/payments` 请求。
- 商品上下架和商品详情请求返回时也要检查组件是否已卸载，避免旧请求改写状态。
- 商品列表和订单列表每次加载递增 `loadRequestId`；只有最新请求可回写数据或错误，卸载时使在途请求全部失效。

## 11. 测试与文档同步约束

- `npm test` 必须可以在不启动浏览器的情况下运行。
- 当前测试覆盖 `money.js`、`product.js`、`quantity.js`、`productForm.js`（表单校验）、`permissions.js`（角色、演示会话与商品归属）、`productGroups.js`（展示聚合与组级权限摘要）、`api/index.js`（请求封装）和 `router/routes.js`（路由匹配），共 8 个测试文件、83 个用例；修改这些模块时必须同步更新测试。
- 金额测试至少保留 `0.01`、`19.9`、`12.5`、`1`、`100` 和浮点误差用例。
- 标题测试至少保留 script 标签、HTML 标签、控制字符、零宽字符和正常长标题用例。
- 数量测试至少保留合法整数、零、负数、小数、非数字、超库存、零库存和异常库存用例。
- 表单测试至少保留 emoji 码点计数、标题 50/51 字、价格两位小数、库存 0~999 和描述 200/201 字用例。
- API 测试至少保留 URL 拼接、路径参数编码、JSON 请求体、业务 code 非 0、HTTP 非 2xx、空响应/非 JSON 和网络错误用例。
- 路由测试至少保留 `/products/new` 优先于 `/products/:id`、普通商品 ID、多余路径段 404 和未知路径 404 用例。
- 权限测试至少保留无会话、游客/买家、卖家本人/他人、数字/字符串 ID、同名不同人、空 `sellerId`、管理员和演示会话用例，并覆盖 `createDemoSession()` 的默认管理员、卖家编号清洗、空编号失败关闭和未知角色降级。
- 权限演示切换器只能改变前端会话和权限提示，不得用 `sellerName`、标题或伪造 `sellerId` 制造可管理状态；当前 Mock 没有 `sellerId` 时，卖家模式应如实显示“仅浏览”。
- 聚合测试至少保留 SPU 优先、标题回退、重复 `id`、无标题独立组、异常价格库存、来源计数和权限 `all / partial / none` 三态。
- 测试输出不能伪造成浏览器 E2E；临时 Playwright 验证与仓库内 Vitest 单测要明确区分。
- 修改 API 函数签名、组件 props/emits、路由表或数据形状时，必须同步更新 [代码接口说明](CODE_INTERFACE.md)。
- 修改验收范围、完成状态或已知限制时，必须同步更新 [提交说明](SUBMISSION.md) 和 [项目 README](README.md)。
- 新功能完成后至少执行 `npm run check`；该命令依次执行 ESLint、Prettier 检查、Vitest 和生产构建。涉及交互边界时再补浏览器手动验证。

## 12. 当前未实现边界

以下内容不在当前代码中，不应在验收说明里写成已完成：

- 下单后在商品列表页即时回写库存；当前通过进入订单页、再次进入商品列表时重新请求完成刷新。
- 组件级测试和完整 Playwright E2E 套件；当前仓库只有 8 个单元测试文件（工具函数、表单校验、商品归属、展示聚合与组级权限摘要、API 请求封装和路由匹配）。
- 分页控件、服务端搜索、图片上传和富文本编辑。
- 真实登录、服务端会话实名认证和接口级授权；当前只有前端体验层 `canManageProduct()` 与顶部可切换、明确标记的演示角色，直接调用接口仍不受其限制。
- 真实卖家 `sellerId`、商品归属持久化和来源认证；Mock 新建商品固定写为“我”且没有 `sellerId`，界面只能显示“身份未核验”。`sellerName` 不能作为权限依据。
- 管理员审核、强制下架、举报处理和审计日志；演示管理员目前只能通过现有上下架按钮体验状态切换，不代表这些管理能力已完成。
- SPU/SKU 实体治理、同名商品合并与来源认证；当前 Mock 没有真实 `spuId`/`skuId`，展示层只能按标题回退聚合，不能把聚合组视为同一商品或同一卖家。
- 退款、优惠券和更复杂的支付状态模型。
- 生产静态服务器的 SPA History fallback。

这些边界是为了让当前实现保持可验证；后续扩展时应先补测试，再改公共金额、接口或轮询逻辑。

## 13. 改动前检查表

1. 是否破坏了 C1~C16 中的任一核心约定？
2. 是否修改了 `mock/`？如果修改了，撤回。
3. 金额是否仍以整数分提交，并以 `¥xx.xx` 展示？
4. 新增文案是否通过 `{{ }}` 渲染，是否存在 `v-html`？
5. 新增异步操作是否有失败提示、请求锁和卸载保护？
6. 新增或变更的 API、props、emits、路由、`meta.title` 是否写进 `CODE_INTERFACE.md`？
7. 数量、下架状态和描述长度是否在本地输入与全局入口都做了校验？
8. 商品归属是否只按 `sellerId` 判断，是否避免用 `sellerName` 或标题授权？
9. 是否明确前端权限不替代服务端鉴权，且没有把演示角色切换写成真实登录？
10. 聚合组 key 是否只用于展示分组，没有被用于权限判断或数据层去重？
11. `npm run check` 是否通过，测试、构建和验收文档是否同步？
