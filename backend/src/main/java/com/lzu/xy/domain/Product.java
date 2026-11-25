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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_category", columnList = "category"),
        @Index(name = "idx_products_status_deleted", columnList = "status, is_deleted")
})
public class Product {

    /** 商品ID（雪花ID，由应用生成） */
    @Id
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    /** 单价，单位：分 */
    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 状态：1-上架 on，0-下架 off */
    @Column(nullable = false)
    private Integer status;

    @Column(name = "seller_name", nullable = false, length = 64)
    private String sellerName;

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
