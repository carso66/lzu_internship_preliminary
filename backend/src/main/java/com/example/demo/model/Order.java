package com.example.demo.model;

import java.time.LocalDateTime;

public record Order(
        long id,
        String orderNo,
        long productId,
        String productTitle,
        int quantity,
        long unitPrice,
        long totalAmount,
        String buyerName,
        String buyerPhone,
        String address,
        String remark,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime paidAt
) {
}