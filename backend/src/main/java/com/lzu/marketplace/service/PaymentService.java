package com.lzu.marketplace.service;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.mapper.OrderMapper;
import com.lzu.marketplace.mapper.PaymentMapper;
import com.lzu.marketplace.pojo.Order;
import com.lzu.marketplace.pojo.Payment;
import com.lzu.marketplace.pojo.dto.PaymentCreateRequest;
import com.lzu.marketplace.pojo.dto.PaymentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

/**
 * 支付服务：同步返回 paid，不做异步回调。
 *
 * <p>幂等靠两层保证：
 * <ol>
 *   <li>订单状态用带条件的 UPDATE 抢占（status = 'created'），并发下只有一个能成功；</li>
 *   <li>payments.order_no 上有唯一约束，兜底防止同一订单落两条支付记录。</li>
 * </ol>
 */
@Service
public class PaymentService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String ORDER_NO_PREFIX = "PAY";

    private static final Set<String> METHODS = Set.of("alipay", "wechat");

    private static final int PAYMENT_NO_RETRY = 5;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Transactional
    public PaymentResponse pay(PaymentCreateRequest request) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }

        String orderNo = trimToNull(request.getOrderNo());
        if (orderNo == null) {
            throw badRequest("orderNo 不能为空");
        }
        if (orderNo.length() > 32) {
            throw badRequest("orderNo 长度不能超过 32");
        }

        String method = trimToNull(request.getMethod());
        if (method == null) {
            throw badRequest("method 不能为空");
        }
        method = method.toLowerCase(Locale.ROOT);
        if (!METHODS.contains(method)) {
            throw badRequest("method 只能为 alipay 或 wechat");
        }

        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(40401, 404, "订单不存在");
        }
        if ("paid".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(40903, 409, "订单已支付");
        }

        // H2 的 TIMESTAMP 只精确到微秒，先截断，保证订单与支付单存进去、读出来是同一个值
        LocalDateTime paidAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        // 抢占式更新：两个请求同时到达时，只有一个能把 created 改成 paid。
        if (orderMapper.markPaid(orderNo, paidAt) == 0) {
            // 更新 0 行有两种可能：已被并发请求支付，或订单处于其它状态（如已取消）
            Order latest = orderMapper.findByOrderNo(orderNo);
            if (latest == null) {
                throw new BusinessException(40401, 404, "订单不存在");
            }
            if ("paid".equalsIgnoreCase(latest.getStatus())) {
                throw new BusinessException(40903, 409, "订单已支付");
            }
            // 其它状态（如已取消）：题面错误码表没有对应码，严格只用 40903 表示"订单不可支付"
            throw new BusinessException(40903, 409, "订单状态为 " + latest.getStatus() + "，不可支付");
        }

        Payment payment = new Payment();
        payment.setOrderNo(orderNo);
        payment.setAmount(order.getTotalAmount());
        payment.setMethod(method);
        payment.setStatus("paid");
        // 支付单与订单使用同一个支付时刻，避免两处时间对不上
        payment.setPaidAt(paidAt);
        insertWithUniquePaymentNo(payment);

        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getPaymentNo());
        response.setOrderNo(orderNo);
        response.setAmount(order.getTotalAmount());
        response.setStatus("paid");
        response.setPaidAt(payment.getPaidAt());
        return response;
    }

    /** 生成 PAY + yyyyMMdd + 4 位当日序号；撞唯一键就重新取号重试。 */
    private void insertWithUniquePaymentNo(Payment payment) {
        String prefix = ORDER_NO_PREFIX + LocalDate.now().format(DAY);
        for (int attempt = 0; attempt < PAYMENT_NO_RETRY; attempt++) {
            payment.setPaymentNo(prefix + String.format("%04d", nextSequence(prefix)));
            try {
                paymentMapper.insert(payment);
                return;
            } catch (DataIntegrityViolationException e) {
                // 单号被并发抢占，换下一个序号重试
            }
        }
        throw new BusinessException(50000, 500, "支付单号生成失败，请重试");
    }

    private long nextSequence(String prefix) {
        String max = paymentMapper.maxPaymentNo(prefix + "%");
        if (max == null || max.length() <= prefix.length()) {
            return 1;
        }
        String tail = max.substring(prefix.length());
        try {
            return Long.parseLong(tail) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(40001, 400, message);
    }
}
