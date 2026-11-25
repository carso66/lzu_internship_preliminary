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

    @Transactional
    public Payment create(CreatePaymentRequest request) {
        Order order = orderMapper.findByOrderNo(request.orderNo());
        if (order == null) {
            throw new OrderNotFoundException(request.orderNo());
        }

        OffsetDateTime paidAt = OffsetDateTime.now();
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
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException exception) {
            throw new OrderAlreadyPaidException(request.orderNo());
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
