package com.example.demo.product;

import java.util.List;

/**
 * GET /api/products 的 data：当前页数据 + 筛选后的总条数。
 */
public record ProductPage(long total, int page, int pageSize, List<Product> items) {
}
