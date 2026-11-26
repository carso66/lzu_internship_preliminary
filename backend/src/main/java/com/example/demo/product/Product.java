package com.example.demo.product;

import java.time.LocalDateTime;

/**
 * 商品。price 单位为「分」，整数。
 */
public record Product(
		long id,
		String title,
		int price,
		int stock,
		String category,
		String description,
		String status,
		String sellerName,
		LocalDateTime createdAt) {
}
