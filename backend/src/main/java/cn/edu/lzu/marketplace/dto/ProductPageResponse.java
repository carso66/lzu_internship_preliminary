package cn.edu.lzu.marketplace.dto;

import java.util.List;

/**
 * 商品列表响应 DTO。业务层封装筛选后的总条数、当前页码、页大小和商品明细；返回分页商品列表。
 */
public record ProductPageResponse(long total, int page, int pageSize, List<ProductResponse> items) {
}
