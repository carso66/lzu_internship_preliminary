package com.lzu.marketplace.order;

import jakarta.validation.constraints.*;

public record CreateOrderRequest(
        @NotNull(message = "不能为空") @Positive(message = "必须为正整数") Long productId,
        @NotNull(message = "不能为空") @Min(value = 1, message = "必须至少为1") Integer quantity,
        @NotBlank(message = "不能为空") @Size(max = 60, message = "最多60字符") String buyerName,
        @NotNull(message = "不能为空") @Pattern(regexp = "[0-9]{11}", message = "必须为11位数字") String buyerPhone,
        @NotBlank(message = "不能为空") @Size(max = 500, message = "最多500字符") String address) {
}
