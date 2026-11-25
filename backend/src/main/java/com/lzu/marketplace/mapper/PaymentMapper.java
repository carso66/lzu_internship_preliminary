package com.lzu.marketplace.mapper;

import com.lzu.marketplace.pojo.Payment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 支付单 Mapper（注解式 SQL，列名与 payments 表一致）。
 */
@Mapper
public interface PaymentMapper {

    String COLUMNS = "id, payment_no, order_no, amount, method, status, paid_at, created_at";

    @Insert("INSERT INTO payments (payment_no, order_no, amount, method, status, paid_at) "
            + "VALUES (#{paymentNo}, #{orderNo}, #{amount}, #{method}, #{status}, #{paidAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Payment payment);

    @Select("SELECT " + COLUMNS + " FROM payments WHERE order_no = #{orderNo}")
    Payment findByOrderNo(@Param("orderNo") String orderNo);

    /** 取某个日期前缀下最大的支付单号，用于生成当日序号。 */
    @Select("SELECT MAX(payment_no) FROM payments WHERE payment_no LIKE #{prefix}")
    String maxPaymentNo(@Param("prefix") String prefix);
}
