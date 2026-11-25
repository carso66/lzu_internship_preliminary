package com.zyh.marketplace.mapper;

import com.zyh.marketplace.entity.Order;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * orders 表的 SQL 层。
 * 所有入参一律使用 #{} 参数绑定（不拼接字符串），防止 SQL 注入。
 */
@Mapper
public interface OrderMapper {

    String COLUMNS = "id, order_no, product_id, product_title, quantity, unit_price, total_amount, "
            + "buyer_name, buyer_phone, address, status, paid_at, created_at";

    @Insert("INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, total_amount, "
            + "buyer_name, buyer_phone, address, status, created_at) "
            + "VALUES (#{orderNo}, #{productId}, #{productTitle}, #{quantity}, #{unitPrice}, #{totalAmount}, "
            + "#{buyerName}, #{buyerPhone}, #{address}, #{status}, CURRENT_TIMESTAMP)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Order order);

    @Select("SELECT " + COLUMNS + " FROM orders WHERE id = #{id}")
    Order findById(@Param("id") Long id);

    /** L1-7：按订单号查详情；order_no 有 UNIQUE 约束，最多命中一条 */
    @Select("SELECT " + COLUMNS + " FROM orders WHERE order_no = #{orderNo}")
    Order findByOrderNo(@Param("orderNo") String orderNo);

    /** L1-8：按手机号统计筛选后的总条数 */
    @Select("SELECT COUNT(*) FROM orders WHERE buyer_phone = #{buyerPhone}")
    long countByBuyerPhone(@Param("buyerPhone") String buyerPhone);

    /**
     * L1-8：数据库分页 + 稳定排序：created_at DESC, id DESC ——
     * id 唯一，同一创建时间的订单顺序固定，翻页不重不漏；等价查询已由
     * idx_orders_buyer_phone_created_at (buyer_phone, created_at) 支持。
     */
    @Select("SELECT " + COLUMNS + " FROM orders WHERE buyer_phone = #{buyerPhone} "
            + "ORDER BY created_at DESC, id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Order> findPageByBuyerPhone(@Param("buyerPhone") String buyerPhone,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);
}
