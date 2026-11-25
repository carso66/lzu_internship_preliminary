package com.example.marketplace.dto;

import java.util.List;

public record OrderListResponse(
        long total,
        int page,
        int pageSize,
        List<OrderResponse> items
) {
}
