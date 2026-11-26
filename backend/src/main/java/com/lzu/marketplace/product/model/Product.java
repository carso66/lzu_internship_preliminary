package com.lzu.marketplace.product.model;

import java.time.LocalDateTime;

/**
 * 商品数据对象，对应 products 表的一行。
 *
 * <p>金额 price 以"分"为单位、用 Long 存；stock 用 Integer。</p>
 */
public record Product(
        Long id,
        String title,
        Long price,
        Integer stock,
        String category,
        String description,
        String status,
        String sellerName,
        LocalDateTime createdAt
) {
}
