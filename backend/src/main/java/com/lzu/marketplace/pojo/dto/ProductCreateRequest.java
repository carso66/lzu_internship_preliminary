package com.lzu.marketplace.pojo.dto;

import lombok.Data;

/**
 * 发布商品请求体。
 * price / stock 用 Integer 接收：传小数、字符串等非法类型时 Jackson 直接解析失败，
 * 由 GlobalExceptionHandler 转成 400 + 40001。
 */
@Data
public class ProductCreateRequest {

    private String title;
    private Integer price;
    private Integer stock;
    private String category;
    private String description;
    private String sellerName;
}
