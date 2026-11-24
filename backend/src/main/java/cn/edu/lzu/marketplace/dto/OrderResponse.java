package cn.edu.lzu.marketplace.dto;

import cn.edu.lzu.marketplace.model.Order;

import java.time.LocalDateTime;

/**
 * 订单响应体：下单、详情、列表 items 中的单个元素。
 *
 * <p>unitPrice / totalAmount 以「分」为单位的整数；字段覆盖 README L1-6 示例，
 * 额外附带 buyerName（题目未禁止，便于展示）。paidAt 在 L1 阶段恒为 null，暂不暴露。
 */
public record OrderResponse(
        Long id,
        String orderNo,
        Long productId,
        String productTitle,
        Integer quantity,
        Long unitPrice,
        Long totalAmount,
        String status,
        String buyerName,
        String buyerPhone,
        String address,
        LocalDateTime createdAt) {

    public static OrderResponse from(Order o) {
        return new OrderResponse(o.id(), o.orderNo(), o.productId(), o.productTitle(),
                o.quantity(), o.unitPrice(), o.totalAmount(), o.status(),
                o.buyerName(), o.buyerPhone(), o.address(), o.createdAt());
    }
}
