package com.lzu.marketplace.payment;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 支付流水号生成：{@code PAY + yyyyMMdd + 32 位大写十六进制}。
 *
 * <p>与订单号同源：UUID 不依赖计数状态，唯一性由 {@code uk_payments_payment_id}
 * 兜底。单独成 bean 便于回滚测试注入固定值。
 */
@Component
public class PaymentIdGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PREFIX = "PAY";

    public String next() {
        return PREFIX
                + LocalDate.now().format(DATE)
                + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
