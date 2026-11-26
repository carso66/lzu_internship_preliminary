package com.example.marketplace.service;

import com.example.marketplace.domain.Product;
import com.example.marketplace.dto.CreateProductRequest;
import com.example.marketplace.exception.ProductNotFoundException;
import com.example.marketplace.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品业务服务，负责商品创建、状态变更和分页查询。
 */
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
    public Product getById(long id) {
        Product product = productMapper.findById(id);
        if (product == null) {
            throw new ProductNotFoundException(id);
        }
        return product;
    }

    /**
     * 更新商品上下架状态；影响 0 行表示商品不存在。
     */
    @Transactional
    public ProductStatus updateStatus(long id, String status) {
        int updatedRows = productMapper.updateStatus(id, status);
        if (updatedRows == 0) {
            throw new ProductNotFoundException(id);
        }
        return new ProductStatus(id, status);
    }

    @Transactional(readOnly = true)
    public ProductPage list(String keyword, String status, String category, int page, int pageSize) {
        long total = productMapper.count(keyword, status, category);
        // 空结果直接返回，避免再执行一次无意义的分页查询。
        if (total == 0) {
            return new ProductPage(0, List.of());
        }

        long offset = ((long) page - 1) * pageSize;
        List<Product> items = productMapper.findPage(keyword, status, category, offset, pageSize);
        return new ProductPage(total, items);
    }

    public record ProductPage(long total, List<Product> items) {
    }

    public record ProductStatus(long id, String status) {
    }
}
