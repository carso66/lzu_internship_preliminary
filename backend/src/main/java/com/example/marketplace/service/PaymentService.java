package com.example.marketplace.service;

import com.example.marketplace.domain.Order;
import com.example.marketplace.domain.Payment;
import com.example.marketplace.dto.CreatePaymentRequest;
import com.example.marketplace.exception.OrderAlreadyPaidException;
import com.example.marketplace.exception.OrderNotFoundException;
import com.example.marketplace.exception.PaymentNotFoundException;
import com.example.marketplace.mapper.OrderMapper;
import com.example.marketplace.mapper.PaymentMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 支付业务服务：以订单状态条件更新和支付唯一约束防止重复支付。
 */
@Service
public class PaymentService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentNumberGenerator paymentNumberGenerator;

    public PaymentService(
            OrderMapper orderMapper,
            PaymentMapper paymentMapper,
            PaymentNumberGenerator paymentNumberGenerator
    ) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.paymentNumberGenerator = paymentNumberGenerator;
    }

    /**
     * 创建模拟支付。订单状态迁移和支付记录写入在同一事务中完成。
     */
    @Transactional
    public Payment create(CreatePaymentRequest request) {
        Order order = orderMapper.findByOrderNo(request.orderNo());
        if (order == null) {
            throw new OrderNotFoundException(request.orderNo());
        }

        OffsetDateTime paidAt = OffsetDateTime.now();
        // 只有 created 订单能迁移到 paid，影响行数是并发幂等判断依据。
        int updatedRows = orderMapper.markPaidIfCreated(order.getId(), paidAt);
        if (updatedRows != 1) {
            throw new OrderAlreadyPaidException(request.orderNo());
        }

        Payment payment = new Payment();
        payment.setPaymentId(paymentNumberGenerator.generate());
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setMethod(request.method());
        payment.setAmount(order.getTotalAmount());
        payment.setPaidAt(paidAt);
        // 唯一约束兜底并发重复支付；若冲突来自支付号碰撞则继续抛出原异常。
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException exception) {
            if (paymentMapper.existsByOrderId(order.getId())) {
                throw new OrderAlreadyPaidException(request.orderNo());
            }
            throw exception;
        }
        return paymentMapper.findByPaymentId(payment.getPaymentId());
    }

    @Transactional(readOnly = true)
    public Payment getByPaymentId(String paymentId) {
        Payment payment = paymentMapper.findByPaymentId(paymentId);
        if (payment == null) {
            throw new PaymentNotFoundException(paymentId);
        }
        return payment;
    }
}
