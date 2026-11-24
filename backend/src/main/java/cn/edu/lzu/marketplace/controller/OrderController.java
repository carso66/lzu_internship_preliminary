package cn.edu.lzu.marketplace.controller;

import cn.edu.lzu.marketplace.common.ApiResponse;
import cn.edu.lzu.marketplace.dto.CreateOrderRequest;
import cn.edu.lzu.marketplace.dto.OrderPageResponse;
import cn.edu.lzu.marketplace.dto.OrderResponse;
import cn.edu.lzu.marketplace.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * 订单接口（L1）：
 * POST /api/orders、GET /api/orders/{orderNo}、
 * GET /api/orders?buyerPhone=...&page=...&pageSize=...
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** L1-6 下单：成功返回 201 + code 0 */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** L1-7 按订单号查详情：不存在 → 404 + 40401（由 Service 抛 BusinessException） */
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderResponse> getByOrderNo(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getByOrderNo(orderNo));
    }

    /** L1-8 按买家手机号查询订单列表（分页 + 创建时间倒序） */
    @GetMapping
    public ApiResponse<OrderPageResponse> list(
            @RequestParam String buyerPhone,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page 从 1 开始") Integer page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 不能小于 1")
            @Max(value = 50, message = "pageSize 最大为 50") Integer pageSize) {
        return ApiResponse.ok(orderService.list(page, pageSize, buyerPhone));
    }
}
