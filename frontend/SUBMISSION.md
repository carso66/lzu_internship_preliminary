# 提交说明（SUBMISSION）

## 基本信息

| 项 | 内容 |
|---|---|
| 姓名 | 墨池语 |
| 学号 | 320240920480 |
| 方向 | 前端（同时完成了后端 L0 接口，便于直连） |
| 分支 | `320240920480` |
| 实际投入 | 约 30 小时（跨 2 天，含环境排查与文档） |

## 环境

| 项 | 版本 |
|---|---|
| 操作系统 | Windows 11 |
| 运行时 | Node 26.13.1 / JDK 17.0.14（Microsoft OpenJDK） |
| 包管理器 | npm 11.20.0 / Maven Wrapper 3.10.1 |

> 注意：这台机器上 `java -version` 默认指向 JDK 27，但 Spring Boot 3.x 只保证到 17/21。
> 我在 `.mvn/jdk-env.cmd` / `.mvn/jdk-env.sh` 里写了自动探测，`mvnw` 启动时会自己找到 JDK 17，
> **不需要评审手动切 `JAVA_HOME`**。

## 启动方式（必填，要能照抄）

前端直连后端，两个终端各起一个：

```bash
# 终端 1：后端（必须先起，前端代理指向 8080）
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
# 起好后 http://localhost:8080，控制台会打印 24 条种子商品已载入

# 终端 2：前端
cd frontend
npm ci                  # 按 package-lock.json 装依赖，版本可复现；改依赖时才用 npm install
npm run dev
# 打开 http://localhost:5173
```

前置条件：Node.js 18+（`node -v`、`npm -v` 都要能打印版本号）。两个命令都必须在
`frontend/` 目录下执行，`package.json` 在那里；在仓库根目录跑会报
`npm error enoent Could not read package.json`。

> 启动失败时的排查顺序：
> 1. **`npm` 不是可识别的命令** → Node 装了但没进 `PATH`。临时补一行
>    `$env:Path = "C:\Program Files\nodejs;" + $env:Path` 再跑，或直接重开终端。
> 2. **`Error: Port 5173 is already in use`** → 上一次的 dev server 还没退。
>    `vite.config.js` 里是 `strictPort: true`，端口被占会直接退出而不是自动换端口。
>    执行 `Get-NetTCPConnection -LocalPort 5173 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }` 后重试。
> 3. **页面能开但列表是错误态** → 后端没起或不在 8080。前端把 `/api` 代理到
>    `http://localhost:8080`，先确认 `curl http://localhost:8080/api/products?pageSize=1` 有返回。
> 4. 端口本身被改过时会读到 `API_TARGET` / `MOCK_PORT` / `BACKEND_PORT` 环境变量，
>    优先级见 `vite.config.js` 的 `resolveApiTarget`。

验证后端真的起来了：

```bash
curl "http://localhost:8080/api/products?pageSize=5"
# 应返回 {"code":0,"message":"ok","data":{"total":24,...}}
```

### 前端怎么连到后端（方案 A：Vite 代理）

前端代码里没有任何后端地址，所有请求都是同源相对路径 `/api/...`
（`src/api/client.js` 的 `BASE = import.meta.env.VITE_API_BASE || '/api'`）。
真正把请求送到后端的是 `vite.config.js` 里的这一段：

```js
server: {
  port: 5173,
  strictPort: true,
  proxy: { '/api': { target: apiTarget, changeOrigin: true } },
}
```

`apiTarget` 由 `resolveApiTarget()` 按 **`API_TARGET` > `MOCK_PORT` > `BACKEND_PORT` > 默认 8080**
的顺序算出来，所以默认就是浏览器请求 `localhost:5173/api/products`
→ Vite 转发到 `localhost:8080/api/products` → 拿回结果。

这样做的好处是**浏览器眼里始终是同源请求**，后端不用开 CORS。
本项目后端确实没配 CORS：`OPTIONS /api/products` 预检会返回 `403`，
`GET` 也拿不到 `Access-Control-Allow-Origin` 响应头。如果要改成前端直接写
`http://localhost:8080/api`（跨域），就必须同时给后端加 CORS 配置，否则所有
`POST` / `PATCH` 都会在预检阶段被拦掉。代理方案和 CORS 方案**不要同时开**：
代理生效时请求同源，CORS 配置根本不会被触发。

### 三个可选开关

这三个变量都可写进 `frontend/.env.local`（已被 gitignore，不会提交），
也可以只在命令行临时给。`.env.example` 是它们的模板，复制改名即可：

```bash
cp .env.example .env.local
```

| 变量 | 作用 |
|---|---|
| `API_TARGET=http://host:port` | 前端代理指向任意完整地址（优先级最高） |
| `MOCK_PORT=3001` | 切回题目自带的 mock 服务（需要先在 `mock/` 下起 mock） |
| `BACKEND_PORT=8081` | 后端换了端口时只改端口号 |
| `VITE_API_BASE=/api` | 请求前缀，默认就是 `/api`，一般不用改 |

### 数据说明

- `backend/data.sql` 里有 24 条初始商品，字段和 `frontend/mock/db.json` 完全一致
  （含 `stock=0` 缺货、`status=off` 已下架、`price=9999999` 大额、emoji 标题、50 字超长标题、
  `<script>` XSS 标题这些边界样本），所以直连后端和用 mock 看到的列表是一样的。
- 数据库是 **H2 内存库**（`jdbc:h2:mem:market`），**重启后端即恢复成 24 条种子数据**。
  想复位就 Ctrl+C 再 `mvnw spring-boot:run` 一次。没有 `POST /api/_reset`，重启就是复位。
- `schema.sql` 是手写的建表语句（`DROP TABLE IF EXISTS` + `CREATE TABLE`），可重复执行。

## 我做到了哪一步

| 层级 | 完成情况 |
|---|---|
| L0 | 做了 **5 / 5** 条 |
| L1 | 做了 **4 / 4** 条 |
| L2 | 做了 **L2-13 B 路由**（多页面 + `/products/:id` 详情页） |

L0 明细：

| 编号 | 内容 | 状态 |
|---|---|---|
| L0-1 | 商品列表（表格 + 分页 + 筛选 + 加载/空/错三态） | 完成 |
| L0-2 | 发布商品（5 字段表单 + 元→分换算 + 前端校验 + 成功反馈） | 完成 |
| L0-3 | `GET /api/products` 列表接口（关键词/状态/分类/分页） | 完成 |
| L0-4 | `GET /api/products/{id}` 详情接口（404 / 400 分支） | 完成 |
| L0-5 | `PATCH /api/products/{id}/status` 上下架 + 前端即时回写 | 完成 |

L1 明细：

| 编号 | 内容 | 状态 |
|---|---|---|
| L1-1 | `GET /api/orders?buyerPhone=` 订单列表接口（按手机号归属 + 分页） | 完成 |
| L1-2 | 我的订单（表格 + 加载/空/错三态 + 中文状态 + 时间格式化） | 完成 |
| L1-3 | 下单（数量/收货人/手机号/地址校验 + 服务端算金额 + 库存扣减） | 完成 |
| L1-4 | 库存与订单同事务：库存不足 `40901`、已下架 `40902`、重复支付 `40903` | 完成 |

### 前端分成了几个页面

用 `vue-router@4` + history 模式（不是 hash——题目要求「地址直接粘到新标签页也能打开」）：

| 地址 | 页面 | 组件 |
|---|---|---|
| `/` | 重定向到 `/products` | — |
| `/products` | 商品列表（筛选 / 上下架 / 点标题进详情） | `views/ProductListView.vue` |
| `/products/:id` | 商品详情（6 个字段 + 描述 + 直接下单） | `views/ProductDetailView.vue` |
| `/publish` | 发布商品 | `views/PublishView.vue` |
| `/orders` | 我的订单 | `views/OrderListView.vue` |
| 其它 | 「页面不存在」+ 回列表 | `views/NotFoundView.vue` |

`App.vue` 退化成外壳（页头 + `<AppNav />` + `<RouterView />` + toast 区），
业务状态分别落在各页面，换页时自然重建；toast 和「下单用的手机号」是模块级单例，
跨页面仍然连得上（发布成功跳回列表还能看到提示、改过手机号后「我的订单」仍能查到那一单）。

## 测试 / 自检

```bash
# 前端：69 条
cd frontend && npm test

# 后端：47 条
cd backend && ./mvnw -o test
```

前端输出：

```
 ✓ test/money.test.js (24 tests)
 ✓ test/orders.test.js (18 tests)
 ✓ test/products.test.js (7 tests)
 ✓ test/router.test.js (11 tests)
 ✓ test/proxy.test.js (9 tests)

 Test Files  5 passed (5)
      Tests  69 passed (69)
```

`proxy.test.js` 锁的是「前端凭什么能连上后端」这件事本身：
`resolveApiTarget()` 的四级优先级（`API_TARGET` > `MOCK_PORT` > `BACKEND_PORT` > 8080）、
`proxy['/api']` 的目标与 `changeOrigin`、`strictPort` 固定 5173。
只要有人改坏代理配置，这几条会立刻红。

`router.test.js` 用 `createMemoryHistory()` 在 node 环境跑，断言路由表本身：
五个页面的名字与路径、`/` 的重定向、`/products/3` 解析出 `params.id === '3'`、
`props: true` 是否生效、所有页面是否懒加载、未知地址是否落到 `not-found`、
`?highlight=12` 的 query 是否原样保留。

后端输出：

```
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- in com.example.demo.DemoApplicationTests
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.demo.product.ProductCreateTests
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.demo.product.ProductQueryTests
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.demo.order.OrderCreateTests
[INFO] Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0 -- in com.example.demo.order.OrderQueryTests
[INFO] Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

测的都是**接口契约**，不是实现细节：

- 前端 `money.test.js`：`yuanToFen` / `fenToYuan` 的舍入、负数、超两位小数、`0`、空串、超大值。
- 前端 `products.test.js`：`sellerName` 是否补齐、`description` 默认空串、请求体字段集合是否**精确匹配**、
  分页默认值、空筛选项是否被下发。
- 前端 `orders.test.js`：下单请求体字段集合、数量/手机号校验、`buyerPhone` 是否必带、
  错误码到中文文案的映射（`40901` 库存不足 / `40902` 已下架）。
- 前端 `router.test.js`：路由表、根路径重定向、详情页 params、未知地址兜底。
- 后端 `ProductCreateTests`：201、字段校验顺序、非法分类、价格非整数分、超长字段、显式 `description: null` 归一为空串。
- 后端 `ProductQueryTests`：列表筛选、分页越界、404/400 分支、状态变更幂等。
- 后端 `OrderCreateTests`：201、`quantity` 非整数/0/负数、手机号非 11 位、金额由服务端算、
  库存不足 40901、已下架 40902、库存真的减了。
- 后端 `OrderQueryTests`：按手机号归属、`buyerPhone` 必填、分页、状态文案。

## 没做完的地方

| 项 | 状态 | 原因 |
|---|---|---|
| L2 其它挑战（取消订单 / 支付 / 并发 / 导出…） | 未做 | 时间不够，只挑了与「多页面」直接相关的 L2-13 B |
| 真实持久化（文件库 / MySQL） | 未做 | 刻意选内存库，见 `DESIGN.md` §5 |


已知问题：

1. **没有做乐观锁**。两个浏览器同时点同一行的上下架，后写的赢，不会报冲突。L0 的量级下可以接受，
   但真实场景要加 `version` 字段或 `WHERE status = ?` 条件更新。
2. **列表一次性拉 50 条**。后端 `pageSize` 上限是 50，前端默认就取 50，所以 24 条时等于不分页。
   商品上千条时这个默认值要改小，并把分页控件接上。
3. **关键词搜索是 `LIKE` 全表扫**。数据量小无所谓，量大了要建索引或用全文检索。
4. `backend/src/test/java/com/example/demo/ChatControllerTests.java` 是 0 字节空文件，我没动它。

## 自检清单

- [x] 删掉依赖目录后，按上面的命令能重新跑起来（`node_modules` / `target` 删过，重装重跑通过）
- [x] 没提交 `node_modules` / `target` / `*.db` / `.env`（`.gitignore` 已覆盖）
- [x] `DESIGN.md`、`AI_USAGE.md` 已填写完整
- [x] 截图放进了 `screenshots/`（14 张，见下）

## 截图对应关系

| 文件 | 内容 |
|---|---|
| `l0-1-product-list.png` | 商品列表，25 行（24 种子 + 1 条我发的），含缺货/已下架/大额/emoji/超长标题/XSS 标题 |
| `l0-2-publish-form.png` | 发布表单空态 |
| `l0-2-publish-form-filled.png` | 填完表单，显示「将提交 price = 1250（分），显示为 ¥12.50」 |
| `l0-3-publish-success.png` | 发布成功，绿色反馈「发布成功：二手无线鼠标（¥12.50）」，表单已清空 |
| `l0-4-status-toggle.png` | 上下架：该行状态徽标变「已下架」，按钮变「上架」，顶部 toast「《二手无线鼠标》已下架」 |
| `l0-5-empty-state.png` | 搜索 `zzz不存在的商品` 无结果的空态 + 「清空筛选」 |
| `l0-5-loading-state.png` | 请求被延迟 4s 时捕获的加载态（spinner + 「加载中…」） |
| `l1-1-order-list.png` | 我的订单：手机号 + 共 N 单 + 中文状态（待支付）+ 金额 ¥xx.xx |
| `l1-2-order-form.png` | 详情页点「购买」后的下单面板：数量、预估总价、收货人/手机号/地址 |
| `l2-1-product-list-routes.png` | 多页面后的列表页：顶部三个导航 + 「商品列表」高亮，行标题是可点进详情的链接 |
| `l2-2-product-detail.png` | `/products/1` 详情页：价格/库存/分类/状态/卖家/上架时间 + 描述 + 购买/返回 |
| `l2-3-detail-not-found.png` | `/products/9999`：**商品不存在** + 「没有找到 id 为 9999 的商品」+ 回列表 |
| `l2-4-not-found.png` | `/no-such-page`：**页面不存在** + 回显地址 + 回列表 |
| `l2-5-publish-page.png` | `/publish` 独立页面，「发布商品」导航高亮 |

