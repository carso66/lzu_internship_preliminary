package com.lzu.xy.controller.model.vo;

import com.lzu.xy.domain.Payment;
import com.lzu.xy.enums.PaymentStatusEnum;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付结果返回结构。
 */
@Data
@Builder
public class PaymentVO {

    /** 支付流水号 */
    private String paymentId;

    private String orderNo;

    /** 支付金额，单位：分 */
    private Long amount;

    /** processing / paid / failed */
    private String status;

    private String method;

    private LocalDateTime paidAt;

    public static PaymentVO from(Payment payment) {
        return PaymentVO.builder()
                .paymentId(payment.getPaymentNo())
                .orderNo(payment.getOrderNo())
                .amount(payment.getAmount())
                .status(PaymentStatusEnum.labelOf(payment.getStatus()))
                .method(payment.getMethod())
                .paidAt(payment.getPaidAt())
                .build();
    }
}
