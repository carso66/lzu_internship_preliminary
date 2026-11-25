package com.lzu.xy.controller;

import com.lzu.xy.controller.model.dto.OrderCreateDTO;
import com.lzu.xy.controller.model.dto.OrderQueryDTO;
import com.lzu.xy.controller.model.vo.OrderVO;
import com.lzu.xy.controller.model.vo.PageVO;
import com.lzu.xy.response.Result;
import com.lzu.xy.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateDTO dto) {
        return Result.success(orderService.create(dto));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.success(orderService.getByOrderNo(orderNo));
    }

    @GetMapping
    public Result<PageVO<OrderVO>> list(@Valid OrderQueryDTO query) {
        return Result.success(orderService.listByBuyerPhone(query));
    }
}
