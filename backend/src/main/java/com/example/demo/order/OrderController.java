package com.example.demo.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.common.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	/** L1-6 下单：成功返回 201，data 为落库后的订单（含服务端算出的金额）。 */
	@PostMapping
	public ResponseEntity<ApiResponse<Order>> create(@RequestBody(required = false) JsonNode body) {
		Order order = orderService.create(OrderCreateRequest.parse(body));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(order));
	}

	/** L1-7 订单详情：按订单号查，不存在 → 404 + 40401。 */
	@GetMapping("/{orderNo}")
	public ApiResponse<Order> detail(@PathVariable String orderNo) {
		return ApiResponse.ok(orderService.get(orderNo));
	}

	/** L1-8 我的订单：按手机号查，创建时间倒序，page 与 pageSize 有默认值。 */
	@GetMapping
	public ApiResponse<OrderPage> list(
			@RequestParam(required = false) String buyerPhone,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer pageSize) {
		OrderListQuery query = OrderListQuery.parse(buyerPhone, page, pageSize);
		return ApiResponse.ok(orderService.list(query));
	}
}
