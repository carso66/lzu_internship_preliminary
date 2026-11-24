package com.example.marketplace.dto;

import com.example.marketplace.domain.Product;

import java.time.OffsetDateTime;

public record ProductResponse(
        Long id,
        String title,
        Long price,
        Integer stock,
        String category,
        String description,
        String sellerName,
        String status,
        OffsetDateTime createdAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getTitle(),
                product.getPrice(),
                product.getStock(),
                product.getCategory(),
                product.getDescription(),
                product.getSellerName(),
                product.getStatus(),
                product.getCreatedAt()
        );
    }
}