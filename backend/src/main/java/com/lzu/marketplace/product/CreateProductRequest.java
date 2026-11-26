package com.lzu.marketplace.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 发布商品的输入字段。Long/Integer 可以用 null 区分缺字段和显式传入 0。
 * NotBlank 检查空白文本，Size 限制长度；description 允许缺省，入库时统一转为空字符串。
 */
public record CreateProductRequest(
        @NotBlank(message = "不能为空") @Size(max = 120, message = "不能超过120个字符") String title,
        @NotNull(message = "不能为空") @Positive(message = "必须为正整数，单位是分") Long price,
        @NotNull(message = "不能为空") @Min(value = 0, message = "不能为负数") Integer stock,
        @NotBlank(message = "不能为空") @Size(max = 60, message = "不能超过60个字符") String category,
        @Size(max = 2000, message = "不能超过2000个字符") String description,
        @NotBlank(message = "不能为空") @Size(max = 60, message = "不能超过60个字符") String sellerName
) {
}
