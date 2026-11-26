package com.zyh.marketplace.controller;

import com.zyh.marketplace.common.ApiResponse;
import com.zyh.marketplace.common.PageResult;
import com.zyh.marketplace.dto.CreateProductRequest;
import com.zyh.marketplace.dto.ProductQuery;
import com.zyh.marketplace.dto.ProductResponse;
import com.zyh.marketplace.dto.ProductStatusResponse;
import com.zyh.marketplace.dto.UpdateProductStatusRequest;
import com.zyh.marketplace.service.ProductService;
import com.zyh.marketplace.support.RequestBodyParser;
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
 * 商品接口（L0-2 ~ L0-5）：只负责 HTTP 收发，校验与业务逻辑在 ProductService。
 * 响应体统一为 code / message / data。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final RequestBodyParser requestBodyParser;

    public ProductController(ProductService productService, RequestBodyParser requestBodyParser) {
        this.productService = productService;
        this.requestBodyParser = requestBodyParser;
    }

    /** L0-2 发布商品：成功 201 */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@RequestBody(required = false) String body) {
        CreateProductRequest request = requestBodyParser.parseCreateProduct(body);
        ProductResponse created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** L0-3 商品列表：keyword / status / category 可叠加，page 默认 1，pageSize 默认 10（1~50） */
    @GetMapping
    public ApiResponse<PageResult<ProductResponse>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "pageSize", required = false) String pageSize) {
        return ApiResponse.ok(productService.list(ProductQuery.of(keyword, status, category, page, pageSize)));
    }

    /** L0-4 商品详情 */
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(productService.detail(id));
    }

    /** L0-5 上下架：status 仅 on / off */
    @PatchMapping("/{id}/status")
    public ApiResponse<ProductStatusResponse> updateStatus(@PathVariable("id") Long id,
                                                           @RequestBody(required = false) String body) {
        UpdateProductStatusRequest request = requestBodyParser.parseUpdateStatus(body);
        return ApiResponse.ok(productService.updateStatus(id, request));
    }
}
