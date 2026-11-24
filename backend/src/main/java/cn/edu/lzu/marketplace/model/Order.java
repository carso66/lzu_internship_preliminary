package cn.edu.lzu.marketplace.model;

import java.time.LocalDateTime;

/**
 * 订单领域模型，对应 orders 表。
 *
 * <p>金额字段 unitPrice / totalAmount 使用 Long，以「分」为单位的整数存储，
 * 全程不使用浮点数。orderNo 由服务端根据自增主键生成（见 OrderNoGenerator）。
 */
public record Order(
        Long id,
        String orderNo,
        Long productId,
        String productTitle,
        Integer quantity,
        Long unitPrice,
        Long totalAmount,
        String buyerName,
        String buyerPhone,
        String address,
        String status,
        LocalDateTime createdAt,
        LocalDateTime paidAt) {

    /** 订单状态：已创建（下单成功即进入该状态） */
    public static final String STATUS_CREATED = "created";
    /** 订单状态：已支付（L2 支付链路写入） */
    public static final String STATUS_PAID = "paid";

    /** 插入后回填自增主键 */
    public Order withId(Long newId) {
        return new Order(newId, orderNo, productId, productTitle, quantity, unitPrice,
                totalAmount, buyerName, buyerPhone, address, status, createdAt, paidAt);
    }

    /** 回填正式订单号 */
    public Order withOrderNo(String newOrderNo) {
        return new Order(id, newOrderNo, productId, productTitle, quantity, unitPrice,
                totalAmount, buyerName, buyerPhone, address, status, createdAt, paidAt);
    }
}
