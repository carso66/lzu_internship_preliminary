package cn.edu.lzu.marketplace.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证订单号的日期格式、序号补位以及同日不同订单 ID 的区分。
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

    // 同一日期下，序号部分应随订单 ID 变化。
    @Test
    void generateIsUniqueAcrossDifferentIds() {
        assertNotEquals(OrderNoGenerator.generate(1L, AT), OrderNoGenerator.generate(2L, AT));
    }

    @Test
    void generateDoesNotTruncateLargeId() {
        // 六位是最小宽度，较长的订单 ID 应完整保留。
        assertEquals("SO202611191234567", OrderNoGenerator.generate(1234567L, AT));
    }
}
