package com.lzu.marketplace.product;

import java.util.List;

/** 商品列表分页结果：total 是筛选后的总条数，items 是当前页的商品。 */
public record ProductPage(long total, int page, int pageSize, List<Product> items) {
}
