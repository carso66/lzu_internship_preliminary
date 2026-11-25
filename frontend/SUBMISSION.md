# 提交说明

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 提交前由本人填写 |
| 学号 | 提交前由本人填写 |
| 方向 | 前端 |
| 分支 | 提交前按实际学号分支填写 |
| 实际投入 | 提交前按真实投入时间填写 |

个人信息没有在开发对话中提供，因此这里不编造姓名、学号、分支和工时。

## 环境

以下版本是当前开发环境实测结果：

| 项 | 版本 / 说明 |
|---|---|
| 操作系统 | Windows |
| Node.js | v24.14.0 |
| npm | 11.9.0 |
| Vue | 3.5.x |
| Vite | 8.3.x |
| vue-router | 4.6.x |
| Vitest | 5.1.x |
| Mock API | `node mock/server.mjs`，默认 3001 端口 |

题目要求 Node 18 以上；上述 Node 24 版本满足要求。

## 启动方式

在 PowerShell 中准备两个终端。

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
npm install
npm run dev
```

浏览器打开：

```text
http://localhost:5173
```

默认端口：

- 前端：`5173`
- Mock API：`3001`

如果修改了 Mock 端口，需要同步修改 `src/api/index.js` 中的 `API_BASE_URL`。

需要恢复初始商品和订单数据时：

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:3001/api/_reset
```

## 完成情况

| 层级 | 完成情况 |
|---|---|
| L0 | 商品列表、发布表单、发布反馈、加载/空态和商品上下架均已实现 |
| L1 | 数量选择、下单、订单列表、金额转换和表单校验均已实现 |
| L2 | 已实现支付轮询、三类防重复提交、Vitest 单测和商品详情路由 |

具体说明：

- 商品列表从 `GET /api/products?pageSize=50` 的 `data.items` 渲染，不硬编码。
- 商品卡片支持“上架 / 下架”，同一商品请求期间按钮禁用并显示“处理中…”。
- 发布表单在发请求前校验标题、价格和库存，价格用 `yuanToFen()` 转整数分。
- 商品卡片和详情页可选择购买数量，请求体使用真实 `{ productId, quantity }`。
- 下单期间所有购买按钮锁定；成功后跳转 `/orders`，订单页重新调用 `getOrders()`。
- 支付轮询每 1 秒一次，最多 10 次；离开页面后清理定时器并使在途请求失效。
- 商品详情支持直接访问 `/products/:id`，不存在时显示“商品不存在”。
- Vitest 已覆盖金额转换、金额展示和标题清洗，共 15 个用例。

## 构建与自检

### 自动化测试

在 `frontend` 目录运行：

```powershell
npm test
```

实测输出：

```text
> frontend@0.0.0 test
> vitest run

 RUN  v5.1.1 D:/Projects/personal/lzu_internship_preliminary/frontend

 Test Files  2 passed (2)
      Tests  15 passed (15)
   Duration  235ms
```

覆盖范围：

- `0.01 → 1`、`19.9 → 1990`、`12.5 → 1250`、`1 → 100`、`100 → 10000`。
- 浮点乘法误差、整数分结果和非法金额。
- `formatPrice(0) === '¥0.00'`，脏数据不出现 `NaN`。
- script/style 内容、HTML 标签、控制字符和零宽字符清理。
- `id=23` 类正常长标题不被误删。

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
✓ 44 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                   0.47 kB │ gzip:  0.33 kB
dist/assets/index-98yttaWV.css   22.01 kB │ gzip:  3.92 kB
dist/assets/index-CCwSLHrO.js   124.31 kB │ gzip: 46.10 kB

✓ built in 153ms
```

### 浏览器全链路验证

使用临时 Playwright 脚本进行过实际点击和 Network 观察，主要结果如下：

- 商品列表加载 24 条，无 `NaN` / `undefined`。
- 异常标题不再原样显示，显示为“二手教材”，也没有弹窗或脚本执行。
- 长标题保持单行省略，未撑破卡片布局。
- 分类下拉恰好 5 项。
- 51 字标题、`19.999` 价格、`1.5` 库存均在对应字段下方显示错误。
- `19.9` 元发布时请求体金额为整数 `1990`。
- 发布成功显示 toast、表单重置、列表刷新。
- 搜索无结果时显示“没找到相关商品”和“清空筛选”。
- 同一商品快速连点上下架只产生 1 次 PATCH。
- 已下架商品下单返回 409，页面显示“商品已下架”。
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

浏览器验证脚本位于临时目录，不在仓库内，不作为提交物；仓库保留 Vitest 测试、构建配置和截图。

## 截图

截图位于 `frontend/screenshots/`：

| 截图 | 内容 |
|---|---|
| `product-list-current.png` | 当前商品列表，包括数量输入和上下架按钮 |
| `order-list-current.png` | 当前订单列表 |
| `order-list-paid-current.png` | 支付完成后的订单列表 |
| `product-form-empty-errors.png` | 发布表单字段校验 |
| `product-form-success.png` | 发布成功反馈 |
| `product-list-empty.png` | 搜索无结果与清空筛选 |
| `product-detail.png` | 商品详情 |
| `product-detail-404.png` | 商品不存在 |
| `product-title-cleaned-list.png` | 异常标题清洗后的列表 |
| `product-title-cleaned-detail.png` | 异常标题清洗后的详情 |

至少包含题目要求的商品列表、发布表单和订单列表三类截图。

## 已知限制与风险

| 项 | 状态 | 原因 |
|---|---|---|
| 下单后列表库存即时回写 | 未覆盖 | 下单后跳转订单页；返回列表会重新拉取，但不在旧列表实例上原地扣减 |
| 完整 E2E 测试套件 | 未提交 | 已提交 15 个 Vitest 单测；临时 Playwright 脚本不在仓库 |
| 分页与服务端搜索 | 未实现 | 当前只加载 50 条并在前端按标题过滤 |
| 登录鉴权、图片上传、富文本 | 未实现 | 题目未要求，且会扩大数据模型与安全边界 |
| Mock 数据持久化 | 不适用 | 题目要求不修改 `mock/`，数据重启即恢复 |
| 生产 History fallback | 需部署配置 | 使用 `createWebHistory()`，静态服务器需回退到 `index.html` |

## 提交前清单

- [x] `npm test` 通过，2 个测试文件、15 个用例。
- [x] `npm run build` 通过。
- [x] `.gitignore` 已忽略 `node_modules` 和 `dist`。
- [x] 未修改 `mock/` 目录。
- [x] 已生成列表、表单、订单和详情相关截图。
- [x] `README.md`、`DESIGN.md`、`AI_USAGE.md`、`SUBMISSION.md`、`constraint.md` 已同步。
- [x] 商品上下架、购买数量和防重复提交已接入 UI。
- [ ] 姓名、学号、分支和实际投入时间由本人补填。
- [ ] 提交前按平台记录核对 AI 模型和调用数据。
