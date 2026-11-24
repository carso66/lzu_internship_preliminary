package com.lzu.marketplace.product;

import java.time.OffsetDateTime;

/** 数据库中的商品，也是商品详情和列表使用的返回结构。金额单位为分。 */
public record Product(
        Long id,
        String title,
        Long price,
        Integer stock,
        String category,
        String description,
        String status,
        String sellerName,
        OffsetDateTime createdAt
) {
}
