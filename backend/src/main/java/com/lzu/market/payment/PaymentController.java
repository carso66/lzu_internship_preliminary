package com.lzu.market.payment;

import com.lzu.market.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 支付成功是"状态变更"而不是"新建资源"，按题目约定返回 200 不是 201 */
    @PostMapping
    public ApiResponse<PaymentResult> pay(@Valid @RequestBody PayReq req) {
        return ApiResponse.ok(paymentService.pay(req));
    }
}
