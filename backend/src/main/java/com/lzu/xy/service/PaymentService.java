package com.lzu.xy.service;

import com.lzu.xy.controller.model.dto.PaymentCreateDTO;
import com.lzu.xy.controller.model.vo.PaymentVO;
import com.lzu.xy.domain.Order;
import com.lzu.xy.domain.Payment;
import com.lzu.xy.enums.OrderStatusEnum;
import com.lzu.xy.enums.PaymentStatusEnum;
import com.lzu.xy.enums.ResultCodeEnum;
import com.lzu.xy.exception.BusinessException;
import com.lzu.xy.repository.OrderRepository;
import com.lzu.xy.repository.PaymentRepository;
import com.lzu.xy.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * 支付：订单状态流转 + 支付单落库，在同一事务里完成。
     * <p>
     * 幂等靠两道保险：
     * <ol>
     *   <li>把订单从「待支付」改成「已支付」用带状态条件的原子 UPDATE，并发时只有一个能改成功；</li>
     *   <li>payments.order_id 上有唯一索引，即使前面漏了也不会出现两条成功记录。</li>
     * </ol>
     */
    @Transactional
    public PaymentVO pay(PaymentCreateDTO dto) {
        Order order = orderRepository.findByOrderNoAndIsDeleted(dto.getOrderNo(), 0)
                .orElseThrow(() -> new BusinessException(ResultCodeEnum.NOT_FOUND));

        checkPayable(order.getStatus());

        LocalDateTime now = LocalDateTime.now();
        int updated = orderRepository.markPaid(order.getId(),
                OrderStatusEnum.CREATED.getValue(),
                OrderStatusEnum.PAID.getValue(),
                now);
        if (updated == 0) {
            // 竞态：另一个请求先把它付掉了，或者状态刚被改成取消
            Order latest = orderRepository.findById(order.getId())
                    .orElseThrow(() -> new BusinessException(ResultCodeEnum.NOT_FOUND));
            checkPayable(latest.getStatus());
            throw new BusinessException(ResultCodeEnum.ORDER_ALREADY_PAID);
        }

        long id = idGenerator.nextId();
        Payment payment = Payment.builder()
                .id(id)
                .paymentNo("PAY" + id)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .amount(order.getTotalAmount())
                .method(dto.getMethod())
                .status(PaymentStatusEnum.PAID.getValue())
                .paidAt(now)
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(0)
                .build();

        try {
            // 立即 flush，让唯一索引冲突在这里暴露，而不是等到事务提交
            return PaymentVO.from(paymentRepository.saveAndFlush(payment));
        } catch (DataIntegrityViolationException e) {
            // order_id 唯一索引兜底：同一订单已经有一条支付记录了
            throw new BusinessException(ResultCodeEnum.ORDER_ALREADY_PAID);
        }
    }

    @Transactional(readOnly = true)
    public PaymentVO getByPaymentNo(String paymentNo) {
        return paymentRepository.findByPaymentNoAndIsDeleted(paymentNo, 0)
                .map(PaymentVO::from)
                .orElseThrow(() -> new BusinessException(ResultCodeEnum.NOT_FOUND));
    }

    private void checkPayable(Integer status) {
        if (status != null && status == OrderStatusEnum.PAID.getValue()) {
            throw new BusinessException(ResultCodeEnum.ORDER_ALREADY_PAID);
        }
        if (status != null && status == OrderStatusEnum.CANCELLED.getValue()) {
            throw new BusinessException(ResultCodeEnum.ORDER_NOT_PAYABLE);
        }
    }
}
