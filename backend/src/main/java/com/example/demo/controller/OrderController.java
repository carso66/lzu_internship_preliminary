package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.PageResult;
import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.model.Order;
import com.example.demo.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * L1-6：创建订单。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Order>> create(
            @RequestBody CreateOrderRequest request
    ) {
        Order order = orderService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(order));
    }

    /**
     * L1-7：按订单号查询订单详情。
     */
    @GetMapping("/{orderNo}")
    public ApiResponse<Order> getByOrderNo(
            @PathVariable("orderNo") String orderNo
    ) {
        return ApiResponse.ok(
                orderService.getByOrderNo(orderNo)
        );
    }

    /**
     * L1-8：按买家手机号分页查询订单。
     */
    @GetMapping
    public ApiResponse<PageResult<Order>> list(
            @RequestParam(name = "buyerPhone") String buyerPhone,
            @RequestParam(name = "page", defaultValue = "1")
            int page,
            @RequestParam(name = "pageSize", defaultValue = "10")
            int pageSize
    ) {
        return ApiResponse.ok(
                orderService.listByBuyerPhone(
                        buyerPhone,
                        page,
                        pageSize
                )
        );
    }
}
