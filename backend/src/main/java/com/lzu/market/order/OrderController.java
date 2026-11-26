package com.lzu.market.order;

import com.lzu.market.common.ApiException;
import com.lzu.market.common.ApiResponse;
import com.lzu.market.common.PageResult;
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

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderDao orderDao;

    public OrderController(OrderService orderService, OrderDao orderDao) {
        this.orderService = orderService;
        this.orderDao = orderDao;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Order> create(@Valid @RequestBody CreateOrderReq req) {
        return ApiResponse.ok(orderService.create(req));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Order> detail(@PathVariable String orderNo) {
        Order order = orderDao.findByNo(orderNo)
                .orElseThrow(() -> ApiException.notFound("订单不存在"));
        return ApiResponse.ok(order);
    }

    @GetMapping
    public ApiResponse<PageResult<Order>> list(
            @RequestParam(required = false) String buyerPhone,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        if (buyerPhone == null || buyerPhone.isBlank()) {
            throw ApiException.badRequest("buyerPhone 不能为空");
        }
        if (page < 1) {
            throw ApiException.badRequest("page 必须 ≥ 1");
        }
        if (pageSize < 1 || pageSize > 50) {
            throw ApiException.badRequest("pageSize 必须在 1~50 之间");
        }
        return ApiResponse.ok(orderDao.findPageByPhone(buyerPhone.trim(), page, pageSize));
    }
}
