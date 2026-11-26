package cn.edu.lzu.marketplace.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 根据下单日期和订单主键生成正式订单号，格式为 {@code SO + yyyyMMdd + 序号}。
 *
 * <p>主键在当前订单表内唯一，序号最少六位且不截断，数据库同时约束 order_no 唯一。
 * 日期部分仅取自下单时间的年月日，序号跨天继续递增。同日重启空 H2 内存库后可能重新生成相同编号。
 */
public final class OrderNoGenerator {

    /** 订单号前缀 */
    public static final String PREFIX = "SO";

    // 日期段与自增主键分别格式化，序号独立于日期变化。
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private OrderNoGenerator() {
    }

    /**
     * 由自增主键与下单时间生成正式订单号。
     *
     * @param id        订单自增主键（插入后回填）
     * @param createdAt 下单时间，仅取其日期段
     */
    public static String generate(long id, LocalDateTime createdAt) {
        return PREFIX + createdAt.format(DATE) + String.format("%06d", id);
    }
}
