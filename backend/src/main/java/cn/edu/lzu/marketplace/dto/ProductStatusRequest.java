package cn.edu.lzu.marketplace.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 商品上下架请求 DTO。控制层通过 Bean Validation 校验状态必填且仅允许 on 或 off。
 */
public record ProductStatusRequest(
        @NotNull(message = "状态不能为空")
        @Pattern(regexp = "on|off", message = "状态只能是 on 或 off")
        String status) {
}
