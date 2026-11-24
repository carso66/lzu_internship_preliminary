package com.lzu.market.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateStatusReq(
        @NotBlank(message = "status 不能为空")
        @Pattern(regexp = "on|off", message = "status 只能是 on 或 off")
        String status) {
}
