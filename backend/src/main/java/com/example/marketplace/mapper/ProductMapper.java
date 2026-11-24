package com.example.marketplace.mapper;

import com.example.marketplace.domain.Product;

public interface ProductMapper {

    int insert(Product product);

    Product findById(long id);
}