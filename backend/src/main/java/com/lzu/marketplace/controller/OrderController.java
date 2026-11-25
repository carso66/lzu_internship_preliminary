package com.lzu.marketplace.controller;

import com.lzu.marketplace.common.ApiResponse;
import com.lzu.marketplace.pojo.Order;
import com.lzu.marketplace.pojo.dto.OrderCreateRequest;
import com.lzu.marketplace.pojo.dto.OrderListResponse;
import com.lzu.marketplace.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 订单接口（L1）：L1-6 下单 / L1-7 订单详情 / L1-8 订单列表。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /** L1-6 下单：成功 201 + 订单详情。 */
    @PostMapping
    public ResponseEntity<ApiResponse<Order>> create(@RequestBody OrderCreateRequest request) {
        Order order = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(order));
    }

    /** L1-7 订单详情：按订单号查，不存在 404 + 40401。 */
    @GetMapping("/{orderNo}")
    public ApiResponse<Order> detail(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.findByOrderNo(orderNo));
    }

    /** L1-8 订单列表：按手机号筛选，创建时间倒序。 */
    @GetMapping
    public ApiResponse<OrderListResponse> list(
            @RequestParam(required = false) String buyerPhone,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(orderService.page(buyerPhone, page, pageSize));
    }
}
