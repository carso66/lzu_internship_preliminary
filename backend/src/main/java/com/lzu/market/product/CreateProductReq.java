package com.lzu.market.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProductReq(
        @NotBlank(message = "title 不能为空")
        @Size(max = 100, message = "title 长度不能超过 100")
        String title,

        @NotNull(message = "price 不能为空")
        @Min(value = 0, message = "price 不能小于 0")
        @Max(value = 1000000, message = "price 不能超过 1000000 分")
        Integer price,

        @NotNull(message = "stock 不能为空")
        @Min(value = 0, message = "stock 不能小于 0")
        @Max(value = 1000000, message = "stock 不能超过 1000000")
        Integer stock,

        @NotBlank(message = "category 不能为空")
        @Size(max = 50, message = "category 长度不能超过 50")
        String category,

        @Size(max = 1000, message = "description 长度不能超过 1000")
        String description,

        @NotBlank(message = "sellerName 不能为空")
        @Size(max = 50, message = "sellerName 长度不能超过 50")
        String sellerName) {
}
