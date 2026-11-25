package com.lzu.marketplace.order.model;

import java.time.LocalDateTime;

/**
 * 订单数据对象，对应 orders 表的一行，同时作为订单相关接口的响应体。
 *
 * <p>金额字段 unitPrice / totalAmount 单位都是"分"、用 Long 存；quantity 用 Integer。
 * productTitle、unitPrice、totalAmount 都是下单当时的<b>快照</b>，
 * 之后商品改名或改价都不影响已有订单的展示。</p>
 *
 * <p>paidAt 未支付时为 null（L1 阶段恒为 null，L2 支付完成后才有值）。</p>
 */
public record Order(
        Long id,
        String orderNo,
        Long productId,
        String productTitle,
        Integer quantity,
        Long unitPrice,
        Long totalAmount,
        String buyerName,
        String buyerPhone,
        String address,
        String status,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {
}
