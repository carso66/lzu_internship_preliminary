package com.zyh.marketplace;

import com.zyh.marketplace.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L0-1 建表回归：在隔离的内存库上执行真实的 schema.sql，
 * 校验 products / orders 及关键约束、索引确实被创建出来。
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaInitTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductMapper productMapper;

    @Test
    void productsAndOrdersTablesAreCreated() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC' ORDER BY table_name",
                String.class);
        assertThat(tables).contains("PRODUCTS", "ORDERS");
    }

    @Test
    void productsColumnLayoutMatchesSpec() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'PUBLIC' AND table_name = 'PRODUCTS' ORDER BY ordinal_position",
                String.class);
        assertThat(columns).containsExactly(
                "ID", "TITLE", "PRICE", "STOCK", "CATEGORY", "DESCRIPTION",
                "STATUS", "SELLER_NAME", "CREATED_AT");
    }

    @Test
    void keyConstraintsAndIndexExist() {
        List<String> constraints = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'PUBLIC' ORDER BY constraint_name",
                String.class);
        assertThat(constraints).contains(
                "CHK_PRODUCTS_PRICE", "CHK_PRODUCTS_STOCK", "CHK_PRODUCTS_STATUS",
                "CHK_ORDERS_QUANTITY", "CHK_ORDERS_STATUS", "CHK_ORDERS_AMOUNT_CALC",
                "UQ_ORDERS_ORDER_NO", "FK_ORDERS_PRODUCT");

        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes "
                        + "WHERE table_schema = 'PUBLIC' AND index_name = 'IDX_ORDERS_BUYER_PHONE_CREATED_AT'",
                Integer.class);
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    void mapperIsWired() {
        assertThat(productMapper).isNotNull();
        assertThat(productMapper.countByCriteria(
                new com.zyh.marketplace.dto.ProductPageCriteria(null, null, null, 0, 10))).isGreaterThanOrEqualTo(0);
    }
}
