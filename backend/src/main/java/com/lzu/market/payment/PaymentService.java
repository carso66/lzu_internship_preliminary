package com.lzu.market.payment;

import com.lzu.market.common.ApiException;
import com.lzu.market.common.BizNoGenerator;
import com.lzu.market.order.Order;
import com.lzu.market.order.OrderDao;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 支付只做一件事：把订单从 created 推到 paid，并留一条流水。
 * 重复支付挡三层，任何一层命中都抛 runtime 异常让整个事务回滚：
 * 1) 进事务先看订单状态；2) UPDATE 带 status <> 'paid' 条件，affected=0 就说明已付；
 * 3) payments.order_id 唯一约束兜并发下的漏网之鱼。
 */
@Service
public class PaymentService {

    private final OrderDao orderDao;
    private final PaymentDao paymentDao;
    private final BizNoGenerator bizNoGenerator;

    public PaymentService(OrderDao orderDao, PaymentDao paymentDao, BizNoGenerator bizNoGenerator) {
        this.orderDao = orderDao;
        this.paymentDao = paymentDao;
        this.bizNoGenerator = bizNoGenerator;
    }

    @Transactional
    public PaymentResult pay(PayReq req) {
        Order order = orderDao.findByNo(req.orderNo().trim())
                .orElseThrow(() -> ApiException.notFound("订单不存在"));

        if ("paid".equals(order.status())) {
            throw ApiException.orderAlreadyPaid();
        }

        LocalDateTime paidAt = LocalDateTime.now();
        if (orderDao.markPaid(order.id(), Timestamp.valueOf(paidAt)) == 0) {
            throw ApiException.orderAlreadyPaid();
        }

        String paymentNo = bizNoGenerator.next("PAY");
        try {
            paymentDao.insert(paymentNo, order.id(), req.method(), order.totalAmount(), paidAt);
        } catch (DuplicateKeyException e) {
            // 并发的第二个事务会撞唯一约束，这里转成业务语义，顺带回滚上面那次 UPDATE
            throw ApiException.orderAlreadyPaid();
        }

        return new PaymentResult(paymentNo, order.orderNo(), order.totalAmount(), "paid", paidAt);
    }
}
