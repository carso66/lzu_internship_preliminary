package com.zyh.marketplace.controller;

import com.zyh.marketplace.common.ApiResponse;
import com.zyh.marketplace.common.PageResult;
import com.zyh.marketplace.dto.CreateOrderRequest;
import com.zyh.marketplace.dto.OrderQuery;
import com.zyh.marketplace.dto.OrderResponse;
import com.zyh.marketplace.service.OrderService;
import com.zyh.marketplace.support.RequestBodyParser;
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
 * 订单接口（L1-6 ~ L1-8）：只负责 HTTP 收发，校验、算价与事务在 OrderService。
 * 响应体统一为 code / message / data。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final RequestBodyParser requestBodyParser;

    public OrderController(OrderService orderService, RequestBodyParser requestBodyParser) {
        this.orderService = orderService;
        this.requestBodyParser = requestBodyParser;
    }

    /** L1-6 下单：成功 201 */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@RequestBody(required = false) String body) {
        CreateOrderRequest request = requestBodyParser.parseCreateOrder(body);
        OrderResponse created = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** L1-7 订单详情：orderNo 是字符串，按路径变量原样传入 */
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderResponse> detail(@PathVariable("orderNo") String orderNo) {
        return ApiResponse.ok(orderService.detail(orderNo));
    }

    /** L1-8 订单列表：按 buyerPhone 精确查询，创建时间倒序，分页约定与商品列表一致 */
    @GetMapping
    public ApiResponse<PageResult<OrderResponse>> list(
            @RequestParam(value = "buyerPhone", required = false) String buyerPhone,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "pageSize", required = false) String pageSize) {
        return ApiResponse.ok(orderService.list(OrderQuery.of(buyerPhone, page, pageSize)));
    }
}
