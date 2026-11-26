package com.lzu.marketplace.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品实体，字段与 schema.sql 中 products 表一一对应。
 */
@Data
public class Product {

    private Long id;
    private String title;
    private Integer price;
    private Integer stock;
    private String category;
    private String description;
    private String status;
    private String sellerName;
    private LocalDateTime createdAt;
}
