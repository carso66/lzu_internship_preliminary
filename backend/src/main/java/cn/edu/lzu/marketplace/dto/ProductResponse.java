package cn.edu.lzu.marketplace.dto;

import cn.edu.lzu.marketplace.model.Product;

import java.time.LocalDateTime;

/**
 * 商品响应 DTO，供发布结果、详情和列表明细共用，价格单位为分。
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

    /** 将商品领域对象映射为统一的对外展示字段。 */
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.id(), p.title(), p.price(), p.stock(),
                p.category(), p.description(), p.status(), p.sellerName(), p.createdAt());
    }
}
