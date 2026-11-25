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
 * 订单 HTTP 接口。接收并校验请求，委托 OrderService 处理订单业务，
 * 再使用统一响应结构返回结果。库存与事务规则由业务层负责。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // ① 通过构造器注入订单服务，以接口衔接控制层与业务层。
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ② 创建订单
    // 接收买家的下单请求，反序列化后由 Bean Validation 校验。订单服务完成下单后，控制器返回 HTTP 201 和订单快照。
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    // ③ 订单详情
    // 根据正式订单号查询订单详情，订单不存在时由异常处理器映射为 HTTP 404 和业务码 40401。
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderResponse> getByOrderNo(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getByOrderNo(orderNo));
    }

    // ④ 订单列表
    // 按买家手机号分页查询。Spring MVC 校验页码和页大小，业务层返回按创建时间倒序的数据。
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
