package com.example.marketplace.controller;

import com.example.marketplace.domain.Order;
import com.example.marketplace.dto.CreateOrderRequest;
import com.example.marketplace.dto.OrderResponse;
import com.example.marketplace.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderResponse> getByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.getByOrderNo(orderNo);
        return ApiResponse.ok(OrderResponse.from(order));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(OrderResponse.from(order)));
    }
}
