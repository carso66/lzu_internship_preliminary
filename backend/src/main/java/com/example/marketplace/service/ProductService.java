package com.example.marketplace.service;

import com.example.marketplace.domain.Product;
import com.example.marketplace.dto.CreateProductRequest;
import com.example.marketplace.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private static final String DEFAULT_STATUS = "on";

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Transactional
    public Product create(CreateProductRequest request) {
        Product product = new Product();
        product.setTitle(request.title());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategory(request.category());
        product.setDescription(request.description());
        product.setSellerName(request.sellerName());
        product.setStatus(DEFAULT_STATUS);

        productMapper.insert(product);
        return productMapper.findById(product.getId());
    }

    @Transactional(readOnly = true)
    public ProductPage list(String keyword, String status, String category, int page, int pageSize) {
        long total = productMapper.count(keyword, status, category);
        if (total == 0) {
            return new ProductPage(0, List.of());
        }

        long offset = ((long) page - 1) * pageSize;
        List<Product> items = productMapper.findPage(keyword, status, category, offset, pageSize);
        return new ProductPage(total, items);
    }

    public record ProductPage(long total, List<Product> items) {
    }
}
