package com.lzu.marketplace.payment;

import java.time.OffsetDateTime;

/**
 * 数据库中的支付记录，也是支付接口返回结构。
 * paymentId 对客户端暴露为字符串形式的唯一支付编号（PAY 前缀），金额为分。
 */
public record Payment(
        String paymentId,
        String orderNo,
        Long amount,
        String method,
        String status,
        OffsetDateTime paidAt
) {
}
