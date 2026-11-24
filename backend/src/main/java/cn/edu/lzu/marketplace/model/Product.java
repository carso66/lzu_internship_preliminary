package cn.edu.lzu.marketplace.model;

import java.time.LocalDateTime;

/**
 * 商品领域模型，对应 products 表。
 *
 * <p>金额字段 price 使用 Long，以「分」为单位的整数存储，全程不使用浮点数。
 */
public record Product(
        Long id,
        String title,
        Long price,
        Integer stock,
        String category,
        String description,
        String status,
        String sellerName,
        LocalDateTime createdAt) {

    /** 商品状态：上架 */
    public static final String STATUS_ON = "on";
    /** 商品状态：下架 */
    public static final String STATUS_OFF = "off";

    /** 插入后回填自增主键 */
    public Product withId(Long newId) {
        return new Product(newId, title, price, stock, category, description,
                status, sellerName, createdAt);
    }
}
