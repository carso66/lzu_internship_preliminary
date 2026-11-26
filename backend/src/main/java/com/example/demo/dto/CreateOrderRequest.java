package com.example.demo.dto;

public record CreateOrderRequest(
        Long productId,
        Integer quantity,
        String buyerName,
        String buyerPhone,
        String address,
        String remark
) {
}