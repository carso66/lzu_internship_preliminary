package cn.edu.lzu.marketplace.model;

import java.time.LocalDateTime;

/**
 * 订单领域模型，由订单服务组装并由数据访问层读写 orders 表；定义订单领域对象。
 *
 * <p>金额字段 unitPrice / totalAmount 使用 Long，以「分」为单位的整数存储，
 * 全程不使用浮点数。商品标题与单价取自下单时的快照，订单号由服务端根据自增主键生成。
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
    /** 支付流程预留状态，当前下单流程只写入 created。 */
    public static final String STATUS_PAID = "paid";

    /** 返回带自增主键的副本，用于组装下单响应。 */
    public Order withId(Long newId) {
        return new Order(newId, orderNo, productId, productTitle, quantity, unitPrice,
                totalAmount, buyerName, buyerPhone, address, status, createdAt, paidAt);
    }

    /** 返回带正式订单号的副本，业务层先完成数据库回填。 */
    public Order withOrderNo(String newOrderNo) {
        return new Order(id, newOrderNo, productId, productTitle, quantity, unitPrice,
                totalAmount, buyerName, buyerPhone, address, status, createdAt, paidAt);
    }
}
