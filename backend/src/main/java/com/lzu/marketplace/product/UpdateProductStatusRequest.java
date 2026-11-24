package com.lzu.marketplace.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateProductStatusRequest(
        @NotBlank(message = "不能为空")
        @Pattern(regexp = "on|off", message = "只能为 on 或 off")
        String status
) {
}
