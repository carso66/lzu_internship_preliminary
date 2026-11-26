package com.lzu.marketplace.product;

import java.time.LocalDateTime;

/** 商品查询结果，也用作接口返回数据；price 使用 long 保存整数分，避免浮点误差。 */
public record Product(long id, String title, long price, int stock, String category,
                      String description, String status, String sellerName, LocalDateTime createdAt) {
}
