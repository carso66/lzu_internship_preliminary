package com.lzu.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 通过 MockMvc 走完整控制器、校验和服务层，数据写入独立测试库并在每个用例后回滚。
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:marketplace-list-test;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class ProductListTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    // 直接插入商品，隔离列表接口本身的逻辑，不依赖尚未实现的其他接口。
    private long insert(String title, long price, String category, String status) {
        return jdbc.queryForObject("""
                SELECT id FROM FINAL TABLE (
                    INSERT INTO products (title, price, stock, category, description, seller_name, status)
                    VALUES (?, ?, 10, ?, '', '卖家', ?)
                )
                """, Long.class, title, price, category, status);
    }

    @Test
    void emptyTableReturnsZeroTotalWithDefaults() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void paginatesAcrossPages() throws Exception {
        for (int i = 1; i <= 15; i++) {
            insert("商品" + i, 1000 + i, "文具", "on");
        }
        mvc.perform(get("/api/products").param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(15))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items", hasSize(5)));
    }

    @Test
    void filtersByKeywordStatusAndCategoryAndCombinesThem() throws Exception {
        insert("考研数学书", 4990, "图书", "on");
        insert("英语书", 2990, "图书", "off");
        insert("钢笔", 990, "文具", "on");

        // keyword 标题模糊匹配
        mvc.perform(get("/api/products").param("keyword", "书"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        // status 精确匹配
        mvc.perform(get("/api/products").param("status", "off"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("英语书"));

        // category 精确匹配
        mvc.perform(get("/api/products").param("category", "文具"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("钢笔"));

        // 三个条件叠加，只命中同时满足的一条
        mvc.perform(get("/api/products")
                        .param("keyword", "书").param("status", "on").param("category", "图书"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("考研数学书"));
    }

    @Test
    void ordersNewestFirst() throws Exception {
        insert("先发布的", 100, "其他", "on");
        insert("后发布的", 200, "其他", "on");
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("后发布的"));
    }

    @Test
    void rejectsInvalidStatus() throws Exception {
        mvc.perform(get("/api/products").param("status", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void rejectsPageSizeOutOfRange() throws Exception {
        mvc.perform(get("/api/products").param("pageSize", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        mvc.perform(get("/api/products").param("pageSize", "51"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void rejectsNonPositivePage() throws Exception {
        mvc.perform(get("/api/products").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void rejectsNonNumericPage() throws Exception {
        mvc.perform(get("/api/products").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
