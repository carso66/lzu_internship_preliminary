package com.example.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreatePaymentRequest(
        @NotBlank(message = "orderNo must not be blank")
        String orderNo,

        @NotBlank(message = "method must be alipay or wechat")
        @Pattern(regexp = "alipay|wechat", message = "method must be alipay or wechat")
        String method
) {
}
