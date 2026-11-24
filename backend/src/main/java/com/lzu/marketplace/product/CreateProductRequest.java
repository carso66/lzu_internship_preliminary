package com.lzu.marketplace.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 只接收发布时允许填写的字段；主键、状态和创建时间由服务端生成。 */
public record CreateProductRequest(
        @NotBlank(message = "不能为空")
        @Size(max = 120, message = "长度不能超过 120")
        String title,

        @NotNull(message = "不能为空")
        @Min(value = 0, message = "不能为负数，单位为分")
        @Max(value = 100_000_000_000L, message = "不能超过 100000000000（分）")
        Long price,

        @NotNull(message = "不能为空")
        @Min(value = 0, message = "不能为负数")
        Integer stock,

        @NotBlank(message = "不能为空")
        @Size(max = 50, message = "长度不能超过 50")
        String category,

        @Size(max = 2000, message = "长度不能超过 2000")
        String description,

        @NotBlank(message = "不能为空")
        @Size(max = 100, message = "长度不能超过 100")
        String sellerName
) {
    public CreateProductRequest {
        title = title == null ? null : title.strip();
        category = category == null ? null : category.strip();
        description = description == null || description.isBlank() ? null : description.strip();
        sellerName = sellerName == null ? null : sellerName.strip();
    }
}
