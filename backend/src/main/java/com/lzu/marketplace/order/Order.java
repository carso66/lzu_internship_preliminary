package com.lzu.marketplace.order;

import java.time.LocalDateTime;

/**
 * 订单领域对象（字段与 orders 表一一对应）。
 *
 * <p>{@code productTitle} / {@code unitPrice} / {@code totalAmount} 是下单时点的商品快照：
 * 后续商品改名、改价都不影响历史订单。
 *
 * @param id           自增主键
 * @param orderNo      订单号，唯一，格式 SO + yyyyMMdd + 32 位大写十六进制
 * @param productId    商品 id
 * @param productTitle 下单时的商品标题快照
 * @param quantity     购买数量，≥1
 * @param unitPrice    下单时的单价快照，单位「分」
 * @param totalAmount  订单总额 = unitPrice * quantity，单位「分」
 * @param buyerName    买家姓名
 * @param buyerPhone   买家手机号
 * @param address      收货地址
 * @param status       created / paid
 * @param createdAt    创建时间，由数据库生成
 * @param paidAt       支付时间，未支付时为 null
 */
public record Order(
        Long id,
        String orderNo,
        Long productId,
        String productTitle,
        int quantity,
        long unitPrice,
        long totalAmount,
        String buyerName,
        String buyerPhone,
        String address,
        String status,
        LocalDateTime createdAt,
        LocalDateTime paidAt) {

    public static final String STATUS_CREATED = "created";
    public static final String STATUS_PAID = "paid";
}
