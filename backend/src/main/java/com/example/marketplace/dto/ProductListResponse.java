package com.example.marketplace.dto;

import java.util.List;

public record ProductListResponse(
        long total,
        int page,
        int pageSize,
        List<ProductResponse> items
) {
}
