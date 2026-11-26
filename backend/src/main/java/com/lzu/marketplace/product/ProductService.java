package com.lzu.marketplace.product;

import com.lzu.marketplace.common.ApiCode;
import com.lzu.marketplace.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Product create(CreateProductRequest request) {
        long id = repository.insert(request);
        return getById(id);
    }

    public Product getById(long id) {
        return repository.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, ApiCode.NOT_FOUND, "商品不存在"));
    }

    @Transactional(readOnly = true)
    public ProductPage list(ProductListQuery query) {
        return new ProductPage(repository.count(query), query.getPage(), query.getPageSize(),
                repository.findPage(query));
    }

    @Transactional
    public ProductStatusResponse updateStatus(long id, UpdateProductStatusRequest request) {
        getById(id);
        repository.updateStatus(id, request.status());
        return new ProductStatusResponse(id, request.status());
    }
}
