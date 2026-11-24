package cn.edu.lzu.marketplace.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * PATCH /api/products/{id}/status 请求体，status 只能是 on / off。
 */
public record ProductStatusRequest(
        @NotNull(message = "状态不能为空")
        @Pattern(regexp = "on|off", message = "状态只能是 on 或 off")
        String status) {
}
