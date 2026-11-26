package com.campus.marketplace.controller;

import com.campus.marketplace.common.ApiResponse;
import com.campus.marketplace.dto.CreateProductRequest;
import com.campus.marketplace.dto.PageResult;
import com.campus.marketplace.dto.StatusUpdateRequest;
import com.campus.marketplace.dto.UpdateStatusResponse;
import org.springframework.web.bind.annotation.PatchMapping;
import com.campus.marketplace.model.Product;
import com.campus.marketplace.service.ProductService;
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
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** L0-2 发布商品：成功返回 201 + code=0 */
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Product> create(@RequestBody CreateProductRequest request) {
        return ApiResponse.success(productService.create(request));
    }

    @GetMapping("/products")
    public ApiResponse<PageResult<Product>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "10") String pageSize) {
        return ApiResponse.success(productService.list(keyword, status, category, page, pageSize));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<Product> getDetail(@PathVariable("id") String id) {
        return ApiResponse.success(productService.getById(id));
    }

    @PatchMapping("/products/{id}/status")
    public ApiResponse<UpdateStatusResponse> updateStatus(
            @PathVariable("id") String id,
            @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(productService.updateStatus(id, request.getStatus()));
    }
}
