package com.lzu.marketplace.pojo.dto;

import com.lzu.marketplace.pojo.Product;
import lombok.Data;

import java.util.List;

/**
 * 商品列表分页结果：{ total, page, pageSize, items }。
 */
@Data
public class ProductListResponse {

    private long total;
    private int page;
    private int pageSize;
    private List<Product> items;

    public ProductListResponse() {
    }

    public ProductListResponse(long total, int page, int pageSize, List<Product> items) {
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.items = items;
    }
}
