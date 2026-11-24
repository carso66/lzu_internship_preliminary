package com.lzu.market.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 支付入参。金额不从这里来 —— 一律以订单上的 total_amount 为准。
 */
public record PayReq(
        @NotBlank(message = "orderNo 不能为空")
        String orderNo,

        @NotBlank(message = "method 不能为空")
        @Pattern(regexp = "alipay|wechat", message = "method 只能是 alipay 或 wechat")
        String method) {
}
