package com.zyh.marketplace;

import com.zyh.marketplace.entity.Order;
import com.zyh.marketplace.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 事务回滚验证：订单插入失败时，已扣减的库存必须由 @Transactional 回滚恢复。
 *
 * <p>用 @TestConfiguration 装饰真实的 OrderMapper：除 insert 外全部委托原实现，
 * insert 抛 RuntimeException，从而在「原子扣库存已执行、插订单失败」的节点制造真实失败。
 *
 * <p>为了让「异常确实发生在扣库存成功之后」可被证明（而不是靠假设），insert 失败前先用
 * 事务内的 JdbcTemplate 读库存：
 *   - 读到的是同一事务未提交的 3（原 5 扣掉 2）→ 证明扣减已生效且与订单插入共用同一事务；
 *   - 若读到 5，说明扣减没发生，测试直接失败，不会给出“假通过”的回滚结论。
 * 断言：接口返回 500 + 50000；products.stock 恢复原值 5；orders 表无残留。
 */
class L1TransactionRollbackTest extends ApiTestSupport {

    /** insert 失败前读到的库存值（-1 表示未执行到读库存这一步） */
    static final java.util.concurrent.atomic.AtomicInteger STOCK_SEEN_IN_FAILED_TX =
            new java.util.concurrent.atomic.AtomicInteger(-1);

    @TestConfiguration
    static class InsertFailureConfig {

        @Bean
        @Primary
        OrderMapper orderMapperThatFailsInsert(@Qualifier("orderMapper") OrderMapper delegate,
                                               JdbcTemplate jdbcTemplate) {
            return new OrderMapper() {
                @Override
                public int insert(Order order) {
                    // 证明：此刻我们仍在 OrderService.create() 的事务里，且库存已被扣减
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .as("insert 时必须有活动事务")
                            .isTrue();
                    Integer stock = jdbcTemplate.queryForObject(
                            "SELECT stock FROM products WHERE id = ?", Integer.class, order.getProductId());
                    STOCK_SEEN_IN_FAILED_TX.set(stock == null ? -1 : stock);
                    // 模拟插订单失败（约束冲突、宕机等）
                    throw new IllegalStateException("模拟订单插入失败");
                }

                @Override
                public Order findById(Long id) {
                    return delegate.findById(id);
                }

                @Override
                public Order findByOrderNo(String orderNo) {
                    return delegate.findByOrderNo(orderNo);
                }

                @Override
                public long countByBuyerPhone(String buyerPhone) {
                    return delegate.countByBuyerPhone(buyerPhone);
                }

                @Override
                public List<Order> findPageByBuyerPhone(String buyerPhone, int offset, int limit) {
                    return delegate.findPageByBuyerPhone(buyerPhone, offset, limit);
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        truncateProducts();
        STOCK_SEEN_IN_FAILED_TX.set(-1);
    }

    @Test
    @DisplayName("L1-6 插订单失败 → 异常发生在扣库存之后，库存回滚、订单不落库、响应 500 + 50000")
    void insertFailureRollsBackStockDeduction() throws Exception {
        insertProductDirectly("回滚验证商品", 4990L, 5, "图书", "on", "店铺", "2026-01-01 10:00:00");

        postOrder("{\"productId\":1,\"quantity\":2,\"buyerName\":\"张三\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"兰州市城关区天水南路222号\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.data").isEmpty());

        // 关键前置：失败时同一事务里已读到扣减后的 3，证明扣库存已成功、两者共用同一事务
        assertThat(STOCK_SEEN_IN_FAILED_TX.get())
                .as("insert 失败时应已读到扣减后的库存 3（5 - 2）")
                .isEqualTo(3);

        // 关键断言：已执行的扣库存被事务回滚，恢复为 5
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM products", Integer.class);
        assertThat(stock).isEqualTo(5);
        // 订单没有残留
        Integer orderRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        assertThat(orderRows).isZero();
    }
}
