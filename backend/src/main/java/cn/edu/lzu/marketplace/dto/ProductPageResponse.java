package cn.edu.lzu.marketplace.dto;

import java.util.List;

/**
 * 商品列表响应 data：total 为筛选后的总条数，items 为当前页数组。
 */
public record ProductPageResponse(long total, int page, int pageSize, List<ProductResponse> items) {
}
