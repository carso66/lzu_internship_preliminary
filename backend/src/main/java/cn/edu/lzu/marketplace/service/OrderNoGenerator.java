package cn.edu.lzu.marketplace.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 订单号生成策略。
 *
 * <p>格式：{@code SO + yyyyMMdd + 序号}，与 README L1-6 示例「SO202611190001」一致。
 * 序号直接取 orders 表的全局自增主键 id，用 {@code %06d} 零填充（超出 6 位时自然扩展，不截断）。
 *
 * <h3>唯一性论证</h3>
 * <ul>
 *   <li>id 是 orders 表的主键（自增），全局唯一；订单号把 id 编码进序号位，
 *       因此订单号也全局唯一，不依赖跨天重置。</li>
 *   <li>「日期」段仅作可读性，不参与唯一性判定；跨天序号不重置属于可接受的简化。</li>
 *   <li>数据库侧依赖 order_no 上的 UNIQUE 约束作为最终兜底（见 JdbcOrderRepository 表结构说明）。</li>
 * </ul>
 *
 * <h3>潜在冲突风险</h3>
 * <p>若将来希望「按天重置序号」（每天从 001 开始），单纯用自增主键无法做到，
 * 需在建表阶段引入独立序号表 / 序列 + 日期 + UNIQUE(order_no) 约束，
 * 并在冲突时重试。当前方案不依赖按天序号，故无此风险；唯一假设是同一事务内
 * 先用临时占位号插入、再用正式编号覆盖，占位号与正式编号均不会与他人冲突。
 */
public final class OrderNoGenerator {

    /** 订单号前缀 */
    public static final String PREFIX = "SO";

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
