package com.lzu.marketplace.order;

import com.lzu.marketplace.common.ApiResponse;
import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.order.dto.CreateOrderRequest;
import com.lzu.marketplace.order.dto.OrderQuery;
import com.lzu.marketplace.order.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。已实现官方 §5 L1-6 下单、L1-7 详情、L1-8 列表。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /**
     * POST /api/orders —— 下单。
     *
     * <p>成功 201 + 完整订单对象；参数非法 → 400 + 40001；
     * 商品不存在 → 404 + 40401；下架 → 409 + 40902；库存不足 → 409 + 40901。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /**
     * GET /api/orders/{orderNo} —— 订单详情（官方 §5 L1-7）。
     *
     * <p>不存在 → 404 + 40401。orderNo 声明为 String：不合法格式（如纯数字串）
     * 只是「查不到」，不是类型错误，应当走 404 而非 400。
     */
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderResponse> detail(@PathVariable String orderNo) {
        return ApiResponse.ok(service.getByOrderNo(orderNo));
    }

    /**
     * GET /api/orders?buyerPhone=...&amp;page=1&amp;pageSize=10 —— 买家订单列表（官方 §5 L1-8）。
     *
     * <p>buyerPhone 必填：缺失或空白 → 400 + 40001，不退化成「查全部买家」。
     * 分页参数沿用商品列表约定（默认 1/10，pageSize 范围 1~50，越界报 400）。
     */
    @GetMapping
    public ApiResponse<PageResult<OrderResponse>> list(
            @RequestParam(required = false) String buyerPhone,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {

        OrderQuery query = OrderQuery.of(buyerPhone, page, pageSize);
        return ApiResponse.ok(service.listByBuyerPhone(query));
    }
}
