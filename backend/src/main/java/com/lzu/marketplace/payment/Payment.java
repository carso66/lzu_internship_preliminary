package com.lzu.marketplace.payment;

import java.time.LocalDateTime;

/**
 * 支付记录领域对象（字段与 payments 表一一对应）。
 *
 * <p>{@code amount} 是支付金额，等于订单的 {@code total_amount}（单位「分」）。
 * 本项目同步返回 paid，无中间态，故 {@code status} 恒为 paid。
 *
 * @param id        自增主键
 * @param paymentId 支付流水号，唯一，格式 PAY + 日期 + 32 位大写十六进制
 * @param orderNo   关联订单号，唯一（数据库幂等兜底）
 * @param amount    支付金额，单位「分」
 * @param method    支付方式，alipay / wechat
 * @param status    paid
 * @param paidAt    支付时间
 * @param createdAt 记录创建时间
 */
public record Payment(
        Long id,
        String paymentId,
        String orderNo,
        long amount,
        String method,
        String status,
        LocalDateTime paidAt,
        LocalDateTime createdAt) {

    public static final String STATUS_PAID = "paid";
}
