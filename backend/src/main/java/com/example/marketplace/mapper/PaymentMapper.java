package com.example.marketplace.mapper;

import com.example.marketplace.domain.Payment;
import org.apache.ibatis.annotations.Param;

/**
 * 支付数据访问接口。
 */
public interface PaymentMapper {

    int insert(Payment payment);

    Payment findByPaymentId(@Param("paymentId") String paymentId);

    boolean existsByOrderId(@Param("orderId") long orderId);
}
