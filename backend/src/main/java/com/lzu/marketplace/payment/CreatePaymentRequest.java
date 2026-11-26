package com.lzu.marketplace.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 只接收支付时允许填写的字段；金额、状态和支付时间由服务端生成。 */
public record CreatePaymentRequest(
        @NotBlank(message = "不能为空")
        @Size(max = 64, message = "长度不能超过 64")
        String orderNo,

        @NotBlank(message = "不能为空")
        @Pattern(regexp = "alipay|wechat", message = "必须为 alipay 或 wechat")
        String method
) {
    public CreatePaymentRequest {
        orderNo = orderNo == null ? null : orderNo.strip();
        method = method == null ? null : method.strip();
    }
}
