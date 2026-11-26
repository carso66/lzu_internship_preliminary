package com.lzu.marketplace.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 支付请求体（官方 §6 L2-9）。
 *
 * <p>{@code method} 用 {@code @NotBlank + @Pattern} 而非枚举：非法值由我们控制的
 * message 说明（「只能是 alipay 或 wechat」），而不是框架生成的枚举反序列化报错。
 *
 * <p>刻意没有金额字段：支付金额只能取订单里服务端算好的 {@code total_amount}，
 * 不接受客户端传值（对应 L1-6 的「金额由服务端计算」）。
 *
 * @param orderNo 订单号，必填
 * @param method  支付方式，只接受 alipay / wechat
 */
public record CreatePaymentRequest(

        @NotBlank(message = "不能为空")
        String orderNo,

        @NotBlank(message = "不能为空")
        @Pattern(regexp = "^(alipay|wechat)$", message = "只能是 alipay 或 wechat")
        String method) {
}
