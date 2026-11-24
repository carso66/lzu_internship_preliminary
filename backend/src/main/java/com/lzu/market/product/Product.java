package com.lzu.market.product;

import java.time.LocalDateTime;

public record Product(
        long id,
        String title,
        int price,
        int stock,
        String category,
        String description,
        String status,
        String sellerName,
        LocalDateTime createdAt) {
}
