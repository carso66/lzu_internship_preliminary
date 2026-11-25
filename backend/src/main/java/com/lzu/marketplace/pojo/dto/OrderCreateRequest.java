package com.lzu.marketplace.pojo.dto;

import lombok.Data;

/**
 * 下单请求。注意：单价与总价不接受客户端传入，一律由服务端按数据库价格计算。
 */
@Data
public class OrderCreateRequest {

    private Long productId;
    private Integer quantity;
    private String buyerName;
    private String buyerPhone;
    private String address;
}
