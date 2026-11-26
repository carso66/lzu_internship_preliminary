package com.example.marketplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 发布商品请求；price 字段使用整数分。
 */
public record CreateProductRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 120, message = "title must be at most 120 characters")
        String title,

        @NotNull(message = "price is required")
        @Positive(message = "price must be greater than 0")
        Long price,

        @NotNull(message = "stock is required")
        @Min(value = 0, message = "stock must be greater than or equal to 0")
        Integer stock,

        @NotBlank(message = "category must not be blank")
        @Size(max = 64, message = "category must be at most 64 characters")
        String category,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @NotBlank(message = "sellerName must not be blank")
        @Size(max = 80, message = "sellerName must be at most 80 characters")
        String sellerName
) {
}