package com.lzu.marketplace.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发布商品的请求体。
 *
 * <p>title/category/sellerName 必填且不能为空白；price、stock 必填且不能为负；
 * description 可省略，为空时由 Service 兜底成空字符串。</p>
 */
public record CreateProductRequest(

        @NotBlank(message = "不能为空")
        @Size(max = 100, message = "长度不能超过 100")
        String title,

        @NotNull(message = "不能为空")
        @Min(value = 0, message = "不能为负数")
        Long price,

        @NotNull(message = "不能为空")
        @Min(value = 0, message = "不能为负数")
        Integer stock,

        @NotBlank(message = "不能为空")
        @Size(max = 50, message = "长度不能超过 50")
        String category,

        @Size(max = 500, message = "长度不能超过 500")
        String description,

        @NotBlank(message = "不能为空")
        @Size(max = 50, message = "长度不能超过 50")
        String sellerName
) {
}
