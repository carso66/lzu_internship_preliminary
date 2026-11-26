package com.lzu.marketplace.controller;

import com.lzu.marketplace.common.ApiResponse;
import com.lzu.marketplace.payment.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }

    @PostMapping
    public ApiResponse<Payment> pay(@Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.success(service.pay(request));
    }
}
