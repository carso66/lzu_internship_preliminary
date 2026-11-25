package com.lzu.marketplace.product.controller;

import com.lzu.marketplace.common.ApiResponse;
import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.product.dto.CreateProductRequest;
import com.lzu.marketplace.product.dto.UpdateStatusRequest;
import com.lzu.marketplace.product.dto.UpdateStatusResult;
import com.lzu.marketplace.product.model.Product;
import com.lzu.marketplace.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口：发布、列表、详情、上下架。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** POST /api/products —— 发布商品，返回 201。 */
    @PostMapping
    public ResponseEntity<ApiResponse<Product>> create(@Valid @RequestBody CreateProductRequest request) {
        Product product = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(product));
    }

    /** GET /api/products —— 关键字/状态/分类筛选 + 分页。 */
    @GetMapping
    public ApiResponse<PageResult<Product>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(service.list(keyword, status, category, page, pageSize));
    }

    /** GET /api/products/{id} —— 商品详情。 */
    @GetMapping("/{id}")
    public ApiResponse<Product> getById(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    /** PATCH /api/products/{id}/status —— 上下架。 */
    @PatchMapping("/{id}/status")
    public ApiResponse<UpdateStatusResult> updateStatus(@PathVariable Long id,
                                                        @RequestBody UpdateStatusRequest request) {
        return ApiResponse.ok(service.updateStatus(id, request.status()));
    }
}
