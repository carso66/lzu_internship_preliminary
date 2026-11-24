package com.lzu.marketplace.order;

/** 插入订单所需的服务端快照字段；状态、创建时间和支付时间交给数据库默认值。 */
record OrderDraft(
        String orderNo,
        long productId,
        String productTitle,
        int quantity,
        long unitPrice,
        long totalAmount,
        String buyerName,
        String buyerPhone,
        String address
) {
}
