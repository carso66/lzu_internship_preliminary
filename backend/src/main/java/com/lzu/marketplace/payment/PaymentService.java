package com.lzu.marketplace.payment;

import com.lzu.marketplace.common.ApiCode;
import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.order.Order;
import com.lzu.marketplace.order.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentNumberGenerator paymentNumberGenerator;

    public PaymentService(OrderRepository orderRepository,
                          PaymentRepository paymentRepository,
                          PaymentNumberGenerator paymentNumberGenerator) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentNumberGenerator = paymentNumberGenerator;
    }

    /**
     * 同步支付在一个事务内完成：锁定订单行、校验状态、更新订单、写入支付记录。
     * 行锁使同一订单的并发支付按顺序处理，第二个请求只能看到已支付状态并返回 409。
     * 订单条件更新和 payments.order_no 唯一约束作为数据库层面的第二道幂等保护。
     */
    @Transactional
    public Payment pay(CreatePaymentRequest request) {
        Order order = orderRepository.findByOrderNoForUpdate(request.orderNo())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, ApiCode.NOT_FOUND, "订单不存在"));
        if (!"created".equals(order.status())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, ApiCode.ORDER_ALREADY_PAID, "订单已支付，不可重复支付");
        }

        OffsetDateTime paidAt = OffsetDateTime.now(ZoneOffset.UTC);
        int changed = orderRepository.markPaid(order.orderNo(), paidAt);
        if (changed != 1) {
            // 正常路径上订单行已锁定且状态为 created，更新应当成功。
            // 无法确认支付更新时不写支付记录，直接回滚。
            throw new IllegalStateException("更新订单支付状态失败");
        }

        String paymentNo = paymentNumberGenerator.next();
        paymentRepository.insert(new PaymentDraft(
                paymentNo, order.orderNo(), order.totalAmount(), request.method(), paidAt));
        return paymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new IllegalStateException("写入支付记录后无法读取支付记录"));
    }
}
