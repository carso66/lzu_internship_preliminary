package com.lzu.marketplace.order;

import com.lzu.marketplace.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Order>> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request)));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Order> getByOrderNo(@PathVariable String orderNo) {
        return ApiResponse.ok(service.getByOrderNo(orderNo));
    }

    @GetMapping
    public ApiResponse<OrderPage> list(@Valid @ModelAttribute OrderListQuery query) {
        return ApiResponse.ok(service.list(query));
    }
}
