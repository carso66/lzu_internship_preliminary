package com.lzu.marketplace.controller;

import com.lzu.marketplace.common.ApiResponse;
import com.lzu.marketplace.order.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Order> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Order> detail(@PathVariable String orderNo) {
        return ApiResponse.success(service.detail(orderNo));
    }

    @GetMapping
    public ApiResponse<OrderPage> list(@Valid OrderQuery query) {
        return ApiResponse.success(service.list(query));
    }
}
