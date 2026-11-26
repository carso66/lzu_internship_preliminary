package com.lzu.marketplace.product.dto;

import com.lzu.marketplace.product.Product;

import java.time.LocalDateTime;

/**
 * 发布商品的响应体（官方 §4 L0-2）。
 *
 * <p>官方示例 {@code {"id":13,"title":"测试商品","price":1990,"stock":10,"status":"on",...}}
 * 中的 {@code ...} 表示其余商品字段；这里返回<b>完整商品对象</b>，使客户端拿到刚创建的
 * 商品无需再查一次。
 *
 * <p>字段为 camelCase（接口契约），数据库列是 snake_case，转换在 {@link #from(Product)} 里做，
 * 不在 SQL 里用别名硬凑。
 */
public record ProductResponse(
        Long id,
        String title,
        long price,
        int stock,
        String category,
        String description,
        String status,
        String sellerName,
        LocalDateTime createdAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.id(),
                p.title(),
                p.price(),
                p.stock(),
                p.category(),
                p.description(),
                p.status(),
                p.sellerName(),
                p.createdAt());
    }
}
