package com.lzu.marketplace.payment;

import jakarta.validation.constraints.*;

public record CreatePaymentRequest(
        @NotBlank(message = "不能为空") @Size(max = 64, message = "最多64字符") String orderNo,
        @NotNull(message = "不能为空") @Pattern(regexp = "alipay|wechat", message = "只能为alipay或wechat") String method) {
}
