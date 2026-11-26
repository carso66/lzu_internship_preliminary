package com.zyh.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L0-2 POST /api/products —— 发布商品。
 * 覆盖：正常发布 / 必填 / 长度 / 金额边界与类型 / 库存边界与类型 / 非法 JSON。
 */
class L02CreateProductApiTest extends ApiTestSupport {

    @BeforeEach
    void setUp() {
        truncateProducts();
    }

    @Test
    @DisplayName("L0-2 正常发布：HTTP 201 + code 0 + 全部字段回显 + 默认状态 on")
    void createProductSuccess() throws Exception {
        postProduct("{\"title\":\"测试商品\",\"price\":1990,\"stock\":10,\"category\":\"文具\","
                + "\"description\":\"备注\",\"sellerName\":\"校园数码铺\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("测试商品"))
                .andExpect(jsonPath("$.data.price").value(1990))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.category").value("文具"))
                .andExpect(jsonPath("$.data.description").value("备注"))
                .andExpect(jsonPath("$.data.status").value("on"))
                .andExpect(jsonPath("$.data.sellerName").value("校园数码铺"))
                .andExpect(jsonPath("$.data.createdAt").isString());
    }

    @Test
    @DisplayName("L0-2 落库校验：price 以整数分写入 BIGINT，未被转换成浮点/元")
    void createProductStoresIntegerCents() throws Exception {
        postProduct(validProductJson("分单位校验", 1990, 10, "文具", "店铺")).andExpect(status().isCreated());
        Long price = jdbcTemplate.queryForObject("SELECT price FROM products WHERE title = ?", Long.class, "分单位校验");
        assertThat(price).isEqualTo(1990L);
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM products WHERE title = ?", Integer.class, "分单位校验");
        assertThat(stock).isEqualTo(10);
        String status = jdbcTemplate.queryForObject("SELECT status FROM products WHERE title = ?", String.class, "分单位校验");
        assertThat(status).isEqualTo("on");
        String priceType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name = 'PRODUCTS' AND column_name = 'PRICE'",
                String.class);
        assertThat(priceType).isEqualToIgnoringCase("BIGINT");
    }

    @Test
    @DisplayName("L0-2 description 可选：不传时返回 null 且 201")
    void descriptionIsOptional() throws Exception {
        postProduct("{\"title\":\"无描述\",\"price\":1,\"stock\":0,\"category\":\"文具\",\"sellerName\":\"店铺\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.description").isEmpty());
    }

    @Test
    @DisplayName("L0-2 stock=0 合法（与 schema CHECK stock >= 0 一致）")
    void zeroStockIsValid() throws Exception {
        postProduct(validProductJson("零库存", 1990, 0, "文具", "店铺"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stock").value(0));
    }

    @Test
    @DisplayName("L0-2 边界：title 100 / category 32 / sellerName 64 / description 1000 字符均可用")
    void maxLengthBoundariesAccepted() throws Exception {
        String title = "t".repeat(100);
        String category = "c".repeat(32);
        String seller = "s".repeat(64);
        String desc = "d".repeat(1000);
        postProduct("{\"title\":\"" + title + "\",\"price\":1,\"stock\":1,\"category\":\"" + category
                + "\",\"description\":\"" + desc + "\",\"sellerName\":\"" + seller + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value(title))
                .andExpect(jsonPath("$.data.category").value(category))
                .andExpect(jsonPath("$.data.sellerName").value(seller));
    }

    @Test
    @DisplayName("L0-2 超长：title 101 / category 33 / sellerName 65 / description 1001 均 400 且指明字段")
    void overMaxLengthRejected() throws Exception {
        postProduct("{\"title\":\"" + "t".repeat(101) + "\",\"price\":1,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("title")));
        postProduct("{\"title\":\"t\",\"price\":1,\"stock\":1,\"category\":\"" + "c".repeat(33) + "\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("category")));
        postProduct("{\"title\":\"t\",\"price\":1,\"stock\":1,\"category\":\"c\",\"sellerName\":\"" + "s".repeat(65) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("sellerName")));
        postProduct("{\"title\":\"t\",\"price\":1,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\",\"description\":\""
                + "d".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("description")));
    }

    @Test
    @DisplayName("L0-2 缺少必填字段：逐个缺失都返回 400 + 40001 且指明字段")
    void missingRequiredFieldsRejected() throws Exception {
        String[][] cases = {
                {"title", "{\"price\":1990,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}"},
                {"price", "{\"title\":\"t\",\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}"},
                {"stock", "{\"title\":\"t\",\"price\":1990,\"category\":\"c\",\"sellerName\":\"s\"}"},
                {"category", "{\"title\":\"t\",\"price\":1990,\"stock\":1,\"sellerName\":\"s\"}"},
                {"sellerName", "{\"title\":\"t\",\"price\":1990,\"stock\":1,\"category\":\"c\"}"}
        };
        for (String[] c : cases) {
            postProduct(c[1])
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001))
                    .andExpect(jsonPath("$.data").isEmpty())
                    .andExpect(jsonPath("$.message").value(containsString(c[0])));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isZero();
    }

    @ParameterizedTest(name = "空标题 [{0}] → 400")
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("L0-2 空标题 / 纯空白标题 → 400 + 40001")
    void blankTitleRejected(String title) throws Exception {
        postProduct(validProductJson(title, 1990, 1, "c", "s"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("title")));
    }

    @Test
    @DisplayName("L0-2 必填字符串传 null → 400")
    void nullRequiredStringRejected() throws Exception {
        postProduct("{\"title\":null,\"price\":1990,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("title")));
    }

    @Test
    @DisplayName("L0-2 零价格 price=0 → 400（与 schema CHECK price > 0 一致）")
    void zeroPriceRejected() throws Exception {
        postProduct(validProductJson("t", 0, 1, "c", "s"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("price")));
    }

    @Test
    @DisplayName("L0-2 负数价格 price=-1 → 400")
    void negativePriceRejected() throws Exception {
        postProduct(validProductJson("t", -1, 1, "c", "s"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("price")));
    }

    @Test
    @DisplayName("L0-2 小数价格 19.9 / 1990.0 → 400，不会被静默截断成整数落库")
    void decimalPriceRejected() throws Exception {
        postProduct("{\"title\":\"t\",\"price\":19.9,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("price")));
        postProduct("{\"title\":\"t\",\"price\":1990.0,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isZero();
    }

    @Test
    @DisplayName("L0-2 字符串价格 \"1990\" → 400")
    void stringPriceRejected() throws Exception {
        postProduct("{\"title\":\"t\",\"price\":\"1990\",\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("price")));
    }

    @Test
    @DisplayName("L0-2 布尔价格 true → 400")
    void booleanPriceRejected() throws Exception {
        postProduct("{\"title\":\"t\",\"price\":true,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("price")));
    }

    @Test
    @DisplayName("L0-2 数值溢出：超过 long / 科学计数 / 超业务上限 → 400；上限值本身可用")
    void priceOverflowRejected() throws Exception {
        postProduct("{\"title\":\"t\",\"price\":9999999999999999999999,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        postProduct("{\"title\":\"t\",\"price\":1e400,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        postProduct(validProductJson("t", 1_000_000_000L, 1, "c", "s"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        postProduct(validProductJson("上限值", 999_999_999L, 1, "c", "s"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("L0-2 负数库存 stock=-1 → 400")
    void negativeStockRejected() throws Exception {
        postProduct(validProductJson("t", 1990, -1, "c", "s"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("stock")));
    }

    @Test
    @DisplayName("L0-2 非法库存类型：\"10\" / 1.5 / true → 400；stock 超 int 范围 → 400")
    void invalidStockTypeRejected() throws Exception {
        postProduct("{\"title\":\"t\",\"price\":1990,\"stock\":\"10\",\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("stock")));
        postProduct("{\"title\":\"t\",\"price\":1990,\"stock\":1.5,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        postProduct("{\"title\":\"t\",\"price\":1990,\"stock\":true,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        postProduct("{\"title\":\"t\",\"price\":1990,\"stock\":2147483648,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("stock")));
    }

    @Test
    @DisplayName("L0-2 非字符串 title（数字）→ 400 而不是 500")
    void nonStringTitleRejected() throws Exception {
        postProduct("{\"title\":123,\"price\":1990,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("title")));
    }

    @ParameterizedTest(name = "非法 JSON 用例 #{index}")
    @ValueSource(strings = {
            "",
            "   ",
            "not json",
            "{\"title\":\"t\",",
            "[1,2,3]",
            "\"just a string\"",
            "{\"title\":\"t\" \"price\":1}"
    })
    @DisplayName("L0-2 非法 JSON / 空请求体 / 非对象 JSON → 400 + 40001")
    void invalidJsonRejected(String body) throws Exception {
        postProduct(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("L0-2 缺少 Content-Type → 400 且响应体仍是统一结构")
    void missingContentTypeReturnsUnifiedBody() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/products")
                        .content("{\"title\":\"t\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("L0-2 未知字段 → 400 并提示字段名")
    void unknownFieldRejected() throws Exception {
        postProduct("{\"title\":\"t\",\"price\":1990,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\",\"stockk\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("stockk")));
    }

    @Test
    @DisplayName("L0-2 连续发布两条：id 单调自增，互不覆盖")
    void sequentialCreatesGetDistinctIds() throws Exception {
        postProduct(validProductJson("A", 100, 1, "c", "s")).andExpect(status().isCreated());
        postProduct(validProductJson("B", 200, 2, "c", "s")).andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT id) FROM products", Integer.class)).isEqualTo(2);
    }
}