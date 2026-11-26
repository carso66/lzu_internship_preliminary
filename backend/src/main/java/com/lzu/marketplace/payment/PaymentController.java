package com.lzu.marketplace.payment;

import com.lzu.marketplace.common.ApiResponse;
import com.lzu.marketplace.payment.dto.CreatePaymentRequest;
import com.lzu.marketplace.payment.dto.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口。已实现官方 §6 L2-9 模拟支付。
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /**
     * POST /api/payments —— 模拟支付。
     *
     * <p>成功 200 + {@code {paymentId, orderNo, amount, status, paidAt}}；
     * 非法 method → 400 + 40001；订单不存在 → 404 + 40401；已支付 → 409 + 40903。
     */
    @PostMapping
    public ApiResponse<PaymentResponse> pay(@Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.ok(service.pay(request));
    }
}
