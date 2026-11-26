package com.lzu.marketplace.payment;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.common.ErrorCode;
import com.lzu.marketplace.order.Order;
import com.lzu.marketplace.order.OrderRepository;
import com.lzu.marketplace.payment.dto.CreatePaymentRequest;
import com.lzu.marketplace.payment.dto.PaymentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 模拟支付（官方 §6 L2-9）。
 *
 * <p><b>事务边界</b>：{@link #pay} 是唯一事务。锁订单行 → 判状态 → 写支付记录 →
 * 改订单状态，全部在同一事务内，任一失败整体回滚。
 *
 * <p><b>并发防重</b>：先 {@code SELECT ... FOR UPDATE} 锁订单行。
 * 两个并发支付同一订单时，后到者在锁上排队；先到者提交后，后到者读到
 * {@code status='paid'} → 40903。即便锁失效，{@code uk_payments_order_no}
 * 唯一约束也会让第二条支付记录插入失败，不会出现「同一订单两条成功支付」。
 *
 * <p><b>金额来源</b>：取锁内读到的 {@code order.totalAmount()}（下单时服务端算好的），
 * 不接收客户端传值，也不重新查商品价格——支付结算的是订单成立时的金额。
 *
 * <p><b>不扣库存</b>：库存在下单（L1-6）时已扣，支付阶段只改订单与支付表，
 * 不会碰 {@code products.stock}。
 *
 * <p><b>同步返回 paid</b>：题目允许「直接同步返回 paid，不做异步回调」。
 * 不新增支付轮询、不接入真实第三方平台。
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentIdGenerator paymentIdGenerator;

    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          PaymentIdGenerator paymentIdGenerator) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentIdGenerator = paymentIdGenerator;
    }

    @Transactional
    public PaymentResponse pay(CreatePaymentRequest req) {
        String orderNo = req.orderNo();

        // 1. 锁订单行（不存在 → 404）
        Order order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "订单不存在"));

        // 2. 已支付 → 40903（重复支付）
        if (Order.STATUS_PAID.equals(order.status())) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_PAID, "订单已支付，请勿重复支付");
        }

        // 3. 支付金额 = 订单总额（服务端算好的）
        long amount = order.totalAmount();
        LocalDateTime paidAt = LocalDateTime.now();

        // 4. 写支付记录 + 改订单状态，同一事务
        Payment payment = paymentRepository.insert(
                paymentIdGenerator.next(), orderNo, amount, req.method(), paidAt);

        int affected = orderRepository.markPaid(orderNo, paidAt);
        if (affected != 1) {
            // 锁内已确认 status='created'，走到这里说明状态被未预期地改动。
            // 抛异常触发回滚，宁可失败也不留下「有支付记录但订单没标记」的不一致。
            throw new IllegalStateException("订单状态更新失败：" + orderNo);
        }

        return PaymentResponse.from(payment);
    }
}
