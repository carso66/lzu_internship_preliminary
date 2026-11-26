package com.lzu.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Test
    void flywayCreatesAllTables() {
        for (String table : List.of("products", "orders", "payments")) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class,
                    table
            );
            assertThat(count).as("table %s should exist", table).isEqualTo(1);
        }
    }

    @Test
    void namedParameterJdbcTemplateIsAvailable() {
        Integer result = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE status = :status",
                Map.of("status", "on"),
                Integer.class
        );
        assertThat(result).isZero();
    }

    @Test
    void orderNumberSequenceIsAvailable() {
        Long sequence = jdbcTemplate.queryForObject(
                "SELECT NEXT VALUE FOR order_no_seq", Long.class);
        assertThat(sequence).isNotNull().isPositive();
    }

    @Test
    void paymentNumberSequenceIsAvailable() {
        Long sequence = jdbcTemplate.queryForObject(
                "SELECT NEXT VALUE FOR payment_no_seq", Long.class);
        assertThat(sequence).isNotNull().isPositive();
    }
}
