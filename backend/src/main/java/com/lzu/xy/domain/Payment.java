package com.lzu.xy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 支付单。一次成功支付对应一条记录；同一订单最多一条 paid 记录（order_id 唯一索引兜底）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "uk_payments_payment_no", columnList = "payment_no", unique = true),
        @Index(name = "uk_payments_order_id", columnList = "order_id", unique = true),
        @Index(name = "idx_payments_order_no", columnList = "order_no")
})
public class Payment {

    /** 支付单ID（雪花ID，由应用生成） */
    @Id
    @Column(nullable = false, updatable = false)
    private Long id;

    /** 支付流水号，形如 PAYxxx，全局唯一 */
    @Column(name = "payment_no", nullable = false, length = 32)
    private String paymentNo;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    /** 支付金额，单位：分，取订单总金额 */
    @Column(nullable = false)
    private Long amount;

    /** 支付方式：alipay / wechat */
    @Column(nullable = false, length = 16)
    private String method;

    /** 支付状态：0-processing，1-paid，2-failed */
    @Column(nullable = false)
    private Integer status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    /** 是否删除：0-否，1-是 */
    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;
}
