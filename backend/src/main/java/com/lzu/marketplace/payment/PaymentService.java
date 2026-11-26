package com.lzu.marketplace.payment;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.order.OrderRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    public PaymentService(OrderRepository orders, PaymentRepository payments) {
        this.orders = orders;
        this.payments = payments;
    }

    @Transactional
    public Payment pay(CreatePaymentRequest request) {
        // 锁住订单，让同一订单的支付串行检查状态；唯一约束再提供数据库兜底。
        var order = orders.find(request.orderNo(), true)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));
        if ("paid".equals(order.status())) throw new BusinessException(409, 40903, "订单已支付，不可重复支付");
        var payment = new Payment("PAY" + UUID.randomUUID().toString().replace("-", ""),
                order.orderNo(), order.totalAmount(), "paid", LocalDateTime.now());
        payments.insert(payment, request.method());
        orders.markPaid(order.orderNo(), payment.paidAt());
        return payment;
    }
}
