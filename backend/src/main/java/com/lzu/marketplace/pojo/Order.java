package com.lzu.marketplace.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单实体，字段与 orders 表一一对应。
 */
@Data
public class Order {

    private Long id;
    private String orderNo;
    private Long productId;
    private String productTitle;
    private Integer quantity;
    private Integer unitPrice;
    private Integer totalAmount;
    private String buyerName;
    private String buyerPhone;
    private String address;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
