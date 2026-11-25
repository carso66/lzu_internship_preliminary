package com.lzu.xy.repository;

import com.lzu.xy.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** 按支付流水号查未删除的支付单 */
    Optional<Payment> findByPaymentNoAndIsDeleted(String paymentNo, Integer isDeleted);

    /** 按订单ID查未删除的支付单，用于幂等判断 */
    Optional<Payment> findByOrderIdAndIsDeleted(Long orderId, Integer isDeleted);
}
