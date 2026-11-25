package com.example.marketplace.mapper;

import com.example.marketplace.domain.Payment;
import org.apache.ibatis.annotations.Param;

public interface PaymentMapper {

    int insert(Payment payment);

    Payment findByPaymentId(@Param("paymentId") String paymentId);
}
