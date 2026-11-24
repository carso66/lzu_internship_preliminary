package com.lzu.marketplace.product;

import com.lzu.marketplace.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Product>> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request)));
    }

    @GetMapping
    public ApiResponse<ProductPage> list(@Valid @ModelAttribute ProductListQuery query) {
        return ApiResponse.ok(service.list(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> getById(
            @PathVariable @Positive(message = "必须为正整数") Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ProductStatusResponse> updateStatus(
            @PathVariable @Positive(message = "必须为正整数") Long id,
            @Valid @RequestBody UpdateProductStatusRequest request) {
        return ApiResponse.ok(service.updateStatus(id, request));
    }
}
