package com.lzu.marketplace.pojo.dto;

import lombok.Data;

/**
 * 发起支付的请求体：只接收订单号和支付方式，金额由服务端按订单计算。
 */
@Data
public class PaymentCreateRequest {

    private String orderNo;

    private String method;
}
