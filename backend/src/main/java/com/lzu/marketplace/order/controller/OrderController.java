package com.lzu.marketplace.order.controller;

import com.lzu.marketplace.common.ApiResponse;
import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.order.dto.CreateOrderRequest;
import com.lzu.marketplace.order.model.Order;
import com.lzu.marketplace.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口：下单、按订单号查详情、按手机号分页查订单列表。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /** POST /api/orders —— 下单，返回 201。 */
    @PostMapping
    public ResponseEntity<ApiResponse<Order>> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = service.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(order));
    }

    /** GET /api/orders/{orderNo} —— 按订单号查订单详情。 */
    @GetMapping("/{orderNo}")
    public ApiResponse<Order> getByOrderNo(@PathVariable String orderNo) {
        return ApiResponse.ok(service.getByOrderNo(orderNo));
    }

    /**
     * GET /api/orders?buyerPhone=&page=&pageSize= —— 按手机号分页查订单。
     *
     * <p>参数一律 required=false，由 Service 显式校验并返回 400/40001，
     * 避免缺失参数在参数绑定阶段变成 500。</p>
     */
    @GetMapping
    public ApiResponse<PageResult<Order>> list(
            @RequestParam(required = false) String buyerPhone,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(service.list(buyerPhone, page, pageSize));
    }
}
