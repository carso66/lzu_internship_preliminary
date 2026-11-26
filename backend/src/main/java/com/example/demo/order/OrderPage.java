package com.example.demo.order;

import java.util.List;

/**
 * GET /api/orders 的 data：当前页订单 + 该手机号下的订单总数。
 */
public record OrderPage(long total, int page, int pageSize, List<Order> items) {
}
