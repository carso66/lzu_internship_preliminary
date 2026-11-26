package com.lzu.marketplace.controller;

import com.lzu.marketplace.common.ApiResponse;
import com.lzu.marketplace.pojo.Product;
import com.lzu.marketplace.pojo.dto.ProductCreateRequest;
import com.lzu.marketplace.pojo.dto.ProductListResponse;
import com.lzu.marketplace.pojo.dto.ProductStatusRequest;
import com.lzu.marketplace.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 商品接口（L0）：L0-2 发布 / L0-3 列表 / L0-4 详情 / L0-5 上下架。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    /** L0-2 发布商品：成功 201 + 完整商品。 */
    @PostMapping
    public ResponseEntity<ApiResponse<Product>> create(@RequestBody ProductCreateRequest request) {
        Product product = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(product));
    }

    /** L0-3 商品列表：三个筛选条件可叠加，page/pageSize 有默认值与范围限制。 */
    @GetMapping
    public ApiResponse<ProductListResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(productService.page(keyword, status, category, page, pageSize));
    }

    /** L0-4 商品详情：不存在 404 + 40401。 */
    @GetMapping("/{id}")
    public ApiResponse<Product> detail(@PathVariable Long id) {
        return ApiResponse.ok(productService.findById(id));
    }

    /** L0-5 上下架：status 只能 on/off，否则 400 + 40001。 */
    @PatchMapping("/{id}/status")
    public ApiResponse<Product> updateStatus(@PathVariable Long id,
                                             @RequestBody ProductStatusRequest request) {
        String status = (request == null) ? null : request.getStatus();
        return ApiResponse.ok(productService.updateStatus(id, status));
    }
}
