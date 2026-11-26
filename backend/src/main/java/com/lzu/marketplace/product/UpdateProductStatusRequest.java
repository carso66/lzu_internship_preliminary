package com.lzu.marketplace.product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateProductStatusRequest(
        @NotNull(message = "不能为空") @Pattern(regexp = "on|off", message = "只能为on或off") String status) {
}
