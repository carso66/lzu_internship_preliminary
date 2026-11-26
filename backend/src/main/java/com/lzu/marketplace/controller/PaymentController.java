package com.lzu.marketplace.controller;

import com.lzu.marketplace.common.ApiResponse;
import com.lzu.marketplace.pojo.dto.PaymentCreateRequest;
import com.lzu.marketplace.pojo.dto.PaymentResponse;
import com.lzu.marketplace.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口：成功按题目要求返回 HTTP 200（不是 201）。
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping
    public ApiResponse<PaymentResponse> pay(@RequestBody PaymentCreateRequest request) {
        return ApiResponse.ok(paymentService.pay(request));
    }
}
