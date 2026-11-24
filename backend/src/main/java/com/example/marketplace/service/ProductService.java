package com.example.marketplace.service;

import com.example.marketplace.domain.Product;
import com.example.marketplace.dto.CreateProductRequest;
import com.example.marketplace.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}