package cn.edu.lzu.marketplace.dto;

import cn.edu.lzu.marketplace.model.Product;

import java.time.LocalDateTime;

/**
 * 商品响应体：创建、详情、列表 items 中的单个元素。
 */
public record ProductResponse(
        Long id,
        String title,
        Long price,
        Integer stock,
        String category,
        String description,
        String status,
        String sellerName,
        LocalDateTime createdAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.id(), p.title(), p.price(), p.stock(),
                p.category(), p.description(), p.status(), p.sellerName(), p.createdAt());
    }
}
