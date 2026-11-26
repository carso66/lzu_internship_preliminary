package com.lzu.marketplace.product;

import java.time.LocalDateTime;

/**
 * 商品领域对象（字段与 products 表一一对应）。
 *
 * <p>与输入 DTO {@link com.lzu.marketplace.product.dto.CreateProductRequest}、
 * 输出 DTO {@link com.lzu.marketplace.product.dto.ProductResponse} 分开：
 * 领域对象跟着数据库走，输出对象跟着接口契约走，改任一侧不会连带另一侧。
 *
 * @param id          自增主键
 * @param title       标题
 * @param price       单价，单位「分」
 * @param stock       库存
 * @param category    分类
 * @param description 描述，可为 null
 * @param status      上下架状态，on / off
 * @param sellerName  卖家名称
 * @param createdAt   创建时间，由数据库生成
 */
public record Product(
        Long id,
        String title,
        long price,
        int stock,
        String category,
        String description,
        String status,
        String sellerName,
        LocalDateTime createdAt) {

    public static final String STATUS_ON = "on";
    public static final String STATUS_OFF = "off";
}
