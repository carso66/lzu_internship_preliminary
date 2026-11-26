package com.lzu.marketplace.order;

import java.time.LocalDateTime;

/** 标题、单价和总额保存成交快照，不随商品后续变化而改变。 */
public record Order(long id, String orderNo, long productId, String productTitle, int quantity,
                    long unitPrice, long totalAmount, String buyerName, String buyerPhone,
                    String address, String status, LocalDateTime createdAt, LocalDateTime paidAt) {
}
