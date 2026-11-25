package com.lzu.xy.controller;

import com.lzu.xy.controller.model.dto.PaymentCreateDTO;
import com.lzu.xy.controller.model.vo.PaymentVO;
import com.lzu.xy.response.Result;
import com.lzu.xy.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** L2-9 发起支付，同步返回 paid */
    @PostMapping
    public Result<PaymentVO> create(@Valid @RequestBody PaymentCreateDTO dto) {
        return Result.success(paymentService.pay(dto));
    }

    /** 按支付流水号查询支付状态，供前端轮询 */
    @GetMapping("/{paymentId}")
    public Result<PaymentVO> detail(@PathVariable String paymentId) {
        return Result.success(paymentService.getByPaymentNo(paymentId));
    }
}
