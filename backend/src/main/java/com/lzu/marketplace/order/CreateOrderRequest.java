package com.lzu.marketplace.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 只接收下单时允许填写的字段；价格、总额、状态和订单号由服务端生成。 */
public record CreateOrderRequest(
        @NotNull(message = "不能为空")
        @Positive(message = "必须为正整数")
        Long productId,

        @NotNull(message = "不能为空")
        @Min(value = 1, message = "必须大于等于 1")
        Integer quantity,

        @NotBlank(message = "不能为空")
        @Size(max = 100, message = "长度不能超过 100")
        String buyerName,

        @NotBlank(message = "不能为空")
        @Size(max = 20, message = "长度不能超过 20")
        String buyerPhone,

        @NotBlank(message = "不能为空")
        @Size(max = 500, message = "长度不能超过 500")
        String address
) {
    public CreateOrderRequest {
        buyerName = buyerName == null ? null : buyerName.strip();
        buyerPhone = buyerPhone == null ? null : buyerPhone.strip();
        address = address == null ? null : address.strip();
    }
}
