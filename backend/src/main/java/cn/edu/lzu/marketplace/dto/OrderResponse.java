package cn.edu.lzu.marketplace.dto;

import cn.edu.lzu.marketplace.model.Order;

import java.time.LocalDateTime;

/**
 * 订单响应 DTO，供下单结果、订单详情及列表明细共用；返回订单信息。
 *
 * <p>unitPrice 和 totalAmount 均为订单保存的金额快照，以整数分表示。
 * paidAt 是支付预留字段，当前接口不返回该字段。
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

    /** 从订单领域对象提取接口公开字段，复用下单时保存的商品名称和价格。 */
    public static OrderResponse from(Order o) {
        return new OrderResponse(o.id(), o.orderNo(), o.productId(), o.productTitle(),
                o.quantity(), o.unitPrice(), o.totalAmount(), o.status(),
                o.buyerName(), o.buyerPhone(), o.address(), o.createdAt());
    }
}
