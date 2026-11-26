package com.lzu.marketplace.order;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 订单号生成：{@code SO + yyyyMMdd + 32 位大写十六进制}，共 42 字符（列宽 VARCHAR(64)）。
 *
 * <p>为什么不用「日期 + COUNT(*)+1」：序号在多实例并发下会撞车，而且需要额外查询。
 * UUID 不依赖任何计数状态，唯一性由数据库 {@code uk_orders_order_no} 约束兜底
 * （题目 L1-6 第 6 条：order_no 要唯一）。
 *
 * <p>单独成 bean 而不是在 Service 里内联：测试需要把「下单失败」的故障点精确放在
 * 插入订单这一步（固定单号触发唯一约束冲突），见 OrderRollbackTest。
 */
@Component
public class OrderNoGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PREFIX = "SO";

    public String next() {
        return PREFIX
                + LocalDate.now().format(DATE)
                + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
