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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "uk_orders_order_no", columnList = "order_no", unique = true),
        @Index(name = "idx_orders_product_id", columnList = "product_id"),
        @Index(name = "idx_orders_status_deleted", columnList = "status, is_deleted")
})
public class Order {

    /** 订单ID（雪花ID，由应用生成） */
    @Id
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_title", nullable = false, length = 200)
    private String productTitle;

    @Column(nullable = false)
    private Integer quantity;

    /** 下单时的商品单价，单位：分 */
    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice;

    /** 订单总金额，单位：分 */
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "buyer_name", nullable = false, length = 64)
    private String buyerName;

    @Column(name = "buyer_phone", nullable = false, length = 20)
    private String buyerPhone;

    @Column(nullable = false, length = 255)
    private String address;

    /** 订单状态：0-待支付 created，1-已支付 paid，2-已取消 cancelled */
    @Column(nullable = false)
    private Integer status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

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
