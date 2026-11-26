package com.example.demo.order;

import java.time.LocalDateTime;

/**
 * 订单。金额单位统一为「分」，整数。
 *
 * productTitle / unitPrice 是下单那一刻从商品表抄下来的快照：
 * 商品日后改名或改价，历史订单的金额也不会跟着变。
 */
public record Order(
		long id,
		String orderNo,
		long productId,
		String productTitle,
		int quantity,
		int unitPrice,
		int totalAmount,
		String buyerName,
		String buyerPhone,
		String address,
		String status,
		LocalDateTime createdAt,
		LocalDateTime paidAt) {
}
