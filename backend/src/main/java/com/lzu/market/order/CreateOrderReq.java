package com.lzu.market.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 下单入参。价格一律由服务端按商品现价计算，客户端不传也不被采信。
 */
public record CreateOrderReq(
        @NotNull(message = "productId 不能为空")
        @Min(value = 1, message = "productId 必须 ≥ 1")
        Long productId,

        @NotNull(message = "quantity 不能为空")
        @Min(value = 1, message = "quantity 必须 ≥ 1")
        @Max(value = 999, message = "quantity 不能超过 999")
        Integer quantity,

        @NotBlank(message = "buyerName 不能为空")
        @Size(max = 50, message = "buyerName 长度不能超过 50")
        String buyerName,

        @NotBlank(message = "buyerPhone 不能为空")
        @Pattern(regexp = "^1\\d{10}$", message = "buyerPhone 必须是 1 开头的 11 位手机号")
        String buyerPhone,

        @NotBlank(message = "address 不能为空")
        @Size(max = 255, message = "address 长度不能超过 255")
        String address) {
}
