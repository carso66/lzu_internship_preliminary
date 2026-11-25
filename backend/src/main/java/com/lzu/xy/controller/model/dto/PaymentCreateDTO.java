package com.lzu.xy.controller.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发起支付入参。
 */
@Data
public class PaymentCreateDTO {

    @NotBlank(message = "不能为空")
    private String orderNo;

    /** 支付方式，只接受 alipay / wechat */
    @NotBlank(message = "不能为空")
    @Pattern(regexp = "^(alipay|wechat)$", message = "只能是 alipay 或 wechat")
    private String method;
}
