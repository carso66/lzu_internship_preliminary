package com.example.demo.controller;
import com.example.demo.dto.UpdateProductStatusRequest;
import org.springframework.web.bind.annotation.PatchMapping;
import com.example.demo.common.ApiResponse;
import com.example.demo.dto.CreateProductRequest;
import com.example.demo.model.Product;
import com.example.demo.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.example.demo.common.PageResult;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Product>> create(
            @RequestBody CreateProductRequest request
    ) {
        Product product = productService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(product));
    }
    @PatchMapping("/{id}/status")
    public ApiResponse<Product> updateStatus(
            @PathVariable("id") long id,
            @RequestBody UpdateProductStatusRequest request
    ) {
        return ApiResponse.ok(productService.updateStatus(id, request));
    }
    @GetMapping
    public ApiResponse<PageResult<Product>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(
                productService.list(
                        keyword, status, category, page, pageSize
                )
        );
    }
    @GetMapping("/{id}")
    public ApiResponse<Product> getById(
            @PathVariable("id") long id
    ) {
        return ApiResponse.ok(productService.getById(id));
    }
}