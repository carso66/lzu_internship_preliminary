package com.example.marketplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotNull(message = "productId is required")
        @Positive(message = "productId must be positive")
        Long productId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        @NotBlank(message = "buyerName must not be blank")
        @Size(max = 80, message = "buyerName must be at most 80 characters")
        String buyerName,

        @NotBlank(message = "buyerPhone must not be blank")
        @Size(max = 32, message = "buyerPhone must be at most 32 characters")
        String buyerPhone,

        @NotBlank(message = "address must not be blank")
        @Size(max = 255, message = "address must be at most 255 characters")
        String address
) {
}
