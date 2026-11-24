package com.example.marketplace.controller;

import com.example.marketplace.domain.Product;
import com.example.marketplace.dto.CreateProductRequest;
import com.example.marketplace.dto.ProductListResponse;
import com.example.marketplace.dto.ProductResponse;
import com.example.marketplace.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        Product product = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ProductResponse.from(product)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getById(@PathVariable long id) {
        Product product = productService.getById(id);
        return ApiResponse.ok(ProductResponse.from(product));
    }

    @GetMapping
    public ApiResponse<ProductListResponse> list(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page must be greater than or equal to 1")
            int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize must be between 1 and 50")
            @Max(value = 50, message = "pageSize must be between 1 and 50")
            int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @Pattern(regexp = "on|off", message = "status must be on or off")
            String status,
            @RequestParam(required = false) String category
    ) {
        ProductService.ProductPage result = productService.list(keyword, status, category, page, pageSize);
        List<ProductResponse> items = result.items().stream()
                .map(ProductResponse::from)
                .toList();
        return ApiResponse.ok(new ProductListResponse(result.total(), page, pageSize, items));
    }
}
