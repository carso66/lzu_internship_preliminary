package com.lzu.marketplace.pojo.dto;

import lombok.Data;

/**
 * 上下架请求体：{ "status": "on" | "off" }
 */
@Data
public class ProductStatusRequest {

    private String status;
}
