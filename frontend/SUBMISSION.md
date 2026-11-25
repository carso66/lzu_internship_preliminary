# 提交说明

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 李宓辰 |
| 学号 | 320250946851 |
| 方向 | 前端 |
| 分支 | 320250946851 |
| 实际投入 | 8h |
| PR 标题 | `[FE] 李宓辰 320250946851` |
| PR 地址 | `https://github.com/carso66/lzu_internship_preliminary/pull/24` |
| PR 目标分支 | `320250946851` |
| PR 状态 | `Able to merge`（GitHub 显示“能够合并”） |

以上个人信息由本人最终确认；实际投入按真实情况记录。

## 环境

以下版本是当前开发环境实测结果：

| 项 | 版本 / 说明 |
|---|---|
| 操作系统 | Windows |
| Node.js | v24.14.0（项目要求 >= 22.12.0） |
| npm | 11.9.0（项目要求 >= 10） |
| Vue | 3.5.x |
| Vite | 8.3.x |
| vue-router | 4.6.x |
| Vitest | 5.1.x |
| Mock API | `node mock/server.mjs`，默认 3001 端口 |

项目依赖 Vite 8 和 Vitest 5，统一要求 Node.js >= 22.12.0；当前实测 Node v24.14.0 满足要求。

## 启动方式

在 PowerShell 中准备两个终端。

先在仓库根目录安装依赖：

```powershell
cd frontend
npm ci
```

终端 A，启动 Mock API：

```powershell
cd frontend
node mock/server.mjs
```

看到以下内容即表示启动成功：

```text
好物市集 mock 已启动: http://127.0.0.1:3001
```

终端 B，启动前端：

```powershell
cd frontend
npm run dev
```

浏览器打开：

```text
http://localhost:5173
```

默认端口：

- 前端：`5173`
- Mock API：`3001`

前端默认使用同源 `/api`，Vite 的 `server.proxy` 和 `preview.proxy` 会转发到 `http://127.0.0.1:3001`。如需修改 API 地址，复制 `.env.example` 为 `.env` 并设置 `VITE_API_BASE_URL`；前端与 API 不同域时可填写完整地址。生产部署需配置反向代理，或在构建时提供完整地址。

需要恢复初始商品和订单数据时：

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:3001/api/_reset
```

## 完成情况

| 层级 | 完成情况 |
|---|---|
| L0 | 商品列表、独立上架页、发布反馈、加载/空态和商品上下架均已实现 |
| L1 | 数量选择、下单、订单列表、金额转换和表单校验均已实现 |
| L2 | 已实现支付轮询、三类防重复提交、Vitest 单测、商品详情路由、独立上架路由及优先级测试、404、动态标题、商品聚合展示与组级权限摘要、前端商品归属判断和工程化检查 |

具体说明：

- 商品列表从 `GET /api/products?pageSize=50` 的 `data.items` 渲染，不硬编码。
- 顶部导航提供“商品列表 / 上架商品 / 我的订单”；上架页使用独立路由 `/products/new`，商品列表不再内嵌发布表单。该静态路径必须注册在 `/products/:id` 之前，已有路由单测锁定匹配优先级。
- 商品卡片支持“上架 / 下架”，同一商品请求期间按钮禁用并显示“处理中…”。
- 发布表单在发请求前校验标题、价格、库存和描述长度，价格用 `yuanToFen()` 转整数分；描述最多 200 字并实时显示字数。
- 发布成功后表单重置，独立上架页展示新商品标题、商品 ID、“查看详情”和“继续上架”；列表页不接收刷新通知，返回列表时会重新请求数据。
- 商品卡片和详情页显示商品编号和卖家名称；`sellerName` 缺失或为空白时统一显示“匿名卖家”。该字段只是展示字段，没有有效 `sellerId` 时不能据此判定商品归属。
- 商品唯一身份是 `id`；展示层先识别明确同款，再按标题中的标准商品类型或具体分类归组，无法可靠识别时每件商品独立成组。数据层不合并商品、卖家、库存或权限，卖家名称相同也不会被当成同一卖家。
- 组标题不由第一件商品决定：标准类型和具体分类使用固定名称，明确同款用标准类型或共同标题前缀，无法识别时使用该商品清洗后的标题。
- 组头展示商品件数、卖家信息数、价格区间、库存/状态摘要和组级权限摘要；数据模型另计算有卖家编号的不同卖家数与缺少卖家编号的商品数，但当前组头没有展示这两个字段。多商品组默认折叠，单商品组默认展开。
- 聚合组不是权限资源：页面显示当前角色及整体 `all / partial / none` 范围，组头显示“可管理全部 N 件 / 可管理 x/N 件 / 仅浏览”，每件商品的上下架按钮仍逐件调用 `canManageProduct()` 判定。
- 顶部导航提供权限演示切换器：游客、买家、卖家、管理员可即时切换；选择卖家时显示卖家编号输入框。游客 / 买家应看到“仅浏览”，管理员应看到“全部可管理”，卖家只有在真实 `sellerId` 匹配时才会出现“部分可管理”。
- 上下架按钮根据 `canManageProduct(product, session)` 显示权限提示；卖家只在商品 `sellerId` 与自身用户 ID 精确匹配时通过，管理员可管理全部商品。所有演示会话都通过 `createDemoSession()` 生成并标记 `isDemo: true`，不是真实登录。
- 商品卡片和详情页可选择购买数量，请求体使用真实 `{ productId, quantity }`；`getQuantityError()` 再次校验正整数、库存异常和库存上限。
- 已下架商品在列表和详情页都禁用购买与数量输入，仍可通过标题进入详情查看。
- 下单期间所有购买按钮锁定；成功后跳转 `/orders`，订单页重新调用 `getOrders()`（`pageSize=50`）。
- 支付轮询每 1 秒一次，最多 10 次；离开页面后清理定时器并使在途请求失效。
- 商品详情支持直接访问 `/products/:id`，不存在时显示“商品不存在”；未知路径进入统一 404 页面。
- 路由通过 `meta.title` 更新浏览器标题。
- Vitest 已覆盖金额转换、金额展示、标题清洗、数量校验、表单校验、商品归属与角色边界、相似商品分组与组级权限摘要、API 请求封装错误处理和路由匹配优先级，共 8 个测试文件、89 个用例。
- ESLint、Prettier 和 GitHub Actions CI 已接入，`npm run check` 串联全部检查。

## 构建与自检

### 一键检查

在 `frontend` 目录运行：

```powershell
npm run check
```

`check` 依次执行 `eslint .`、`prettier --check .`、`vitest run` 和 `vite build`。本次全部通过。

### 自动化测试

单独运行：

```powershell
npm test
```

实测输出：

```text
> frontend@0.0.0 test
> vitest run

 RUN  v5.1.1 D:/Projects/personal/lzu_internship_preliminary/frontend

 Test Files  8 passed (8)
      Tests  89 passed (89)
   Start at  00:41:30
   Duration  916ms (import 46%, transform 31%, tests 14%, worker 9%)
```

覆盖范围：

- `0.01 → 1`、`19.9 → 1990`、`12.5 → 1250`、`1 → 100`、`100 → 10000`。
- 空值、负数、三位小数、科学计数法、千分位和超安全范围金额均被拒绝。
- `formatPrice(0) === '¥0.00'`，脏数据不出现 `NaN`。
- script/style 内容、HTML 标签、控制字符、零宽字符、大小写混合和纯标签兜底。
- `id=23` 类正常长标题不被误删。
- 数量为正整数、1 到库存之间；脏类型、科学计数法、非安全整数、超库存、库存为 0、库存异常和非数字输入均被拒绝。
- 表单校验覆盖 emoji 码点计数、标题 50/51 字、价格两位小数、库存 0~999 和描述 200/201 字。
- API 请求封装覆盖 URL 拼接、路径参数编码、JSON 请求体，以及业务 code 非 0、HTTP 非 2xx、空响应/非 JSON 和网络错误。
- 路由覆盖 `/products/new` 优先命中独立上架页、普通商品 ID 命中详情页、多余路径段和未知路径进入 404。
- 权限覆盖无会话、游客/买家、卖家本人/他人、数字/字符串 ID、同名不同人、空 `sellerId`、管理员和演示会话等边界；另覆盖 `createDemoSession()` 的默认管理员、角色切换、卖家编号清洗、空编号失败关闭和未知角色降级。
- 分组覆盖明确同款优先、不同标题的相近商品归组、配件排除、随附配件保留、重复 `id`、无标题独立组、异常价格库存、卖家信息计数和权限 `all / partial / none` 三态。

### 生产构建

```powershell
npm run build
```

实测输出：

```text
> frontend@0.0.0 build
> vite build

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

### 浏览器全链路验证

使用临时 Playwright 脚本进行过实际点击和 Network 观察。以下记录来自历史浏览器验证，其中发布相关结果属于“商品列表内嵌发布表单”阶段，不能代表拆分后的独立上架页：

- 商品列表加载 24 条，无 `NaN` / `undefined`。
- 异常标题不再原样显示，显示为“二手教材”，也没有弹窗或脚本执行。
- 长标题保持单行省略，未撑破卡片布局。
- 分类下拉恰好 5 项。
- 51 字标题、`19.999` 价格、`1.5` 库存均在对应字段下方显示错误。
- 拆分前列表内嵌表单阶段：`19.9` 元发布时请求体金额为整数 `1990`；发布成功显示 toast、表单重置并刷新列表。
- 搜索无结果时显示“没找到相关商品”和“清空筛选”。
- 同一商品快速连点上下架只产生 1 次 PATCH。
- 当前列表和详情页会对已下架商品禁用购买按钮与数量输入；接口层仍保留“商品已下架”的 409 保护。
- 数量 2 下单请求体为 `{"productId":1,"quantity":2}`，订单金额为 `39800` 分，列表显示 `×2` 和 `¥398.00`。
- 快速连点购买只产生 1 次 POST。
- 支付过程中按钮保持“支付中…”且 disabled。
- 观察到 `processing` 后最终变为 `paid`，不会提前成功。
- 支付成功后订单显示“已支付”，按钮永久禁用。
- 离开订单页后 5.2 秒内新增支付查询为 0。
- `/products/1` 直接打开正常，`/products/9999` 显示“商品不存在”。

补充接口验证：直接向已支付订单再次发起支付请求时，HTTP 状态为 409，响应为：

```json
{"code":40903,"message":"订单已支付，请勿重复支付","data":null}
```

页面中的已支付按钮本身就是永久禁用，因此这里验证的是后端重复支付保护，不是“页面仍可点击并展示 message”。

上述结果来自历史浏览器验证。当前版本新增的独立上架页、`/products/new` 路由优先级、相似商品分组、卖家名称与匿名兜底、下架禁购、描述字数限制、全局 404、动态页面标题和工程化配置已完成代码检查、单测和构建，但没有重新执行浏览器回归；因此不把独立上架页写成“已重新浏览器验证”。

浏览器验证脚本位于临时目录，不在仓库内，不作为提交物；仓库保留 Vitest 测试、构建配置、CI 配置和截图。

## 截图

截图位于 `frontend/screenshots/`：

| 截图 | 内容 |
|---|---|
| `product-list-current.png` | 此前商品列表，包括数量输入和上下架按钮；未包含当前独立上架页导航状态 |
| `order-list-current.png` | 当前订单列表 |
| `order-list-paid-current.png` | 支付完成后的订单列表 |
| `product-form-empty-errors.png` | 拆分前列表内嵌表单阶段的字段校验 |
| `product-form-success.png` | 拆分前列表内嵌表单阶段的发布成功反馈 |
| `product-list-empty.png` | 搜索无结果与清空筛选 |
| `product-detail.png` | 商品详情 |
| `product-detail-404.png` | 商品不存在 |
| `product-title-cleaned-list.png` | 异常标题清洗后的列表 |
| `product-title-cleaned-detail.png` | 异常标题清洗后的详情 |

至少包含题目要求的商品列表、发布表单和订单列表三类截图。发布相关截图来自拆分前列表内嵌表单阶段，独立上架页在拆分后没有重新截图。

## 已知限制与风险

| 项 | 状态 | 原因 |
|---|---|---|
| 下单后列表库存即时回写 | 未覆盖 | 下单后跳转订单页；返回列表会重新拉取，但不在旧列表实例上原地扣减 |
| 完整 E2E 测试套件 | 未提交 | 已提交 89 个 Vitest 单测；临时 Playwright 脚本不在仓库 |
| 分页与服务端搜索 | 未实现 | 当前只加载 50 条并在前端按标题过滤 |
| 登录鉴权 | 未实现 | 当前只有顶部可切换、明确标注 `isDemo: true` 的演示会话，没有真实登录、服务端会话或接口级鉴权；游客和买家仍可直接进入上架页 |
| 卖家身份与商品所有权 | 部分演示 | 已有角色切换、`createDemoSession()`、`canManageProduct()` 和单测，但 Mock 商品没有真实 `sellerId`，所以页面无法展示卖家 `partial`；直接调用接口也不受前端限制，服务端归属校验仍未实现 |
| 卖家认证、标准商品实体与同名治理 | 未实现 | `sellerName` 只是展示字段，Mock 商品没有真实 `spuId`，新建商品固定写为“我”且没有 `sellerId`；界面已实现基于明确同款、标准类型和具体分类的保守分组，但缺少卖家编号时不能判定归属，展示分组不能替代实体治理 |
| 图片上传、富文本 | 未实现 | 题目未要求，且会扩大数据模型与安全边界 |
| Mock 数据持久化 | 不适用 | 题目要求不修改 `mock/`，数据重启即恢复 |
| 生产 History fallback | 需部署配置 | 使用 `createWebHistory()`，静态服务器需回退到 `index.html` |

## 提交前清单

- [x] `npm run check` 通过，包含 ESLint、Prettier、8 个测试文件 89 个用例和生产构建。
- [x] `.gitignore` 已忽略 `node_modules`、`dist` 和本地 `.env` 文件，并保留 `.env.example`。
- [x] CI 配置已提交到 `.github/workflows/frontend-ci.yml`。
- [x] 未修改 `mock/` 目录。
- [x] 已生成列表、订单和详情截图，以及拆分前列表内嵌表单阶段的发布截图。
- [x] `README.md`、`constraint.md`、`CODE_INTERFACE.md`、`DESIGN.md`、`AI_USAGE.md`、`SUBMISSION.md` 已同步。
- [x] 商品上下架、购买数量和防重复提交已接入 UI。
- [x] 下架禁购、描述 200 字限制、全局 404、动态页面标题和独立上架页已接入。
- [x] `/products/new` 路由优先级、卖家名称展示、匿名兜底和“不能判定卖家归属”的提示逻辑已接入。
- [x] 商品归属只按 `sellerId` 判断，权限模块单测覆盖无会话、角色、同名和空卖家编号边界。
- [x] 分组只影响展示，不合并商品、卖家、库存或权限；聚合组 key 不用于权限判断。
- [x] 页面级、组级和单件商品级权限展示已接入，组级权限仅作摘要，操作前仍逐件校验。
- [x] 顶部权限演示切换器、`createDemoSession()` 和卖家编号输入已接入；演示身份明确标记 `isDemo: true`，文档没有把它写成真实登录或服务端授权。
- [ ] 姓名、学号、方向、分支和实际投入时间已由本人按真实情况复核。
- [x] 已按平台记录核对 AI 模型和调用数据（1000 次以上，费用 16 元）。
