package com.example.marketplace.controller;

import com.example.marketplace.domain.Order;
import com.example.marketplace.dto.CreateOrderRequest;
import com.example.marketplace.dto.OrderListResponse;
import com.example.marketplace.dto.OrderResponse;
import com.example.marketplace.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
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

    @GetMapping
    public ApiResponse<OrderListResponse> list(
            @RequestParam(required = false)
            @NotBlank(message = "buyerPhone must not be blank")
            String buyerPhone,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page must be greater than or equal to 1")
            int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize must be between 1 and 50")
            @Max(value = 50, message = "pageSize must be between 1 and 50")
            int pageSize
    ) {
        OrderService.OrderPage result = orderService.listByBuyerPhone(buyerPhone, page, pageSize);
        List<OrderResponse> items = result.items().stream()
                .map(OrderResponse::from)
                .toList();
        return ApiResponse.ok(new OrderListResponse(result.total(), page, pageSize, items));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(OrderResponse.from(order)));
    }
}
