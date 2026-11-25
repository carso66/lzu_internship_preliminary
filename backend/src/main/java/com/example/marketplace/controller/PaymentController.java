package com.example.marketplace.controller;

import com.example.marketplace.domain.Payment;
import com.example.marketplace.dto.CreatePaymentRequest;
import com.example.marketplace.dto.PaymentResponse;
import com.example.marketplace.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口：负责创建模拟支付和按支付号查询支付详情。
 */
@Validated
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        Payment payment = paymentService.create(request);
        return ApiResponse.ok(PaymentResponse.from(payment));
    }

    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentResponse> getByPaymentId(@PathVariable String paymentId) {
        Payment payment = paymentService.getByPaymentId(paymentId);
        return ApiResponse.ok(PaymentResponse.from(payment));
    }
}
