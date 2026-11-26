package com.lzu.marketplace.order;

import java.util.List;

public record OrderPage(long total, int page, int pageSize, List<Order> items) {
}
