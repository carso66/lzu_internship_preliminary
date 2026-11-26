package com.lzu.marketplace.controller;

import com.lzu.marketplace.common.ApiResponse;
import com.lzu.marketplace.product.CreateProductRequest;
import com.lzu.marketplace.product.Product;
import com.lzu.marketplace.product.ProductPage;
import com.lzu.marketplace.product.ProductQuery;
import com.lzu.marketplace.product.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import com.lzu.marketplace.product.UpdateProductStatusRequest;
import java.util.Map;

/** 接收商品请求；字段校验交给请求/查询对象，数据库操作交给服务层。 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    // 与类上的路径组合成 POST /api/products；@Valid 会触发请求对象上的校验规则。
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Product> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.success(service.create(request));
    }

    // GET /api/products；查询参数绑定到 ProductQuery，@Valid 触发分页与状态校验。
    @GetMapping
    public ApiResponse<ProductPage> list(@Valid ProductQuery query) {
        return ApiResponse.success(service.list(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> detail(@PathVariable long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(
            @PathVariable long id, @Valid @RequestBody UpdateProductStatusRequest request) {
        Product product = service.updateStatus(id, request.status());
        return ApiResponse.success(Map.of("id", product.id(), "status", product.status()));
    }
}
