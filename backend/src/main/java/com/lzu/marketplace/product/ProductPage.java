package com.lzu.marketplace.product;

import java.util.List;

public record ProductPage(long total, int page, int pageSize, List<Product> items) {
}
