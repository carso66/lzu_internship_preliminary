# 设计说明（DESIGN）

---

## 1. 我做到了哪一步

- [x] L0：5 / 5 条（列表、发布、上下架、加载/空/错三态，且前后端真连上了）
- [x] L1：4 / 4 条（订单列表 / 下单 / 库存扣减 / 订单三态）
- [x] L2-13 B：路由（`/products/:id` 详情页 + 深链可打开 + 未知地址有提示）
- [ ] L2 其它挑战：未做

一句话：**我做了一个商品列表 + 发布 + 上下架 + 下单 + 我的订单的多页面应用，
从 Vue 拉到 Spring Boot 的 H2 库，换页靠 `vue-router` 的 history 路由，
`/products/3` 这种地址直接粘到新标签页也能打开。**


---

## 2. 我是怎么拆的（最重要）

| 顺序 | 我做的事 | 为什么排在这个位置 |
|---|---|---|
| 1 | 先把题目给的 mock 起起来，用 `curl` 打 `/api/products` 看真实返回的每个字段和类型 | 不知道 `price` 是分还是元、`status` 是 `on/off` 还是 `1/0`，后面所有代码都是猜的 |
| 2 | 手写 `schema.sql`，`DROP TABLE IF EXISTS` + `CREATE TABLE` | 表结构定下了字段类型（`price INTEGER` 存分），后面所有校验都以它为准；先建表才能在 H2 里试约束 |
| 3 | 后端先只做 `POST /api/products`，用 `curl` 打通 201 | 写是链路最短的一条，能最快验证「响应包装 / 异常映射 / 自增主键返回」这套骨架是对的 |
| 4 | 再做 `GET /api/products` 列表和 `/{id}` 详情 | 查询依赖前面建好的表和数据；列表的分页/筛选参数最多，放在骨架稳了之后才好调 |
| 5 | 最后做 `PATCH /{id}/status` | 它同时依赖「按 id 找到商品」（404 分支）和「写字段」，是前面几块的组合，放最后能复用已有代码 |
| 6 | 后端 5 个接口全绿了，才动前端 | 前端要对着**真实的**返回写，接口没定下来就写前端，等于把猜测写进两个地方 |
| 7 | 前端先写死数据的列表，确认渲染通，再接 `fetch` | 出问题能立刻分清是「渲染错」还是「请求错」，不用两边一起猜 |
| 8 | 接完 `fetch` 后补三态（加载/空/出错） | 三态是「请求真的发了」的副产品，先有请求才谈得上状态；一开始写三态只会写出一堆用不到的 `v-if` |
| 9 | 最后补测试 | 接口契约已经稳定，这时候写的断言才是契约，不会写完就跟着实现改 |
| 10 | 单页能跑通之后才拆多页面 | 先在一个 `App.vue` 里把数据和请求调通，`<script setup>` 的状态全在一处、排查最省事；等到要「从列表点进详情」「发布完跳回列表」时才引入 `vue-router`，拆出去只搬代码不改逻辑，回归风险最小 |

**为什么不是「先把前端页面全画完再写后端」**：我先试过这条路，画到发布表单就卡住了——
「价格输入 12.5，提交给后端是 12.5 还是 1250」这个问题前端自己回答不了，必须回头看接口约定。
所以顺序改成「接口先定，前端后跟」。

---

## 3. 我的程序分成哪几块

**后端**（`backend/src/main/java/com/example/demo/`）：

| 文件 / 模块 | 负责什么 | 为什么单独拆出来 |
|---|---|---|
| `common/ApiResponse` | 统一 `{code, message, data}` 包装 | 成功和失败长一个样，前端只写一套解析 |
| `common/ApiException` + `GlobalExceptionHandler` | 业务异常 → HTTP 状态码 + 业务码 | 控制器里只管 `throw`，不用每处 try-catch 拼响应体 |
| `product/ProductCreateRequest` | **入参校验**（title/price/stock/category/desc/sellerName 六项，顺序固定） | 校验逻辑放在这里，`Product` 就只表示「库里的一行」，不会被请求格式污染 |
| `product/ProductListQuery` | 查询参数校验 + 默认值（page=1, pageSize=10, 上限 50） | 列表、计数两条 SQL 共用一份清洗后的参数，不会出现「计数用 A 条件、明细用 B 条件」 |
| `product/ProductRepository` | 只写 SQL | 换数据库只改这一个文件；H2 的 `getGeneratedKeys` 差异也全烂在这里 |
| `product/ProductService` | 事务边界 + 组合逻辑（先查存在再更新） | 控制器不该知道「404 要在 update 之前判」；以后加库存扣减也加在这层 |
| `product/ProductController` | 只做 HTTP → Service 的翻译 | 薄到一眼能看完 |

**前端**（`frontend/src/`）：

| 文件 / 模块 | 负责什么 | 为什么单独拆出来 |
|---|---|---|
| `api/client.js` | `fetch` 封装、`ApiError{code,status}`、网络失败文案 | 换 baseURL、加 header、统一报错只改一处 |
| `api/products.js` | 商品接口的出入参映射（含 `sellerName` 补齐） | 「后端要什么」和「页面有什么」的差异集中在这，组件不用知道 |
| `api/orders.js` | 下单 / 订单列表的出入参映射 + 默认收货人 | 收货信息的默认值和字段拼装只写一遍 |
| `composables/useProducts.js` | 列表状态机：`items/total/loading/error/filters` + `reload/patchItemStatus` | 状态和请求绑在一起，组件只读不写；`patchItemStatus` 让上下架不用整表刷新 |
| `composables/useOrders.js` | 订单状态机 + 模块级 `buyerPhone` + `rememberBuyerPhone()` | 「用谁的手机号查订单」必须跨页面共享：下单页改了号码，订单页要跟着查 |
| `composables/useToasts.js` | 模块级 toast 列表 + `toast.success/error/info` | 渲染点在 `App.vue` 外壳、调用点在各个页面，状态必须提到模块作用域 |
| `components/ProductForm.vue` | 5 字段表单 + 元→分换算 + 前端校验 + 成功反馈 | 表单的脏活（校验、清空、反馈）不和列表搅在一起 |
| `components/ProductList.vue` | 筛选栏 + 三态 + 表格骨架 + `highlightId` | 只负责「怎么显示」，行内的交互交给子组件 |
| `components/ProductRow.vue` | 单行渲染 + `pending` 态禁用按钮 + 标题作为详情链接 | 行是唯一需要处理「正在请求」粒度的单位 |
| `components/OrderForm.vue` | 下单面板（数量 / 收货人 / 手机号 / 地址）+ 预估总价 | 下单是一个独立动作，不该塞进列表行里 |
| `components/OrderList.vue` | 订单三态 + 中文状态 + 时间格式化 | 与商品列表不同的表结构，共用三态视觉语言但不共用组件 |
| `components/AppNav.vue` | 三个导航项 + 自己算 active | `/products/3` 的 `matched` 里没有 `/products`，用内置 `router-link-active` 详情页时导航会一条都不亮 |
| `router/index.js` | 路由表 + `createAppRouter(history)` 工厂 | 工厂形式让单测能注入 `createMemoryHistory()`，不必装 jsdom |
| `views/*.vue` | 五个页面各管自己的状态与请求 | 换页即重建，天然不需要手动清理上一页的状态 |
| `utils/money.js` | `yuanToFen` / `fenToYuan` | 钱的换算最容易出错，独立成纯函数才好测（24 条测试打这里） |
| `utils/order.js` | 订单状态文案 / 徽标类名 / 时间格式化 | 同一套文案在两处用（列表 + 详情提示），独立成一列常量 |
| `vite.config.js` | 5173 固定端口 + `/api` 代理 + `resolveApiTarget()` 优先级 | 「前端凭什么连得上后端」的唯一开关，独立成可导出的纯函数才能被测试锁住 |

**如果再加「退款」和「优惠券」，哪里最先乱掉？**

`ProductService`。现在它是「一个实体 + 几条简单规则」，退款要跨商品/订单/支付三方改状态，
优惠券要按订单金额算折扣再回写——这些塞进 `ProductService` 会让它变成 800 行的上帝类。

正确的做法是那时候**按动作拆**：新建 `OrderService`（下单/取消）、`RefundService`（退款）、
`CouponService`（算价），并让它们的公共部分（金额计算、状态机）落到一个独立的 `pricing` 包里。
我现在没这么拆，是因为 L0 只有商品一个实体，提前拆出 5 个空 Service 反而是负担。

---

## 4. 一次完整链路是怎么走的

选「发布商品」，因为它是唯一一条**从用户输入一直走到数据库再回到界面**的完整链路：

```
用户在 ProductForm 填：标题 / 价格 12.50 / 库存 3 / 分类 数码 / 描述
  → 前端校验：标题 1~50 字、价格是 >0 的数字且最多两位小数、库存 0~999 整数、分类必选
      ── ✗ → 字段下方红字，不发请求
  → 元→分：yuanToFen('12.50') = 1250（Math.round(x*100)，避免 12.5*100=1249.9999）
      同时在表单右侧实时显示「将提交 price = 1250（分），显示为 ¥12.50」← 让换算过程可见
  → POST /api/products
      body = { title, price: 1250, stock: 3, category: '数码', description, sellerName: '我' }
      （sellerName 是后端必填、mock 却忽略的差异项，前端在这里补齐）
  → 后端 ProductCreateRequest.parse：JSON 是对象 → title → price → stock → category → description → sellerName
      任何一项不合法 → 400 + 40001 + 具体 message
  → ProductService.create：@Transactional 插入一行
  → ProductRepository.insert：prepareStatement(sql, new String[]{"id"})
      （不能只写 RETURN_GENERATED_KEYS：H2 会返回整行，GeneratedKeyHolder.getKey() 直接抛异常）
  → 201 + { code: 0, data: { id: 25, title: '二手无线鼠标', price: 1250, ... } }
  → 前端拿到 201 → 绿色反馈「发布成功：二手无线鼠标（¥12.50）」+ 清空 5 个输入框
  → PublishView.onCreated 调 router.push({ name:'products', query:{ highlight: 25 } })
  → 跳回列表页，ProductList 把 highlightId 传给 ProductRow → 该行加 row-new 类并滚进视野，用户直接看到
```

再来一条**下单 → 我的订单**的链路，它跨了两个页面，是这一轮最容易被问的地方：

```
用户在 /products/3 详情页点「购买」→ 展开 OrderForm，数量 2
  → 前端算预估总价：multiplyFen(unitPrice, 2)，全程整数分
  → POST /api/orders
      body = { productId: 3, quantity: 2, buyerName, buyerPhone: '13800138000', address }
  → 后端 OrderService：@Transactional 里先 SELECT ... FOR UPDATE 锁商品行
      ── 已下架 → 409 + 40902；库存 < 2 → 409 + 40901（都不落单）
      → 扣库存 UPDATE product SET stock = stock - 2
      → 插入 order（status 硬编码 'created'，orderNo 由日期 + 序列拼）
  → 201 + { code: 0, data: { orderNo: 'SO202611250001', totalAmount: 2500, ... } }
  → rememberBuyerPhone('13800138000') 写进 useOrders 的模块级 ref
  → toast「下单成功，订单号 SO202611250001」，商品库存就地 5 → 4（不整表刷新）
  → 用户点导航「我的订单」→ /orders 挂载时读到共享的 buyerPhone
      → GET /api/orders?buyerPhone=13800138000 → 刚下的那一单在列表里
```

`rememberBuyerPhone` 是这一步的关键：手机号存在 `composables/useOrders.js` 的**模块作用域**里，
而不是存在某个组件的 `data` 里。组件随路由销毁重建，模块不会——所以换页面之后还查得到同一批订单。

对照的失败分支（也在截图/测试里覆盖）：

```
POST 返回 400（比如标题为空）
  → client.js 把 { code, message } 包成 ApiError
  → ProductForm 捕获，显示 message，按钮恢复可用，表单内容不清空（用户不用重填）
POST 网络断了（后端没起）
  → fetch 抛 TypeError，client.js 转成「无法连接后端（http://localhost:8080），请确认已 mvnw spring-boot:run」
```

---

## 5. 一个取舍：我选了 A，没选 B

- **可以在哪两个方案里选**：H2 **内存库** / H2 **文件库**（`jdbc:h2:file:./data/market`）
- **我选了**：内存库
- **因为**：评审照着我写的命令跑一遍时，内存库保证每次启动都是**同样的 24 条种子数据**。
  文件库第一次跑完之后，如果评审在同一台机器上再跑一次，看到的就是我上次留下的数据——
  截图上写的「共 24 件」和实际数量对不上，验收会变成「这到底是不是 bug」的扯皮。
- **代价是**：重启就丢掉我刚发布的商品。所以我把它写进 `SUBMISSION.md` 的数据说明里，
  并明确「没有 `/api/_reset`，重启就是复位」——用**重启**代替复位接口，少一个要维护的端点。

选内存库就带出一个必须交代的事：**那 24 条种子数据是哪来的**。
`backend/data.sql` 是我**逐行手写**的，不是框架生成的。判断依据是项目里根本没有能生成它的东西：
`pom.xml` 的依赖只有 `spring-boot-starter-web` / `-jdbc` / `h2` / `-test`，
没有 Flyway、Liquibase、JPA（Hibernate 的 `ddl-auto`）、jOOQ 或 MyBatis Generator，
全仓库 grep 也搜不到这些关键字。`spring.sql.init.data-locations` 只是**执行** `data.sql`，
不负责**产出**它。

那 24 条的内容不是我编的，是照着题目给的 `frontend/mock/db.json` 一条条转写成 `INSERT`，
所以「直连后端」和「用 mock」看到的是同一批数据。转写时我特意保留了 mock 里那些边界样本
（`stock=0` 缺货、`status=off` 已下架、`price=9999999`、50 字标题、`<script>` 标题、emoji），
因为它们是唯一能验证「表格不会撑破、XSS 不会被当 HTML 渲染、分页边界正确」的输入。
如果只塞 24 条「耳机 99 元」的规整数据，截图好看，但等于没测。

另一组我也真纠结过的取舍：**前端走 Vite proxy 还是直接跨域打 8080**。
选了 proxy，因为后端没配 CORS，proxy 让浏览器看起来是同源，省掉一段容易配错的 CORS 配置；
代价是前端多依赖一个中间层，`API_TARGET` 没设对时会 502 而不是明确的跨域报错。

这个取舍是**实测出来**的，不是照文档抄的。当时先给后端发了两个请求：

```
GET     /api/products   Origin: http://localhost:5173  →  200，响应里没有 Access-Control-Allow-Origin
OPTIONS /api/products   Access-Control-Request-Method: POST  →  403 Forbidden
```

结论很清楚：`GET` 浏览器会拿到 200 但**把响应体丢掉**（缺 `Allow-Origin`），
`POST / PATCH` 因为带 `Content-Type: application/json` 不是简单请求，会先被预检挡在 403。
也就是说前端如果写成 `http://localhost:8080/api`，五个功能里只有「列表」勉强能看到半个结果。
所以必须二选一：要么在后端加 `WebMvcConfigurer` 开 CORS，要么让请求同源。选了后者。

因为这条链路是「看不见的」，我把 `resolveApiTarget()` 从 `vite.config.js` 里 `export` 出来，
并加了 `test/proxy.test.js` 锁住代理目标和端口。这样「前端凭什么能连上后端」有测试兜着，
而不是靠某次手动点通了就算数。

---

## 6. 我卡得最久的一个问题

- **现象**：`.\mvnw spring-boot:run` 在终端里跑，直接失败退出，看不出跟代码有什么关系。
- **我一开始以为**：JDK 版本不对。因为 `java -version` 显示的是 **27**，而 Spring Boot 3.x 只支持到 17/21。
  这个判断看起来非常合理——报错里确实提到了 class file version。
- **试过什么**：
  1. 手动 `set JAVA_HOME=...jdk-17`，再跑 —— **还是失败**，而且报错信息变了，说明原来那条不是根因。
  2. 写 `.mvn/jdk-env.cmd` / `.mvn/jdk-env.sh` 让 `mvnw` 自己探测 JDK 17（这一步**留下了**，确实有用，
     但现在机器上默认 JDK 27，别人拿去跑还会踩，所以这个修改本身是对的）。
  3. 用 `mvnw -X` 看完整日志、把 `spring-boot:run` 换成 `mvnw -o test` 单独跑 ——
     **`test` 全绿**。这就排除了代码和 JDK。
- **最后靠什么定位的**：看 `spring-boot:run` 的完整报错尾部，`Failed to start ... Web server failed to start.
  Port 8080 was already in use`。再用 `Get-NetTCPConnection -LocalPort 8080 -State Listen` 看到
  一个上次没关干净的 java 进程还占着端口。
- **真正的原因**：**不是代码问题，不是 JDK 问题，是端口被残留进程占用**。
  `spring-boot:run` 的启动失败信息被 Maven 包了一层，前半段又长得像 JDK 版本错，所以前两次全看错了方向。
- **之后我改了做法**：遇到「启动就退出」先分三件事查：①端口有没有被占 ②依赖有没有下完
  ③代码能不能编译（`mvnw -o test` 一条命令就能排除第 3 项）。
  这个顺序是从「最不可能是代码」往「最有可能是代码」走，因为构建/环境类问题的概率远高于业务代码。

---

## 7. 没做的事和已知问题

| 项 | 状态 | 原因 |
|---|---|---|
| L2 其它挑战（取消订单 / 支付 / 并发 / 导出） | 没做 | 时间不够，只做了与「多页面」相关的 L2-13 B 路由 |
| 分页控件 | 没做 UI | 后端 `pageSize` 上限 50、前端默认取 50，24 条时等于不分页；上千条时必须补 |
| 上下架并发保护 | 没做 | 两个浏览器同时点同一行，后写赢，不报冲突。需要 `version` 字段或 `WHERE status = ?` 条件更新 |
| 关键词搜索 | `LIKE '%kw%'` 全表扫 | 数据量小无所谓，量大要建索引或换全文检索 |
| 后端 CORS | 没配 | 走 Vite proxy 规避；如果前端要独立部署（不同域），必须回来补 |
| `ChatControllerTests.java` | 0 字节空文件没动 | 起步模板自带的空测试类，我没有对应的 `ChatController` 要测 |

---

## 8. 如果还有一天

1. **补上下架的乐观锁（`version` 字段 + `WHERE id=? AND version=?`，冲突返回 409）。**
   排第一是因为它是**已知的正确性缺陷**：现在这个实现在两人同时操作时会静默覆盖，
   这种 bug 不会报错、只会让数据慢慢变脏，比「没做完的功能」危险得多。
2. **补上「取消订单 / 支付」两个动作（`POST /api/orders/{id}/pay` 这类）。**
   排第二是因为订单已经能建、能查，但状态机只有 `created` 一个出口——
   下单之后既不能付也不能撤，闭环缺最后一环。它同时会逼我把订单状态流转写成
   一条显式的合法迁移表（`created → paid / cancelled`），而不是散在 if 里。
3. **补分页 UI + 把默认 `pageSize` 从 50 降到 20。**
   排第三是因为它影响的是「数据量变大之后」的体验，在 L0 的 24 条上完全看不出问题，
   优先级低于正确性、低于主线功能。
4. **把商品列表的筛选条件同步到 URL query（`/products?kw=键盘&status=on`）。**
   排最后是因为它是体验优化：现在筛选条件只活在组件里，刷新/分享就丢；
   同步到 URL 之后「把我看的这个筛选发给同学」才成立，也和已有的 `?highlight=` 用法一致。
