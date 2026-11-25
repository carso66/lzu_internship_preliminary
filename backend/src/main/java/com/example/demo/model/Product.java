package com.example.demo.model;

import java.time.LocalDateTime;

public record Product(
        long id,
        String title,
        long price,
        int stock,
        String category,
        String description,
        String imageUrl,
        String status,
        String sellerName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}