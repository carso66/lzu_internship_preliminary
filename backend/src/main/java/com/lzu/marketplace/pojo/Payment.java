package com.lzu.marketplace.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付单实体，字段与 payments 表一一对应。
 */
@Data
public class Payment {

    private Long id;

    private String paymentNo;

    private String orderNo;

    private Integer amount;

    private String method;

    private String status;

    /** 支付完成时间，与订单的 paid_at 用同一个时刻写入。 */
    private LocalDateTime paidAt;

    private LocalDateTime createdAt;
}
