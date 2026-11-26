package com.lzu.marketplace.pojo.dto;

import com.lzu.marketplace.pojo.Order;
import lombok.Data;

import java.util.List;

/**
 * 订单列表响应：与商品列表保持一致的 total / page / pageSize / items 结构。
 */
@Data
public class OrderListResponse {

    private long total;
    private int page;
    private int pageSize;
    private List<Order> items;

    public OrderListResponse() {
    }

    public OrderListResponse(long total, int page, int pageSize, List<Order> items) {
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.items = items;
    }
}
