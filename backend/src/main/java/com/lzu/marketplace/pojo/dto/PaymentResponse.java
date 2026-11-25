package com.lzu.marketplace.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付结果：字段名按题目要求用 paymentId / orderNo / amount / status / paidAt。
 */
@Data
public class PaymentResponse {

    private String paymentId;

    private String orderNo;

    private Integer amount;

    private String status;

    private LocalDateTime paidAt;
}
