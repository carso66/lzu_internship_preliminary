package com.lzu.marketplace.mapper;

import com.lzu.marketplace.pojo.Order;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 Mapper（注解式 SQL，列名与 orders 表一致）。
 */
@Mapper
public interface OrderMapper {

    String COLUMNS = "id, order_no, product_id, product_title, quantity, unit_price, total_amount, "
            + "buyer_name, buyer_phone, address, status, created_at, paid_at";

    @Insert("INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, total_amount, "
            + "buyer_name, buyer_phone, address, status) "
            + "VALUES (#{orderNo}, #{productId}, #{productTitle}, #{quantity}, #{unitPrice}, #{totalAmount}, "
            + "#{buyerName}, #{buyerPhone}, #{address}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    @Select("SELECT " + COLUMNS + " FROM orders WHERE id = #{id}")
    Order findById(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + " FROM orders WHERE order_no = #{orderNo}")
    Order findByOrderNo(@Param("orderNo") String orderNo);

    /** 取某个日期前缀下最大的订单号，用于生成当日序号。 */
    @Select("SELECT MAX(order_no) FROM orders WHERE order_no LIKE #{prefix}")
    String maxOrderNo(@Param("prefix") String prefix);

    /**
     * 把「未支付」订单改为已支付并记录支付时间。
     * 条件里带 status = 'created'，并发重复支付时只有一条能更新成功，返回 0 表示没抢到。
     */
    @Update("UPDATE orders SET status = 'paid', paid_at = #{paidAt} "
            + "WHERE order_no = #{orderNo} AND status = 'created'")
    int markPaid(@Param("orderNo") String orderNo, @Param("paidAt") LocalDateTime paidAt);

    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM orders"
            + "<where>"
            + "<if test=\"buyerPhone != null and buyerPhone != ''\"> AND buyer_phone = #{buyerPhone}</if>"
            + "</where>"
            + " ORDER BY created_at DESC, id DESC"
            + " LIMIT #{pageSize} OFFSET #{offset}"
            + "</script>")
    List<Order> page(@Param("buyerPhone") String buyerPhone,
                     @Param("offset") long offset,
                     @Param("pageSize") int pageSize);

    @Select("<script>"
            + "SELECT COUNT(*) FROM orders"
            + "<where>"
            + "<if test=\"buyerPhone != null and buyerPhone != ''\"> AND buyer_phone = #{buyerPhone}</if>"
            + "</where>"
            + "</script>")
    long count(@Param("buyerPhone") String buyerPhone);
}
