package com.lzu.marketplace.payment;

import java.time.OffsetDateTime;

/** 插入支付记录所需的服务端字段；支付时间与订单更新使用同一时间。 */
record PaymentDraft(
        String paymentNo,
        String orderNo,
        long amount,
        String method,
        OffsetDateTime paidAt
) {
}
