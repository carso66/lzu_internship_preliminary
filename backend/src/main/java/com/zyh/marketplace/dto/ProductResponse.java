package com.zyh.marketplace.dto;

/**
 * 商品响应 DTO（camelCase），created_at 输出为 ISO-8601 字符串。
 * 金额 price 为整数「分」。
 */
public record ProductResponse(
        Long id,
        String title,
        Long price,
        Integer stock,
        String category,
        String description,
        String status,
        String sellerName,
        String createdAt
) {
}
