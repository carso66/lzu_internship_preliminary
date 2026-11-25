package com.lzu.xy.controller.model.vo;

import com.lzu.xy.domain.Order;
import com.lzu.xy.enums.OrderStatusEnum;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单详情 / 列表项返回结构。
 */
@Data
@Builder
public class OrderVO {

    private Long id;

    private String orderNo;

    private Long productId;

    /** 下单时的商品标题快照 */
    private String productTitle;

    private Integer quantity;

    /** 下单时的单价，单位：分 */
    private Integer unitPrice;

    /** 订单总金额，单位：分 */
    private Long totalAmount;

    /** created / paid / cancelled */
    private String status;

    private String buyerName;

    private String buyerPhone;

    private String address;

    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    public static OrderVO from(Order order) {
        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .productId(order.getProductId())
                .productTitle(order.getProductTitle())
                .quantity(order.getQuantity())
                .unitPrice(order.getUnitPrice())
                .totalAmount(order.getTotalAmount())
                .status(OrderStatusEnum.labelOf(order.getStatus()))
                .buyerName(order.getBuyerName())
                .buyerPhone(order.getBuyerPhone())
                .address(order.getAddress())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .build();
    }
}
