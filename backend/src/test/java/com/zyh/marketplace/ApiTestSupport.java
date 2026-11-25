package com.zyh.marketplace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 接口测试基类：真实 Spring 上下文 + MockMvc（走完整的 MVC 链路：参数解析、异常处理、JSON 序列化），
 * 数据源为隔离的内存库（application-test.yaml），与 ./data/marketplace 完全无关。
 *
 * <p>测试方法执行前会清空 products 表，保证每个用例数据独立。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected void truncateProducts() {
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM products");
        jdbcTemplate.execute("ALTER TABLE products ALTER COLUMN id RESTART WITH 1");
    }

    protected ResultActions postProduct(String json) throws Exception {
        return mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    protected ResultActions listProducts(String query) throws Exception {
        return mockMvc.perform(get("/api/products" + (query == null ? "" : "?" + query)));
    }

    /**
     * 用 queryParam 传原始查询参数（不手工 URL 编码）。
     * MockMvc 默认用 ISO-8859-1 解析 URI 中的查询串，若直接在 URI 里放中文编码会变成乱码；
     * queryParam 由请求构建器负责编码，等价于真实 HTTP 客户端发出的 UTF-8 查询串。
     */
    protected ResultActions listProductsWithParams(String... nameValuePairs) throws Exception {
        var builder = get("/api/products");
        for (int i = 0; i + 1 < nameValuePairs.length; i += 2) {
            builder.queryParam(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return mockMvc.perform(builder);
    }

    protected ResultActions getProduct(String idSegment) throws Exception {
        return mockMvc.perform(get("/api/products/{id}", idSegment));
    }

    protected ResultActions patchStatus(String idSegment, String json) throws Exception {
        return mockMvc.perform(patch("/api/products/{id}/status", idSegment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** 构造一个合法商品 JSON（可覆盖单个字段由子类自行拼接） */
    protected static String validProductJson(String title, long price, long stock, String category, String seller) {
        return "{\"title\":\"" + title + "\",\"price\":" + price + ",\"stock\":" + stock
                + ",\"category\":\"" + category + "\",\"description\":\"备注\",\"sellerName\":\"" + seller + "\"}";
    }

    protected long insertProductDirectly(String title, long price, int stock, String category, String status,
                                         String seller, String createdAt) {
        jdbcTemplate.update("INSERT INTO products (title, price, stock, category, description, status, seller_name, created_at) "
                        + "VALUES (?, ?, ?, ?, NULL, ?, ?, CAST(? AS TIMESTAMP))",
                title, price, stock, category, status, seller, createdAt);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM products", Long.class);
        return id == null ? -1 : id;
    }
}