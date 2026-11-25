package com.lzu.xy.controller.model.vo;

import com.lzu.xy.domain.Product;
import com.lzu.xy.enums.ProductStatusEnum;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品详情 / 列表项返回结构。
 */
@Data
@Builder
public class ProductVO {

    private Long id;

    private String title;

    /** 单价，单位：分 */
    private Integer price;

    private Integer stock;

    private String category;

    private String description;

    /** on / off */
    private String status;

    private String sellerName;

    private LocalDateTime createdAt;

    public static ProductVO from(Product product) {
        return ProductVO.builder()
                .id(product.getId())
                .title(product.getTitle())
                .price(product.getPrice())
                .stock(product.getStock())
                .category(product.getCategory())
                .description(product.getDescription())
                .status(ProductStatusEnum.labelOf(product.getStatus()))
                .sellerName(product.getSellerName())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
