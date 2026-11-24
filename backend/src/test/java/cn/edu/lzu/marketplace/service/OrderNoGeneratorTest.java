package cn.edu.lzu.marketplace.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单号生成策略单元测试：格式、零填充、唯一性。
 */
class OrderNoGeneratorTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 19, 10, 0, 0);

    @Test
    void generateUsesDateAndZeroPaddedId() {
        assertEquals("SO20261119000001", OrderNoGenerator.generate(1L, AT));
        assertEquals("SO20261119000013", OrderNoGenerator.generate(13L, AT));
    }

    @Test
    void generateMatchesExpectedPattern() {
        String no = OrderNoGenerator.generate(42L, AT);
        assertTrue(no.matches("SO\\d{8}\\d{6}"), "订单号应满足 SO+8位日期+6位序号: " + no);
    }

    @Test
    void generateIsUniqueAcrossDifferentIds() {
        assertNotEquals(OrderNoGenerator.generate(1L, AT), OrderNoGenerator.generate(2L, AT));
    }

    @Test
    void generateDoesNotTruncateLargeId() {
        // 超过 6 位的序号自然扩展，不截断，保证大 id 下仍唯一
        assertEquals("SO202611191234567", OrderNoGenerator.generate(1234567L, AT));
    }
}
